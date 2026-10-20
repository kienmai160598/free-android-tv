package netcheck

import (
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"syscall"
	"time"
)

type Result int

const (
	Closed Result = 0
	Open   Result = 1
	Failed Result = 2
)

const timeout = 3 * time.Second

func ADBPort(host string, port int) (Result, string) {
	address := net.JoinHostPort(host, strconv.Itoa(port))
	conn, err := net.DialTimeout("tcp", address, timeout)
	if err == nil {
		conn.Close()
		return Open, fmt.Sprintf(
			"%s is still open: network ADB is on. On the TV, turn USB debugging and\n"+
				"Network/ADB debugging off, reboot it, and check again.",
			address,
		)
	}

	if errors.Is(err, syscall.ECONNREFUSED) || strings.Contains(err.Error(), "refused") {
		return Closed, fmt.Sprintf("%s is closed: the TV refused the connection.", address)
	}

	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return Closed, fmt.Sprintf(
			"%s did not answer in %.0f s: closed, unless the TV is off or not at this address.",
			address,
			timeout.Seconds(),
		)
	}

	return Failed, fmt.Sprintf("could not check %s (%v). Is the TV on and on this network?",
		address, err)
}
