package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/adb"
	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

var errMissingHost = errors.New("set the TV's IP address with --host or TV_HOST, " +
	"e.g. TV_HOST=192.168.1.50 tvctl probe\n" +
	"(find it under Settings > Network, or look for the TV in `arp -a`)")

func (a *app) session() (*tv.Session, error) {
	if a.host == "" {
		return nil, errMissingHost
	}

	key, err := adb.LoadKey(keyPaths()...)
	if err != nil {
		return nil, err
	}

	return &tv.Session{
		Host: a.host,
		Port: a.port,
		Key:  key,
		Opts: adb.Options{OnPrompt: func() {
			fmt.Fprintln(
				a.stderr,
				`On the TV: allow "USB debugging" for this computer (tick "Always allow").`,
			)
		}},
	}, nil
}

func keyPaths() []string {
	var paths []string
	if key := os.Getenv("TVCTL_KEY"); key != "" {
		paths = append(paths, key)
	}

	if home, err := os.UserHomeDir(); err == nil {
		paths = append(paths, filepath.Join(home, ".android", "adbkey"))
	}

	return paths
}

func (a *app) withTV(use func(*tv.Session) error) error {
	session, err := a.session()
	if err != nil {
		return err
	}
	defer session.Close()

	return use(session)
}

func (a *app) sendBatch(commands []string) error {
	return a.withTV(func(session *tv.Session) error {
		return tv.Batch(session, commands, a.stdout)
	})
}
