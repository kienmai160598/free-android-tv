package main

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/netcheck"
	"github.com/kienmai160598/freeTV/apps/tvctl/internal/tv"
)

var probeProperties = []string{
	"ro.product.model",
	"ro.product.device",
	"ro.build.version.release",
	"ro.build.version.sdk",
	"ro.build.display.id",
	"ro.product.manufacturer",
	"ro.adb.secure",
	"service.adb.tcp.port",
}

func (a *app) probe(args []string) error {
	return a.withTV(func(session *tv.Session) error {
		for _, property := range probeProperties {
			value, err := session.Shell("getprop " + property)
			if err != nil {
				return err
			}
			fmt.Fprintf(a.stdout, "%s = %s\n", property, strings.TrimSpace(value))
		}
		return nil
	})
}

func (a *app) shell(args []string) error {
	if len(args) == 0 {
		return usageError("shell <command...>")
	}

	return a.withTV(func(session *tv.Session) error {
		out, err := session.Shell(strings.Join(args, " "))
		fmt.Fprint(a.stdout, out)
		return err
	})
}

func (a *app) batch(args []string) error {
	commands, err := readBatchCommands(a.stdin)
	if err != nil {
		return err
	}

	return a.sendBatch(commands)
}

func readBatchCommands(input io.Reader) ([]string, error) {
	var commands []string
	scanner := bufio.NewScanner(input)
	scanner.Buffer(make([]byte, 64*1024), 1024*1024)
	for first := true; scanner.Scan(); first = false {
		line := scanner.Text()
		if first {
			line = strings.TrimPrefix(line, "\uFEFF")
		}
		line = strings.TrimSpace(line)
		if line != "" && !strings.HasPrefix(line, "#") {
			commands = append(commands, line)
		}
	}

	return commands, scanner.Err()
}

func (a *app) push(args []string) error {
	return a.copyFile(args, "push <local> <remote>", "pushed", (*tv.Session).Push)
}

func (a *app) pull(args []string) error {
	return a.copyFile(args, "pull <remote> <local>", "pulled", (*tv.Session).Pull)
}

func (a *app) copyFile(
	args []string,
	usage, done string,
	transfer func(*tv.Session, string, string) error,
) error {
	if len(args) != 2 {
		return usageError(usage)
	}

	return a.withTV(func(session *tv.Session) error {
		if err := transfer(session, args[0], args[1]); err != nil {
			return err
		}
		fmt.Fprintf(a.stdout, "%s %s -> %s\n", done, args[0], args[1])
		return nil
	})
}

func (a *app) screencap(args []string) error {
	if len(args) != 1 {
		return usageError("screencap <file.png>")
	}

	return a.withTV(func(session *tv.Session) error {
		png, err := session.Exec("screencap -p")
		if err != nil {
			return err
		}
		if !bytes.HasPrefix(png, []byte("\x89PNG")) {
			return fmt.Errorf("the TV did not return a PNG: %.200s", png)
		}
		return os.WriteFile(args[0], png, 0o644)
	})
}

const closedUsage = "usage: tvctl closed [tv ip] [port]"

func (a *app) closed(args []string) int {
	host, port, err := a.closedTarget(args)
	if err != nil {
		fmt.Fprintln(a.stderr, err)
		return int(netcheck.Failed)
	}

	result, message := netcheck.ADBPort(host, port)
	if result == netcheck.Closed {
		fmt.Fprintln(a.stdout, message)
	} else {
		fmt.Fprintln(a.stderr, message)
	}

	return int(result)
}

func (a *app) closedTarget(args []string) (string, int, error) {
	if len(args) > 2 || (len(args) == 1 && (args[0] == "-h" || args[0] == "--help")) {
		return "", 0, errors.New(closedUsage)
	}

	host, port := a.host, a.port
	if len(args) >= 1 {
		host = args[0]
	}

	if len(args) == 2 {
		parsed, err := strconv.Atoi(args[1])
		if err != nil {
			return "", 0, fmt.Errorf("not a port number: %q", args[1])
		}
		port = parsed
	}

	if host == "" {
		return "", 0, errors.New(closedUsage)
	}

	return host, port, nil
}
