package tv

import (
	"bytes"
	"crypto/md5"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/adb"
)

func (s *Session) Push(local, remote string) error {
	data, err := os.ReadFile(local)
	if err != nil {
		return err
	}

	_, syncErr := withConnection(s, func(conn *adb.Conn) (struct{}, error) {
		return struct{}{}, conn.Push(bytes.NewReader(data), remote, time.Now())
	})
	if syncErr != nil {
		if err := s.pushThroughShell(data, remote); err != nil {
			return fmt.Errorf("push %s: %v (sync), then %v (shell)", local, syncErr, err)
		}
	}

	return s.verifyChecksum(data, remote)
}

func (s *Session) verifyChecksum(data []byte, remote string) error {
	out, err := s.Shell("md5sum " + Quote(remote))
	if err != nil {
		return err
	}

	fields := strings.Fields(out)
	if len(fields) == 0 || len(fields[0]) != 32 {
		return nil
	}

	sum := md5.Sum(data)
	if expected := hex.EncodeToString(sum[:]); fields[0] != expected {
		return fmt.Errorf("%s arrived damaged on the TV (md5 %s, expected %s)",
			remote, fields[0], expected)
	}

	return nil
}

func (s *Session) pushThroughShell(data []byte, remote string) error {
	conn, err := s.connection()
	if err != nil {
		return err
	}

	encoded := remote + ".b64"
	if _, err := s.Shell("rm -f " + Quote(remote) + " " + Quote(encoded)); err != nil {
		return err
	}

	for index, chunk := range base64Chunks(data, conn.MaxData()) {
		if err := s.appendChunk(encoded, chunk, index); err != nil {
			return err
		}
	}

	return s.runExpecting(
		fmt.Sprintf("base64 -d %s > %s && rm %s", Quote(encoded), Quote(remote), Quote(encoded)),
		"decoded",
	)
}

func base64Chunks(data []byte, maxPacket int) []string {
	size := min(48*1024, maxPacket*3/4-64)
	size -= size % 3
	var chunks []string
	for start := 0; start < len(data); start += size {
		chunks = append(
			chunks,
			base64.StdEncoding.EncodeToString(data[start:min(start+size, len(data))]),
		)
	}

	return chunks
}

func (s *Session) appendChunk(path, chunk string, index int) error {
	return s.runExpecting(
		fmt.Sprintf("echo '%s' >> %s", chunk, Quote(path)),
		fmt.Sprintf("ok%d", index),
	)
}

func (s *Session) runExpecting(command, marker string) error {
	out, err := s.Shell(command + " && echo " + marker)
	if err != nil {
		return err
	}

	if !strings.Contains(out, marker) {
		return fmt.Errorf("%s: %s", marker, strings.TrimSpace(out))
	}

	return nil
}
