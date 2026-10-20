package adb

import (
	"fmt"
	"io"
)

type stream struct {
	conn     *Conn
	localID  uint32
	remoteID uint32
	closed   bool
	pending  []byte
}

func (c *Conn) Shell(command string) (string, error) {
	out, err := c.runService("shell:" + command)
	return string(out), err
}

func (c *Conn) Exec(command string) ([]byte, error) {
	return c.runService("exec:" + command)
}

func (c *Conn) runService(service string) ([]byte, error) {
	serviceStream, err := c.open(service)
	if err != nil {
		return nil, err
	}

	return io.ReadAll(serviceStream)
}

func (c *Conn) open(service string) (*stream, error) {
	localID := c.nextID
	c.nextID++
	if err := c.send(message{cmdOPEN, localID, 0, append([]byte(service), 0)}); err != nil {
		return nil, err
	}

	reply, err := c.receiveFor(localID)
	if err != nil {
		return nil, err
	}

	switch reply.command {
	case cmdOKAY:
		return &stream{conn: c, localID: localID, remoteID: reply.arg0}, nil
	case cmdCLSE:
		return nil, fmt.Errorf("the TV refused to open %q", service)
	default:
		return nil, fmt.Errorf("unexpected %s opening %q", reply, service)
	}
}

func (c *Conn) receiveFor(localID uint32) (message, error) {
	for {
		packet, err := c.receive(c.opts.ReadTimeout)
		if err != nil || packet.arg1 == localID {
			return packet, err
		}
	}
}

func (s *stream) receive() (message, error) {
	return s.conn.receiveFor(s.localID)
}

func (s *stream) reply(command uint32) error {
	return s.conn.send(message{command, s.localID, s.remoteID, nil})
}

func (s *stream) keep(data []byte) error {
	s.pending = append(s.pending, data...)
	return s.reply(cmdOKAY)
}

func (s *stream) Write(data []byte) (int, error) {
	written := 0
	for len(data) > 0 {
		size := min(len(data), int(s.conn.maxData))
		chunk := message{cmdWRTE, s.localID, s.remoteID, data[:size]}
		if err := s.conn.send(chunk); err != nil {
			return written, err
		}
		if err := s.awaitAcknowledgement(); err != nil {
			return written, err
		}
		written += size
		data = data[size:]
	}

	return written, nil
}

func (s *stream) awaitAcknowledgement() error {
	for {
		packet, err := s.receive()
		if err != nil {
			return err
		}
		switch packet.command {
		case cmdOKAY:
			return nil
		case cmdWRTE:
			if err := s.keep(packet.data); err != nil {
				return err
			}
		case cmdCLSE:
			s.closed = true
			return io.ErrUnexpectedEOF
		}
	}
}

func (s *stream) Read(buffer []byte) (int, error) {
	for len(s.pending) == 0 {
		if s.closed {
			return 0, io.EOF
		}
		if err := s.receiveIntoPending(); err != nil {
			return 0, err
		}
	}

	copied := copy(buffer, s.pending)
	s.pending = s.pending[copied:]
	return copied, nil
}

func (s *stream) receiveIntoPending() error {
	packet, err := s.receive()
	if err != nil {
		return err
	}

	switch packet.command {
	case cmdWRTE:
		return s.keep(packet.data)
	case cmdCLSE:
		s.closed = true
		return s.reply(cmdCLSE)
	}

	return nil
}

func (s *stream) Close() error {
	if s.closed {
		return nil
	}

	if err := s.reply(cmdCLSE); err != nil {
		return err
	}

	for !s.closed {
		packet, err := s.receive()
		if err != nil {
			return err
		}
		switch packet.command {
		case cmdCLSE:
			s.closed = true
		case cmdWRTE:
			s.reply(cmdOKAY)
		}
	}

	return nil
}
