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

	encodedPrivate, err := x509.MarshalPKCS8PrivateKey(private)
	if err != nil {
		return nil, err
	}

	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, err
	}

	key := &Key{private: private, Path: path}
	pemData := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: encodedPrivate})
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
	modulus := k.private.PublicKey.N
	words := (modulus.BitLen() + 31) / 32

	twoToThe32 := new(big.Int).Lsh(big.NewInt(1), 32)
	inverse := new(big.Int).ModInverse(new(big.Int).Mod(modulus, twoToThe32), twoToThe32)
	negativeInverse := inverse.Sub(twoToThe32, inverse)

	montgomerySquare := new(big.Int).Lsh(big.NewInt(1), uint(64*words))
	montgomerySquare.Mod(montgomerySquare, modulus)

	androidKey := make([]byte, 0, 4*(3+2*words))
	androidKey = binary.LittleEndian.AppendUint32(androidKey, uint32(words))
	androidKey = binary.LittleEndian.AppendUint32(androidKey, uint32(negativeInverse.Uint64()))
	androidKey = appendLittleEndianWords(androidKey, modulus, words)
	androidKey = appendLittleEndianWords(androidKey, montgomerySquare, words)
	androidKey = binary.LittleEndian.AppendUint32(androidKey, uint32(k.private.PublicKey.E))

	encoded := base64.StdEncoding.EncodeToString(androidKey)
	return []byte(encoded + " " + userAtHost() + "\n")
}

func appendLittleEndianWords(buffer []byte, value *big.Int, words int) []byte {
	bigEndian := value.FillBytes(make([]byte, 4*words))
	for index := len(bigEndian) - 1; index >= 0; index-- {
		buffer = append(buffer, bigEndian[index])
	}

	return buffer
}

func userAtHost() string {
	userName := "unknown"
	if currentUser, err := user.Current(); err == nil && currentUser.Username != "" {
		userName = currentUser.Username
	}

	hostName, err := os.Hostname()
	if err != nil || hostName == "" {
		hostName = "unknown"
	}

	return userName + "@" + hostName
}
