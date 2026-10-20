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
	s, err := c.open(service)
	if err != nil {
		return nil, err
	}
	return io.ReadAll(s)
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
		m, err := c.receive(c.opts.ReadTimeout)
		if err != nil || m.arg1 == localID {
			return m, err
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
		if err := s.conn.send(message{cmdWRTE, s.localID, s.remoteID, data[:size]}); err != nil {
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
		m, err := s.receive()
		if err != nil {
			return err
		}
		switch m.command {
		case cmdOKAY:
			return nil
		case cmdWRTE:
			if err := s.keep(m.data); err != nil {
				return err
			}
		case cmdCLSE:
			s.closed = true
			return io.ErrUnexpectedEOF
		}
	}
}

func (s *stream) Read(p []byte) (int, error) {
	for len(s.pending) == 0 {
		if s.closed {
			return 0, io.EOF
		}
		if err := s.receiveIntoPending(); err != nil {
			return 0, err
		}
	}
	n := copy(p, s.pending)
	s.pending = s.pending[n:]
	return n, nil
}

func (s *stream) receiveIntoPending() error {
	m, err := s.receive()
	if err != nil {
		return err
	}
	switch m.command {
	case cmdWRTE:
		return s.keep(m.data)
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
		m, err := s.receive()
		if err != nil {
			return err
		}
		switch m.command {
		case cmdCLSE:
			s.closed = true
		case cmdWRTE:
			s.reply(cmdOKAY)
		}
	}
	return nil
}
