package adb

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"time"
)

const (
	syncBlockLimit = 64 * 1024
	regularFile    = 0o100644
)

func (c *Conn) Push(source io.Reader, remote string, modified time.Time) error {
	s, err := c.open("sync:")
	if err != nil {
		return err
	}
	defer s.Close()
	if err := writeSyncPacket(s, "SEND", []byte(fmt.Sprintf("%s,%d", remote, regularFile))); err != nil {
		return err
	}
	if err := sendFileBlocks(s, source, min(syncBlockLimit, c.MaxData()/2)); err != nil {
		return failureFromTV(s, err)
	}
	if err := writeSyncHeader(s, "DONE", uint32(modified.Unix())); err != nil {
		return failureFromTV(s, err)
	}
	return awaitPushResult(s, remote)
}

func sendFileBlocks(s *stream, source io.Reader, blockSize int) error {
	block := make([]byte, blockSize)
	for {
		n, err := io.ReadFull(source, block)
		if n > 0 {
			if err := writeSyncPacket(s, "DATA", block[:n]); err != nil {
				return err
			}
		}
		if err == io.EOF || err == io.ErrUnexpectedEOF {
			return nil
		}
		if err != nil {
			return err
		}
	}
}

func awaitPushResult(s *stream, remote string) error {
	id, length, err := readSyncHeader(s)
	if err != nil {
		return err
	}
	switch id {
	case "OKAY":
		return writeSyncHeader(s, "QUIT", 0)
	case "FAIL":
		return fmt.Errorf("push to %s: %s", remote, readSyncMessage(s, length))
	default:
		return fmt.Errorf("push to %s: unexpected sync reply %q", remote, id)
	}
}

func failureFromTV(s *stream, err error) error {
	if id, length, readErr := readSyncHeader(s); readErr == nil && id == "FAIL" {
		return errors.New(readSyncMessage(s, length))
	}
	return err
}

func (c *Conn) Pull(remote string, destination io.Writer) error {
	s, err := c.open("sync:")
	if err != nil {
		return err
	}
	defer s.Close()
	if err := writeSyncPacket(s, "RECV", []byte(remote)); err != nil {
		return err
	}
	for {
		id, length, err := readSyncHeader(s)
		if err != nil {
			return err
		}
		switch id {
		case "DATA":
			if _, err := io.CopyN(destination, s, int64(length)); err != nil {
				return err
			}
		case "DONE":
			return writeSyncHeader(s, "QUIT", 0)
		case "FAIL":
			return fmt.Errorf("pull %s: %s", remote, readSyncMessage(s, length))
		default:
			return fmt.Errorf("pull %s: unexpected sync reply %q", remote, id)
		}
	}
}

func writeSyncHeader(w io.Writer, id string, value uint32) error {
	return writeSyncPacketWithValue(w, id, value, nil)
}

func writeSyncPacket(w io.Writer, id string, payload []byte) error {
	return writeSyncPacketWithValue(w, id, uint32(len(payload)), payload)
}

func writeSyncPacketWithValue(w io.Writer, id string, value uint32, payload []byte) error {
	packet := make([]byte, 8, 8+len(payload))
	copy(packet, id)
	binary.LittleEndian.PutUint32(packet[4:], value)
	_, err := w.Write(append(packet, payload...))
	return err
}

func readSyncHeader(r io.Reader) (string, uint32, error) {
	var header [8]byte
	if _, err := io.ReadFull(r, header[:]); err != nil {
		return "", 0, err
	}
	return string(header[:4]), binary.LittleEndian.Uint32(header[4:]), nil
}

func readSyncMessage(r io.Reader, length uint32) string {
	message := make([]byte, length)
	io.ReadFull(r, message)
	return string(message)
}
