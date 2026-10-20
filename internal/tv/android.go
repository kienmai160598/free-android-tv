package tv

import (
	"regexp"
	"sort"
	"strings"
)

const (
	HomeQuery        = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
	HomeClaimsQuery  = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
	Launcher         = "com.gathin.tvhome"
	LauncherActivity = Launcher + "/.MainActivity"
	StockLauncher    = "com.google.android.tvlauncher"
	SetupWizard      = "com.google.android.tungsten.setupwraith"
)

var (
	PackageName = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`)
	Component   = regexp.MustCompile(`^[A-Za-z_][\w.]*/[\w.$]+$`)
	claimLine   = regexp.MustCompile(`^\s*([A-Za-z0-9_.]+)/`)
)

func LastLine(out string) string {
	lines := strings.Split(strings.ReplaceAll(out, "\r", ""), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		if line := strings.TrimSpace(lines[i]); line != "" {
			return line
		}
	}
	return ""
}

func Home(dev Device) string {
	out, err := dev.Shell(HomeQuery)
	if err != nil {
		return ""
	}
	return LastLine(out)
}

func PackageOf(component string) string {
	pkg, _, _ := strings.Cut(component, "/")
	return pkg
}

func HomeClaimants(dev Device) ([]string, error) {
	out, err := dev.Shell(HomeClaimsQuery)
	if err != nil {
		return nil, err
	}
	seen := map[string]bool{}
	var claimants []string
	for _, line := range strings.Split(strings.ReplaceAll(out, "\r", ""), "\n") {
		if m := claimLine.FindStringSubmatch(line); m != nil && !seen[m[1]] {
			seen[m[1]] = true
			claimants = append(claimants, m[1])
		}
	}
	sort.Strings(claimants)
	return claimants, nil
}

func Packages(dev Device, flags string) (map[string]bool, error) {
	command := "pm list packages"
	if flags != "" {
		command += " " + flags
	}
	out, err := dev.Shell(command)
	if err != nil {
		return nil, err
	}
	names := map[string]bool{}
	for _, line := range strings.Split(out, "\n") {
		if name, ok := strings.CutPrefix(strings.TrimSpace(line), "package:"); ok {
			names[strings.TrimSpace(name)] = true
		}
	}
	return names, nil
}

func SetHomeTo(pkg string) string {
	return "cmd package set-home-activity --user 0 $(" + HomeQuery + " " + pkg + " | tail -n 1)"
}
