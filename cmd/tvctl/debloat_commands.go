package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/kienmai160598/free-android-tv/internal/debloat"
	"github.com/kienmai160598/free-android-tv/internal/tv"
)

func (a *app) disable(args []string) error {
	flags := newFlags("disable", a.stderr)
	var opts debloat.DisableOptions
	flags.BoolVar(&opts.ForceHome, "force-home", envBool("FORCE_HOME"), "")
	flags.BoolVar(&opts.ForceSetupWizard, "force-setupwraith", envBool("FORCE_SETUPWRAITH"), "")
	if err := flags.Parse(args); err != nil || flags.NArg() != 1 {
		return usageError("disable [--force-home] [--force-setupwraith] <debloat list>")
	}
	list := flags.Arg(0)

	if _, err := debloat.ReadList(list); err != nil {
		return err
	}
	return a.withTV(func(s *tv.Session) error {
		return debloat.Disable(s, a.recordPath(list), list, a.host, opts, a.stdout, a.stderr)
	})
}

func (a *app) restore(args []string) error {
	flags := newFlags("restore", a.stderr)
	dryRun := flags.Bool("dry-run", false, "")
	record := flags.String("record", "", "")
	if err := flags.Parse(args); err != nil || flags.NArg() < 1 || flags.NArg() > 2 {
		return usageError("restore [--dry-run] [--record <file>] <debloat list> [stock launcher package]")
	}
	if *record == "" {
		*record = a.recordPath(flags.Arg(0))
	}
	commands, err := debloat.RestoreCommands(*record, flags.Arg(0), flags.Arg(1), a.stderr)
	if err != nil {
		return err
	}
	if *dryRun {
		fmt.Fprintln(a.stdout, strings.Join(commands, "\n"))
		return nil
	}
	return a.withTV(func(s *tv.Session) error { return tv.Batch(s, commands, a.stdout) })
}

func (a *app) recordPath(list string) string {
	home, _ := os.UserHomeDir()
	return debloat.RecordPath(home, list, a.host)
}
