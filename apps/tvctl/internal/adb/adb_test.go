package adb

import (
	"bytes"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"io"
	"math/big"
	"net"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

type fakeADBD struct {
	t       *testing.T
	conn    net.Conn
	maxData uint32
	trusted *rsa.PublicKey
	offered []byte
	files   map[string][]byte
	shell   func(command string) string
	largest int
}

func (d *fakeADBD) send(m message) {
	if err := writeMessage(d.conn, m); err != nil {
		d.t.Errorf("fake adbd send: %v", err)
	}
}

func (d *fakeADBD) receive() (message, bool) {
	m, err := readMessage(d.conn, hostMaxData)
	if err != nil {
		return message{}, false
	}

	if len(m.data) > d.largest {
		d.largest = len(m.data)
	}

	return m, true
}

func (d *fakeADBD) serve() {
	defer d.conn.Close()
	token := make([]byte, 20)
	rand.Read(token)
	m, ok := d.receive()
	if !ok || m.command != cmdCNXN {
		d.t.Errorf("expected CNXN, got %v", m)
		return
	}

	d.send(message{cmdAUTH, authToken, 0, token})
	for authenticated := false; !authenticated; {
		m, ok := d.receive()
		if !ok {
			return
		}
		switch m.arg0 {
		case authSignature:
			sum := token
			if d.trusted != nil && rsa.VerifyPKCS1v15(d.trusted, crypto.SHA1, sum, m.data) == nil {
				authenticated = true
				continue
			}
			d.send(message{cmdAUTH, authToken, 0, token})
		case authRSAPublicKey:
			d.offered = m.data
			authenticated = true
		}
	}

	d.send(
		message{cmdCNXN, protocolVersion, d.maxData, []byte("device::ro.product.model=Fake;\x00")},
	)

	for {
		m, ok := d.receive()
		if !ok {
			return
		}
		if m.command != cmdOPEN {
			continue
		}
		service := string(bytes.TrimRight(m.data, "\x00"))
		local, remote := uint32(100), m.arg0
		d.send(message{cmdOKAY, local, remote, nil})
		switch {
		case strings.HasPrefix(service, "shell:"):
			out := []byte(d.shell(strings.TrimPrefix(service, "shell:")))
			for len(out) > 0 {
				n := min(len(out), int(d.maxData))
				d.send(message{cmdWRTE, local, remote, out[:n]})
				out = out[n:]
				if m, _ := d.receive(); m.command != cmdOKAY {
					d.t.Errorf("expected OKAY for shell output, got %v", m)
				}
			}
			d.send(message{cmdCLSE, local, remote, nil})
			d.receive()
		case service == "sync:":
			d.sync(local, remote)
		}
	}
}

type syncReader struct {
	d             *fakeADBD
	local, remote uint32
	buf           []byte
	closed        bool
}

func (r *syncReader) Read(p []byte) (int, error) {
	for len(r.buf) == 0 {
		m, ok := r.d.receive()
		if !ok || m.command == cmdCLSE {
			r.closed = true
			return 0, io.EOF
		}
		if m.command == cmdWRTE {
			r.buf = m.data
			r.d.send(message{cmdOKAY, r.local, r.remote, nil})
		}
	}

	n := copy(p, r.buf)
	r.buf = r.buf[n:]
	return n, nil
}

func (d *fakeADBD) reply(local, remote uint32, data []byte) {
	d.send(message{cmdWRTE, local, remote, data})
	if m, _ := d.receive(); m.command != cmdOKAY {
		d.t.Errorf("expected OKAY for a sync reply, got %v", m)
	}
}

func (d *fakeADBD) sync(local, remote uint32) {
	r := &syncReader{d: d, local: local, remote: remote}
	header := make([]byte, 8)
	for {
		if _, err := io.ReadFull(r, header); err != nil {
			if !r.closed {
				d.t.Errorf("sync: %v", err)
			}
			d.send(message{cmdCLSE, local, remote, nil})
			return
		}
		id, length := string(header[:4]), binary.LittleEndian.Uint32(header[4:])
		switch id {
		case "SEND":
			spec := make([]byte, length)
			io.ReadFull(r, spec)
			path, _, _ := strings.Cut(string(spec), ",")
			var file bytes.Buffer
			for {
				io.ReadFull(r, header)
				id, length := string(header[:4]), binary.LittleEndian.Uint32(header[4:])
				if id == "DONE" {
					break
				}
				if id != "DATA" || length > 64*1024 {
					d.t.Errorf("bad sync DATA %q of %d bytes", id, length)
				}
				io.CopyN(&file, r, int64(length))
			}
			d.files[path] = file.Bytes()
			d.reply(local, remote, []byte("OKAY\x00\x00\x00\x00"))
		case "RECV":
			path := make([]byte, length)
			io.ReadFull(r, path)
			data, ok := d.files[string(path)]
			if !ok {
				msg := "No such file or directory"
				reply := append(
					[]byte("FAIL"),
					binary.LittleEndian.AppendUint32(nil, uint32(len(msg)))...)
				d.reply(local, remote, append(reply, msg...))
				continue
			}
			for len(data) > 0 {
				n := min(len(data), int(d.maxData)-8)
				packet := append(
					[]byte("DATA"),
					binary.LittleEndian.AppendUint32(nil, uint32(n))...)
				d.reply(local, remote, append(packet, data[:n]...))
				data = data[n:]
			}
			d.reply(local, remote, []byte("DONE\x00\x00\x00\x00"))
		case "QUIT":
			d.send(message{cmdCLSE, local, remote, nil})
			d.receive()
			return
		}
	}
}

func connect(t *testing.T, maxData uint32, trust bool) (*Conn, *fakeADBD, *Key) {
	t.Helper()
	key, err := LoadKey(filepath.Join(t.TempDir(), "adbkey"))
	if err != nil {
		t.Fatal(err)
	}

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()

	host, err := net.Dial("tcp", listener.Addr().String())
	if err != nil {
		t.Fatal(err)
	}

	device, err := listener.Accept()
	if err != nil {
		t.Fatal(err)
	}

	d := &fakeADBD{t: t, conn: device, maxData: maxData, files: map[string][]byte{},
		shell: func(command string) string { return "ran: " + command + "\n" }}
	if trust {
		d.trusted = &key.private.PublicKey
	}

	go d.serve()
	prompted := false
	c, err := NewConn(host, key, Options{ReadTimeout: 5 * time.Second, AuthTimeout: 5 * time.Second,
		OnPrompt: func() { prompted = true }})
	if err != nil {
		t.Fatal(err)
	}

	if prompted == trust {
		t.Errorf("prompted = %v for a key the TV trusts = %v", prompted, trust)
	}

	t.Cleanup(func() { c.Close() })
	return c, d, key
}

func TestTrustedKeySignsInAndRunsShell(t *testing.T) {
	c, _, _ := connect(t, 256*1024, true)
	if !strings.Contains(c.Banner, "ro.product.model=Fake") {
		t.Errorf("banner %q", c.Banner)
	}

	for _, command := range []string{"getprop ro.product.model", "pm list packages"} {
		out, err := c.Shell(command)
		if err != nil || out != "ran: "+command+"\n" {
			t.Errorf("Shell(%q) = %q, %v", command, out, err)
		}
	}
}

func TestUnknownKeyIsOfferedInAndroidFormat(t *testing.T) {
	_, d, key := connect(t, 256*1024, false)
	text := strings.TrimRight(string(d.offered), "\x00")
	encoded, _, _ := strings.Cut(text, " ")
	raw, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		t.Fatal(err)
	}

	words := binary.LittleEndian.Uint32(raw)
	if words != 64 || len(raw) != 4*(3+2*64) {
		t.Fatalf("RSAPublicKey of %d words, %d bytes", words, len(raw))
	}

	littleEndian := func(b []byte) *big.Int {
		reversed := make([]byte, len(b))
		for i := range b {
			reversed[len(b)-1-i] = b[i]
		}
		return new(big.Int).SetBytes(reversed)
	}

	n := littleEndian(raw[8 : 8+256])
	if n.Cmp(key.private.N) != 0 {
		t.Error("modulus differs from the key's")
	}

	n0inv := binary.LittleEndian.Uint32(raw[4:])
	if uint32(n.Uint64())*n0inv != 0xffffffff {
		t.Error("n0inv is not -1/n mod 2^32")
	}

	rr := littleEndian(raw[8+256 : 8+512])
	want := new(big.Int).Exp(big.NewInt(2), big.NewInt(4096), n)
	if rr.Cmp(want) != 0 {
		t.Error("rr is not 2^4096 mod n")
	}

	if e := binary.LittleEndian.Uint32(raw[8+512:]); e != 65537 {
		t.Errorf("exponent %d", e)
	}
}

func TestKeyIsReusedNotRegenerated(t *testing.T) {
	path := filepath.Join(t.TempDir(), "adbkey")
	first, err := LoadKey(path)
	if err != nil {
		t.Fatal(err)
	}

	second, err := LoadKey(filepath.Join(t.TempDir(), "missing"), path)
	if err != nil {
		t.Fatal(err)
	}

	if second.Path != path || first.private.N.Cmp(second.private.N) != 0 {
		t.Error("an existing key must be loaded, not replaced")
	}

	token := sha1.Sum([]byte("token"))
	if _, err := second.sign(token[:]); err != nil {
		t.Error(err)
	}
}

func TestPushAndPullOnAnAndroid5PacketSize(t *testing.T) {
	c, d, _ := connect(t, 4096, true)
	data := make([]byte, 300*1024+17)
	rand.Read(data)
	if err := c.Push(bytes.NewReader(data), "/data/local/tmp/x.apk", time.Now()); err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(d.files["/data/local/tmp/x.apk"], data) {
		t.Fatal("pushed file differs")
	}

	if d.largest > 4096 {
		t.Errorf("sent a %d-byte payload to a TV that takes 4096", d.largest)
	}

	var back bytes.Buffer
	if err := c.Pull("/data/local/tmp/x.apk", &back); err != nil {
		t.Fatal(err)
	}

	if !bytes.Equal(back.Bytes(), data) {
		t.Fatal("pulled file differs")
	}

	if err := c.Pull("/nope", io.Discard); err == nil ||
		!strings.Contains(err.Error(), "No such file") {
		t.Errorf("pull of a missing file: %v", err)
	}

	if out, err := c.Shell("echo ok"); err != nil || !strings.Contains(out, "echo ok") {
		t.Errorf("shell after sync: %q, %v", out, err)
	}
}
