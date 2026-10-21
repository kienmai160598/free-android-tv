package tv

import (
	"os"
	"path/filepath"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/adb"
)

type Device interface {
	Shell(command string) (string, error)
	Push(local, remote string) error
}

type Session struct {
	Host string
	Port int
	Key  *adb.Key
	Opts adb.Options
	conn *adb.Conn
}

func (s *Session) connection() (*adb.Conn, error) {
	if s.conn != nil {
		return s.conn, nil
	}

	conn, err := adb.Dial(s.Host, s.Port, s.Key, s.Opts)
	if err != nil {
		return nil, err
	}

	s.conn = conn
	return conn, nil
}

func (s *Session) Close() {
	if s.conn != nil {
		s.conn.Close()
		s.conn = nil
	}
}

func withConnection[T any](s *Session, use func(*adb.Conn) (T, error)) (T, error) {
	conn, err := s.connection()
	if err != nil {
		var zero T
		return zero, err
	}

	result, err := use(conn)
	if err != nil {
		s.Close()
	}

	return result, err
}

func (s *Session) Shell(command string) (string, error) {
	return withConnection(s, func(conn *adb.Conn) (string, error) { return conn.Shell(command) })
}

func (s *Session) Exec(command string) ([]byte, error) {
	return withConnection(s, func(conn *adb.Conn) ([]byte, error) { return conn.Exec(command) })
}

func (s *Session) Pull(remote, local string) error {
	if err := os.MkdirAll(filepath.Dir(local), 0o755); err != nil {
		return err
	}

	file, err := os.Create(local)
	if err != nil {
		return err
	}
	defer file.Close()

	_, err = withConnection(
		s,
		func(conn *adb.Conn) (struct{}, error) { return struct{}{}, conn.Pull(remote, file) },
	)
	if err != nil {
		return err
	}

	return file.Close()
}
