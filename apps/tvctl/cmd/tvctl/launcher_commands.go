package main

import (
	"os"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/launcher"
	"github.com/kienmai160598/freeTV/apps/tvctl/internal/theme"
	"github.com/kienmai160598/freeTV/apps/tvctl/internal/tv"
)

const installUsage = "install [--stock <package|none>] [--input-key-service] " +
	"[--home-key-service] [--now-playing] <apk>"

func (a *app) install(args []string) error {
	flags := newFlags("install", a.stderr)
	opts := launcher.Options{Host: a.host}
	flags.StringVar(&opts.Stock, "stock", os.Getenv("STOCK_LAUNCHER"), "")
	flags.BoolVar(&opts.InputKeyService, "input-key-service", envBool("INPUT_KEY_SERVICE"), "")
	flags.BoolVar(&opts.HomeKeyService, "home-key-service", envBool("HOME_KEY_SERVICE"), "")
	flags.BoolVar(&opts.NowPlaying, "now-playing", envBool("NOW_PLAYING"), "")
	if err := flags.Parse(args); err != nil || flags.NArg() != 1 {
		return usageError(installUsage)
	}

	return a.withTV(func(session *tv.Session) error {
		return launcher.Install(session, flags.Arg(0), opts, a.stdout, a.stderr)
	})
}

func (a *app) theme(args []string) error {
	if len(args) != 1 {
		return usageError("theme <theme folder> | theme --reset")
	}

	return a.withTV(func(session *tv.Session) error {
		if args[0] == "--reset" {
			return theme.Reset(session, a.stdout)
		}
		return theme.Apply(session, args[0], a.stdout)
	})
}
