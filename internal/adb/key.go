package adb

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/pem"
	"errors"
	"fmt"
	"math/big"
	"os"
	"os/user"
	"path/filepath"
)

type Key struct {
	private *rsa.PrivateKey
	Path    string
}

func LoadKey(paths ...string) (*Key, error) {
	if len(paths) == 0 {
		return nil, errors.New("adb: no key path")
	}
	for _, path := range paths {
		data, err := os.ReadFile(path)
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return nil, err
		}
		private, err := parsePrivateKey(data)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", path, err)
		}
		return &Key{private: private, Path: path}, nil
	}
	return createKey(paths[len(paths)-1])
}

func parsePrivateKey(data []byte) (*rsa.PrivateKey, error) {
	block, _ := pem.Decode(data)
	if block == nil {
		return nil, errors.New("not a PEM private key")
	}
	if key, err := x509.ParsePKCS1PrivateKey(block.Bytes); err == nil {
		return key, nil
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, err
	}
	key, ok := parsed.(*rsa.PrivateKey)
	if !ok {
		return nil, errors.New("not an RSA key")
	}
	return key, nil
}

func createKey(path string) (*Key, error) {
	private, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, err
	}
	der, err := x509.MarshalPKCS8PrivateKey(private)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, err
	}
	key := &Key{private: private, Path: path}
	pemData := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})
	if err := os.WriteFile(path, pemData, 0o600); err != nil {
		return nil, err
	}
	if err := os.WriteFile(path+".pub", key.PublicKey(), 0o644); err != nil {
		return nil, err
	}
	return key, nil
}

func (k *Key) sign(token []byte) ([]byte, error) {
	return rsa.SignPKCS1v15(nil, k.private, crypto.SHA1, token)
}

func (k *Key) PublicKey() []byte {
	n := k.private.PublicKey.N
	words := (n.BitLen() + 31) / 32

	r32 := new(big.Int).Lsh(big.NewInt(1), 32)
	n0inv := new(big.Int).ModInverse(new(big.Int).Mod(n, r32), r32)
	n0inv.Sub(r32, n0inv)

	rr := new(big.Int).Lsh(big.NewInt(1), uint(64*words))
	rr.Mod(rr, n)

	out := make([]byte, 0, 4*(3+2*words))
	out = binary.LittleEndian.AppendUint32(out, uint32(words))
	out = binary.LittleEndian.AppendUint32(out, uint32(n0inv.Uint64()))
	out = appendLittleEndianWords(out, n, words)
	out = appendLittleEndianWords(out, rr, words)
	out = binary.LittleEndian.AppendUint32(out, uint32(k.private.PublicKey.E))

	encoded := base64.StdEncoding.EncodeToString(out)
	return []byte(encoded + " " + userAtHost() + "\n")
}

func appendLittleEndianWords(out []byte, value *big.Int, words int) []byte {
	bigEndian := value.FillBytes(make([]byte, 4*words))
	for i := len(bigEndian) - 1; i >= 0; i-- {
		out = append(out, bigEndian[i])
	}
	return out
}

func userAtHost() string {
	name := "unknown"
	if u, err := user.Current(); err == nil && u.Username != "" {
		name = u.Username
	}
	host, err := os.Hostname()
	if err != nil || host == "" {
		host = "unknown"
	}
	return name + "@" + host
}
