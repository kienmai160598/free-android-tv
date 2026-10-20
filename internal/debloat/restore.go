package debloat

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"sort"
	"strings"

	"github.com/kienmai160598/free-android-tv/internal/tv"
)

const listDisabledPackages = "pm list packages -d"

func RestoreCommands(recordPath, listPath, stock string, warn io.Writer) ([]string, error) {
	names, err := ReadList(listPath)
	if err != nil {
		return nil, err
	}
	stock, err = validStockLauncher(stock)
	if err != nil {
		return nil, err
	}
	if _, err := os.Stat(recordPath); err != nil {
		return commandsWithoutRecord(recordPath, names, stock, warn)
	}
	record, err := LoadRecord(recordPath)
	if err != nil {
		return nil, err
	}
	return commandsFromRecord(record, names, stock, warn), nil
}

func validStockLauncher(stock string) (string, error) {
	if stock == "" {
		return tv.StockLauncher, nil
	}
	if !tv.PackageName.MatchString(stock) {
		return "", fmt.Errorf("not a package name: %q", stock)
	}
	return stock, nil
}

func commandsFromRecord(record *Record, names []string, stock string, warn io.Writer) []string {
	enabled := packagesToEnable(record, warn)
	commands := enableCommands(enabled)
	if record.Packages[stock] != stateDisabled && !slices.Contains(enabled, stock) {
		commands = append(commands, "pm enable "+stock)
	}
	commands = append(commands, homeCommand(record, stock))
	commands = append(commands, animationScaleCommands(record, warn)...)
	if added := record.missing(names); len(added) > 0 {
		fmt.Fprintf(warn, "not in the record, leaving alone: %s\n", strings.Join(added, ", "))
	}
	fmt.Fprintf(warn, "restoring the state recorded on %s: re-enabling %d, leaving %d as they were.\n",
		record.Created, len(enabled), len(record.Packages)-len(enabled))
	return append(commands, listDisabledPackages)
}

func packagesToEnable(record *Record, warn io.Writer) []string {
	names := make([]string, 0, len(record.Packages))
	for name := range record.Packages {
		names = append(names, name)
	}
	sort.Strings(names)
	var enabled []string
	for _, name := range names {
		if state := record.Packages[name]; state != stateEnabled {
			fmt.Fprintf(warn, "  leaving %s alone (%s when the record was taken)\n", name, state)
			continue
		}
		enabled = append(enabled, name)
	}
	return enabled
}

func enableCommands(names []string) []string {
	commands := make([]string, 0, len(names))
	for _, name := range names {
		commands = append(commands, "pm enable "+name)
	}
	return commands
}

func homeCommand(record *Record, stock string) string {
	if record.Home != nil && tv.PackageOf(*record.Home) != tv.Launcher {
		return "cmd package set-home-activity --user 0 " + *record.Home
	}
	return tv.SetHomeTo(stock)
}

func animationScaleCommands(record *Record, warn io.Writer) []string {
	var commands []string
	for _, key := range AnimationScales {
		value := recordedSetting(record, key)
		switch {
		case value == "null":
			commands = append(commands, "settings delete global "+key)
		case scaleValue.MatchString(value):
			commands = append(commands, fmt.Sprintf("settings put global %s %s", key, value))
		default:
			fmt.Fprintf(warn, "  skipping %s: the record holds %q, not a number\n", key, value)
		}
	}
	return commands
}

func recordedSetting(record *Record, key string) string {
	value, ok := record.Settings[key]
	if !ok || value == nil {
		return ""
	}
	return strings.TrimSpace(fmt.Sprint(value))
}

func commandsWithoutRecord(recordPath string, names []string, stock string, warn io.Writer) ([]string, error) {
	if others := otherRecordsForSameTV(recordPath); len(others) > 0 {
		return nil, fmt.Errorf("no recovery record for this list at %s, but this TV has others:\n  %s\n"+
			"pass the one taken for this list with --record", recordPath, strings.Join(others, "\n  "))
	}
	fmt.Fprintf(warn, "no recovery record for this TV and list (%s).\n"+
		"Enabling every listed package and resetting Home and the animation scales to their\n"+
		"defaults. Packages that were already disabled before cleanup will be enabled too:\n"+
		"check the list before running this.\n", recordPath)
	commands := enableCommands(names)
	commands = append(commands, "pm enable "+stock, tv.SetHomeTo(stock))
	for _, key := range AnimationScales {
		commands = append(commands, "settings put global "+key+" 1.0")
	}
	return append(commands, listDisabledPackages), nil
}

func otherRecordsForSameTV(recordPath string) []string {
	fileName := filepath.Base(recordPath)
	hostPrefix := fileName[:strings.LastIndex(fileName, "-")+1]
	matches, _ := filepath.Glob(filepath.Join(filepath.Dir(recordPath), hostPrefix+"*.json"))
	var others []string
	for _, match := range matches {
		record, err := LoadRecord(match)
		if err != nil {
			others = append(others, match)
			continue
		}
		others = append(others, fmt.Sprintf("%s (list %s, %s)", match, record.List, record.Created))
	}
	return others
}
