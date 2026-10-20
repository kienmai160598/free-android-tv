package adb

import (
	"bytes"
	"errors"
	"fmt"
	"net"
	"strconv"
	"time"
)

type Options struct {
	DialTimeout time.Duration
	AuthTimeout time.Duration
	ReadTimeout time.Duration
	OnPrompt    func()
}

func (o Options) withDefaults() Options {
	if o.DialTimeout == 0 {
		o.DialTimeout = 10 * time.Second
	}

	if o.AuthTimeout == 0 {
		o.AuthTimeout = 120 * time.Second
	}

	if o.ReadTimeout == 0 {
		o.ReadTimeout = 120 * time.Second
	}

	return o
}

type Conn struct {
	net     net.Conn
	opts    Options
	maxData uint32
	nextID  uint32
	Banner  string
}

var errNobodyAllowed = errors.New("nobody allowed the connection on the TV in time; " +
	`accept "Allow USB debugging?" there and run this again`)

func Dial(host string, port int, key *Key, opts Options) (*Conn, error) {
	opts = opts.withDefaults()
	address := net.JoinHostPort(host, strconv.Itoa(port))
	transport, err := net.DialTimeout("tcp", address, opts.DialTimeout)
	if err != nil {
		return nil, err
	}

	conn, err := NewConn(transport, key, opts)
	if err != nil {
		transport.Close()
		return nil, fmt.Errorf("adb handshake with %s: %w", address, err)
	}

	return conn, nil
}

func NewConn(transport net.Conn, key *Key, opts Options) (*Conn, error) {
	conn := &Conn{net: transport, opts: opts.withDefaults(), maxData: hostMaxData, nextID: 1}
	if err := conn.handshake(key); err != nil {
		return nil, err
	}

	return conn, nil
}

type handshakeState struct {
	sentSignature bool
	sentPublicKey bool
}

func (c *Conn) handshake(key *Key) error {
	connect := message{cmdCNXN, protocolVersion, hostMaxData, []byte("host::\x00")}
	if err := c.send(connect); err != nil {
		return err
	}

	var state handshakeState
	for {
		reply, err := c.receive(c.handshakeTimeout(state))
		if err != nil {
			if state.sentPublicKey && isTimeout(err) {
				return errNobodyAllowed
			}
			return err
		}
		if reply.command == cmdCNXN {
			c.acceptConnection(reply)
			return nil
		}
		if err := c.answerAuth(reply, key, &state); err != nil {
			return err
		}
	}
}

func (c *Conn) handshakeTimeout(state handshakeState) time.Duration {
	if state.sentPublicKey {
		return c.opts.AuthTimeout
	}

	return c.opts.ReadTimeout
}

func (c *Conn) acceptConnection(reply message) {
	if reply.arg1 > 0 && reply.arg1 < c.maxData {
		c.maxData = reply.arg1
	}

	c.Banner = string(bytes.TrimRight(reply.data, "\x00"))
}

func (c *Conn) answerAuth(reply message, key *Key, state *handshakeState) error {
	switch {
	case reply.command != cmdAUTH:
		return fmt.Errorf("unexpected %s during the handshake", reply)
	case reply.arg0 == authToken && !state.sentSignature:
		state.sentSignature = true
		return c.sendSignature(reply.data, key)
	case reply.arg0 == authToken && !state.sentPublicKey:
		state.sentPublicKey = true
		return c.offerPublicKey(key)
	default:
		return errors.New("the TV refused this key")
	}
}

func (c *Conn) sendSignature(token []byte, key *Key) error {
	signature, err := key.sign(token)
	if err != nil {
		return err
	}

	return c.send(message{cmdAUTH, authSignature, 0, signature})
}

func (c *Conn) offerPublicKey(key *Key) error {
	if c.opts.OnPrompt != nil {
		c.opts.OnPrompt()
	}

	publicKey := append(bytes.TrimRight(key.PublicKey(), "\n"), 0)
	return c.send(message{cmdAUTH, authRSAPublicKey, 0, publicKey})
}

func (c *Conn) MaxData() int { return int(c.maxData) }

func (c *Conn) Close() error { return c.net.Close() }

func (c *Conn) send(m message) error {
	c.net.SetWriteDeadline(time.Now().Add(c.opts.ReadTimeout))
	return writeMessage(c.net, m)
}

func (c *Conn) receive(timeout time.Duration) (message, error) {
	c.net.SetReadDeadline(time.Now().Add(timeout))
	return readMessage(c.net, hostMaxData)
}

func isTimeout(err error) bool {
	var netErr net.Error
	return errors.As(err, &netErr) && netErr.Timeout()
}
