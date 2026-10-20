package main

import (
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"strconv"
)

const usage = `tvctl talks to an Android TV over network ADB.
On the TV: Settings > Device Preferences > About, press Build 7 times,
then Developer options > USB debugging (and Network debugging if present).

usage: tvctl [--host IP] [--port 5555] <command> [arguments]

  probe                            model, Android version and ADB security of the TV
  shell <command...>               run one shell command
  batch < commands.txt             one command per line, on one connection
  push <local> <remote>            copy a file to the TV (checked with md5)
  pull <remote> <local>            copy a file from the TV
  screencap <file.png>             screenshot of what the TV shows

  disable [flags] <debloat list>   disable every listed package, guarded and recorded for undo
      --force-home                 also disable the Home owner when nothing else answers Home
      --force-setupwraith          also disable setupwraith when it may own the Wi-Fi network
  restore [flags] <list> [stock launcher]
                                   put back what disable changed, from its recovery record
      --dry-run                    print the commands instead of sending them
      --record <file>              the record to replay (default: the one for this TV and list path)
  install [flags] <apk>            install the launcher and hand Home over to it, with rollback
      --stock <package|none>       stock launcher to disable (default: ask the TV)
      --input-key-service          remote's Input button and app keys (pass on every install)
      --home-key-service           Home buttons that send KEYCODE_ALL_APPS (Xiaomi sticks)
      --now-playing                let the Now playing widget read media sessions
  theme <folder> | theme --reset   push a theme to the launcher, or remove it

  closed [ip] [port]               check network ADB is off again (exit 0 closed, 1 open, 2 unknown)

The host and port default to $TV_HOST and $TV_PORT. The ADB key is ~/.android/adbkey, the one
platform-tools uses, so a TV that already trusts adb is not asked again.
`

func main() {
	os.Exit(run(os.Args[1:], os.Stdin, os.Stdout, os.Stderr))
}

type app struct {
	host   string
	port   int
	stdin  io.Reader
	stdout io.Writer
	stderr io.Writer
}

func run(args []string, stdin io.Reader, stdout, stderr io.Writer) int {
	global := flag.NewFlagSet("tvctl", flag.ContinueOnError)
	global.SetOutput(stderr)
	global.Usage = func() { fmt.Fprint(stderr, usage) }
	a := &app{stdin: stdin, stdout: stdout, stderr: stderr}
	global.StringVar(&a.host, "host", os.Getenv("TV_HOST"), "TV address")
	global.IntVar(&a.port, "port", envInt("TV_PORT", 5555), "TV ADB port")
	if err := global.Parse(args); err != nil {
		return 2
	}

	if global.NArg() == 0 {
		fmt.Fprint(stderr, usage)
		return 2
	}

	command, rest := global.Arg(0), global.Args()[1:]
	if command == "closed" {
		return a.closed(rest)
	}

	handlers := map[string]func([]string) error{
		"probe":     a.probe,
		"shell":     a.shell,
		"batch":     a.batch,
		"push":      a.push,
		"pull":      a.pull,
		"screencap": a.screencap,
		"disable":   a.disable,
		"restore":   a.restore,
		"install":   a.install,
		"theme":     a.theme,
	}

	handler, ok := handlers[command]
	if !ok {
		fmt.Fprintf(stderr, "tvctl: unknown command %q\n\n%s", command, usage)
		return 2
	}

	if err := handler(rest); err != nil {
		var usageErr usageError
		if errors.As(err, &usageErr) {
			fmt.Fprintf(stderr, "usage: tvctl %s\n", usageErr)
			return 2
		}
		fmt.Fprintf(stderr, "tvctl %s: %v\n", command, err)
		return 1
	}

	return 0
}

func newFlags(name string, stderr io.Writer) *flag.FlagSet {
	flags := flag.NewFlagSet(name, flag.ContinueOnError)
	flags.SetOutput(stderr)
	flags.Usage = func() {}
	return flags
}

type usageError string

func (u usageError) Error() string { return string(u) }

func envInt(name string, fallback int) int {
	if value, err := strconv.Atoi(os.Getenv(name)); err == nil {
		return value
	}

	return fallback
}

func envBool(name string) bool {
	value := os.Getenv(name)
	return value != "" && value != "0"
}
