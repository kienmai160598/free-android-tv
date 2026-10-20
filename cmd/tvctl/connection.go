package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/kienmai160598/free-android-tv/internal/adb"
	"github.com/kienmai160598/free-android-tv/internal/tv"
)

func (a *app) session() (*tv.Session, error) {
	if a.host == "" {
		return nil, errors.New("set the TV's IP address with --host or TV_HOST, e.g. TV_HOST=192.168.1.50 tvctl probe\n" +
			"(find it under Settings > Network, or look for the TV in `arp -a`)")
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
			fmt.Fprintln(a.stderr, `On the TV: allow "USB debugging" for this computer (tick "Always allow").`)
		}},
	}, nil
}

func keyPaths() []string {
	var paths []string
	if key := os.Getenv("TVCTL_KEY"); key != "" {
		paths = append(paths, key)
	}
	if exe, err := os.Executable(); err == nil {
		paths = append(paths, filepath.Join(filepath.Dir(exe), "..", "adbkey", "adbkey"))
	}
	if home, err := os.UserHomeDir(); err == nil {
		paths = append(paths, filepath.Join(home, ".android", "adbkey"))
	}
	return paths
}

func (a *app) withTV(fn func(*tv.Session) error) error {
	s, err := a.session()
	if err != nil {
		return err
	}
	defer s.Close()
	return fn(s)
}
