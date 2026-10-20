package debloat

import (
	"errors"
	"fmt"
	"io"
	"os"
	"regexp"
	"slices"
	"strings"
	"time"

	"github.com/kienmai160598/free-android-tv/internal/tv"
)

const (
	stateEnabled  = "enabled"
	stateDisabled = "disabled"
	stateAbsent   = "absent"
)

var AnimationScales = []string{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"}

var scaleValue = regexp.MustCompile(`^-?\d+(\.\d+)?$`)

type packageSnapshot struct {
	installed map[string]bool
	disabled  map[string]bool
}

func readPackageSnapshot(dev tv.Device) (*packageSnapshot, error) {
	installed, err := tv.Packages(dev, "")
	if err == nil && len(installed) == 0 {
		err = errors.New("the TV returned no packages")
	}
	if err != nil {
		return nil, fmt.Errorf("%v, so nothing was recorded and nothing will be disabled. "+
			"Check the connection (tvctl probe) and try again", err)
	}
	disabled, err := tv.Packages(dev, "-d")
	if err != nil {
		return nil, err
	}
	return &packageSnapshot{installed: installed, disabled: disabled}, nil
}

func (s *packageSnapshot) stateOf(name string) string {
	switch {
	case !s.installed[name]:
		return stateAbsent
	case s.disabled[name]:
		return stateDisabled
	default:
		return stateEnabled
	}
}

func SaveRecord(dev tv.Device, path, listPath, host string, names []string, out io.Writer) error {
	if _, err := os.Stat(path); err == nil {
		return extendRecord(dev, path, names, out)
	}
	record, err := takeRecord(dev, listPath, host, names)
	if err != nil {
		return err
	}
	if err := writeRecord(path, record); err != nil {
		return err
	}
	reportNewRecord(out, record, path)
	return nil
}

func takeRecord(dev tv.Device, listPath, host string, names []string) (*Record, error) {
	packages, err := readPackageSnapshot(dev)
	if err != nil {
		return nil, err
	}
	home := readHomeComponent(dev)
	settings, err := readAnimationScales(dev)
	if err != nil {
		return nil, err
	}
	record := &Record{
		Version:  recordVersion,
		Created:  time.Now().Format("2006-01-02T15:04:05-0700"),
		Host:     host,
		List:     resolvedPath(listPath),
		Home:     home,
		Settings: settings,
		Packages: map[string]string{},
	}
	for _, name := range withHomePackage(names, home) {
		record.Packages[name] = packages.stateOf(name)
	}
	return record, nil
}

func readHomeComponent(dev tv.Device) *string {
	answer := tv.Home(dev)
	if !tv.Component.MatchString(answer) {
		return nil
	}
	return &answer
}

func readAnimationScales(dev tv.Device) (map[string]any, error) {
	settings := map[string]any{}
	for _, key := range AnimationScales {
		value, err := dev.Shell("settings get global " + key)
		if err != nil {
			return nil, err
		}
		settings[key] = strings.TrimSpace(value)
	}
	return settings, nil
}

func withHomePackage(names []string, home *string) []string {
	if home == nil || slices.Contains(names, tv.PackageOf(*home)) {
		return names
	}
	return append(slices.Clone(names), tv.PackageOf(*home))
}

func reportNewRecord(out io.Writer, record *Record, path string) {
	homeText := "no component answered"
	if record.Home != nil {
		homeText = *record.Home
	}
	fmt.Fprintf(out, "recorded %d packages on %s (%d already disabled, left alone by undo) -> %s\n",
		len(record.Packages), record.Host, record.countState(stateDisabled), path)
	fmt.Fprintf(out, "HOME before: %s\n", homeText)
	fmt.Fprintln(out, "undo with tvctl restore <same list>; keep this file, it is the only copy.")
}

func extendRecord(dev tv.Device, path string, names []string, out io.Writer) error {
	record, err := LoadRecord(path)
	if err != nil {
		return err
	}
	fmt.Fprintf(out, "keeping the recovery record from %s: %s\n", record.Created, path)
	added := record.missing(names)
	if len(added) == 0 {
		fmt.Fprintln(out, "nothing was recorded again: it holds the state from before the first run.")
		return nil
	}
	packages, err := readPackageSnapshot(dev)
	if err != nil {
		return err
	}
	for _, name := range added {
		record.Packages[name] = packages.stateOf(name)
	}
	if err := writeRecord(path, record); err != nil {
		return err
	}
	fmt.Fprintf(out, "added %d names the list gained since then, entries already recorded are unchanged: %s\n",
		len(added), strings.Join(added, ", "))
	return nil
}
