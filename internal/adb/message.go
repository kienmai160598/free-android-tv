package adb

import (
	"encoding/binary"
	"fmt"
	"io"
)

const (
	cmdCNXN = 0x4e584e43
	cmdAUTH = 0x48545541
	cmdOPEN = 0x4e45504f
	cmdOKAY = 0x59414b4f
	cmdCLSE = 0x45534c43
	cmdWRTE = 0x45545257
)

const (
	authToken        = 1
	authSignature    = 2
	authRSAPublicKey = 3
)

const (
	protocolVersion = 0x01000000
	hostMaxData     = 1024 * 1024
	headerSize      = 24
)

type message struct {
	command uint32
	arg0    uint32
	arg1    uint32
	data    []byte
}

func (m message) String() string {
	name := make([]byte, 4)
	binary.LittleEndian.PutUint32(name, m.command)
	return fmt.Sprintf("%s(%#x, %#x, %d bytes)", name, m.arg0, m.arg1, len(m.data))
}

func checksum(data []byte) uint32 {
	var sum uint32
	for _, b := range data {
		sum += uint32(b)
	}
	return sum
}

func writeMessage(w io.Writer, m message) error {
	packet := make([]byte, headerSize+len(m.data))
	binary.LittleEndian.PutUint32(packet[0:], m.command)
	binary.LittleEndian.PutUint32(packet[4:], m.arg0)
	binary.LittleEndian.PutUint32(packet[8:], m.arg1)
	binary.LittleEndian.PutUint32(packet[12:], uint32(len(m.data)))
	binary.LittleEndian.PutUint32(packet[16:], checksum(m.data))
	binary.LittleEndian.PutUint32(packet[20:], m.command^0xffffffff)
	copy(packet[headerSize:], m.data)
	_, err := w.Write(packet)
	return err
}

func readMessage(r io.Reader, maxData uint32) (message, error) {
	var header [headerSize]byte
	if _, err := io.ReadFull(r, header[:]); err != nil {
		return message{}, err
	}
	m := message{
		command: binary.LittleEndian.Uint32(header[0:]),
		arg0:    binary.LittleEndian.Uint32(header[4:]),
		arg1:    binary.LittleEndian.Uint32(header[8:]),
	}
	length := binary.LittleEndian.Uint32(header[12:])
	if magic := binary.LittleEndian.Uint32(header[20:]); magic != m.command^0xffffffff {
		return message{}, fmt.Errorf("adb: corrupt packet header (command %#x, magic %#x)", m.command, magic)
	}
	if length > maxData {
		return message{}, fmt.Errorf("adb: %s payload of %d bytes exceeds the %d agreed", m, length, maxData)
	}
	m.data = make([]byte, length)
	if _, err := io.ReadFull(r, m.data); err != nil {
		return message{}, err
	}
	return m, nil
}
