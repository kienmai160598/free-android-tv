package main

import (
	"os"

	"github.com/kienmai160598/free-android-tv/internal/launcher"
	"github.com/kienmai160598/free-android-tv/internal/theme"
	"github.com/kienmai160598/free-android-tv/internal/tv"
)

func (a *app) install(args []string) error {
	flags := newFlags("install", a.stderr)
	opts := launcher.Options{Host: a.host}
	flags.StringVar(&opts.Stock, "stock", os.Getenv("STOCK_LAUNCHER"), "")
	flags.BoolVar(&opts.InputKeyService, "input-key-service", envBool("INPUT_KEY_SERVICE"), "")
	flags.BoolVar(&opts.HomeKeyService, "home-key-service", envBool("HOME_KEY_SERVICE"), "")
	flags.BoolVar(&opts.NowPlaying, "now-playing", envBool("NOW_PLAYING"), "")
	if err := flags.Parse(args); err != nil || flags.NArg() != 1 {
		return usageError("install [--stock <package|none>] [--input-key-service] [--home-key-service] [--now-playing] <apk>")
	}
	return a.withTV(func(s *tv.Session) error {
		return launcher.Install(s, flags.Arg(0), opts, a.stdout, a.stderr)
	})
}

func (a *app) theme(args []string) error {
	if len(args) != 1 {
		return usageError("theme <theme folder> | theme --reset")
	}
	return a.withTV(func(s *tv.Session) error {
		if args[0] == "--reset" {
			return theme.Reset(s, a.stdout)
		}
		return theme.Apply(s, args[0], a.stdout)
	})
}
