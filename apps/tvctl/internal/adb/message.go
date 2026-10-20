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

func (msg message) String() string {
	name := make([]byte, 4)
	binary.LittleEndian.PutUint32(name, msg.command)
	return fmt.Sprintf("%s(%#x, %#x, %d bytes)", name, msg.arg0, msg.arg1, len(msg.data))
}

func checksum(data []byte) uint32 {
	var sum uint32
	for _, value := range data {
		sum += uint32(value)
	}

	return sum
}

func writeMessage(writer io.Writer, msg message) error {
	packet := make([]byte, headerSize+len(msg.data))
	binary.LittleEndian.PutUint32(packet[0:], msg.command)
	binary.LittleEndian.PutUint32(packet[4:], msg.arg0)
	binary.LittleEndian.PutUint32(packet[8:], msg.arg1)
	binary.LittleEndian.PutUint32(packet[12:], uint32(len(msg.data)))
	binary.LittleEndian.PutUint32(packet[16:], checksum(msg.data))
	binary.LittleEndian.PutUint32(packet[20:], msg.command^0xffffffff)
	copy(packet[headerSize:], msg.data)

	_, err := writer.Write(packet)
	return err
}

func readMessage(reader io.Reader, maxData uint32) (message, error) {
	var header [headerSize]byte
	if _, err := io.ReadFull(reader, header[:]); err != nil {
		return message{}, err
	}

	msg := message{
		command: binary.LittleEndian.Uint32(header[0:]),
		arg0:    binary.LittleEndian.Uint32(header[4:]),
		arg1:    binary.LittleEndian.Uint32(header[8:]),
	}

	length := binary.LittleEndian.Uint32(header[12:])
	magic := binary.LittleEndian.Uint32(header[20:])

	if magic != msg.command^0xffffffff {
		return message{}, fmt.Errorf("adb: corrupt packet header (command %#x, magic %#x)",
			msg.command, magic)
	}

	if length > maxData {
		return message{}, fmt.Errorf("adb: %s payload of %d bytes exceeds the %d agreed",
			msg, length, maxData)
	}

	msg.data = make([]byte, length)
	if _, err := io.ReadFull(reader, msg.data); err != nil {
		return message{}, err
	}

	return msg, nil
}
