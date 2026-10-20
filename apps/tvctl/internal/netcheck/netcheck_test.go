package netcheck

import (
	"net"
	"runtime"
	"strings"
	"testing"
)

func TestOpenPort(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()

	result, message := ADBPort("127.0.0.1", listener.Addr().(*net.TCPAddr).Port)
	if result != Open || !strings.Contains(message, "still open") {
		t.Errorf("%v: %s", result, message)
	}
}

func TestClosedPort(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}

	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close()
	result, message := ADBPort("127.0.0.1", port)
	if result != Closed ||
		(runtime.GOOS != "windows" && !strings.Contains(message, "is closed: the TV refused")) {
		t.Errorf("%v: %s", result, message)
	}
}

func TestUnresolvableHost(t *testing.T) {
	if result, message := ADBPort("no-such-tv.invalid", 5555); result != Failed {
		t.Errorf("%v: %s", result, message)
	}
}
