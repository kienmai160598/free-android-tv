package tv

import (
	"regexp"
	"sort"
	"strings"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/contract"
)

const homeIntent = "-a android.intent.action.MAIN -c android.intent.category.HOME"

const (
	HomeQuery       = "cmd package resolve-activity --brief " + homeIntent
	HomeClaimsQuery = "cmd package query-activities --brief " + homeIntent
)

const (
	Launcher         = contract.LauncherPackage
	LauncherActivity = Launcher + "/" + contract.MainActivity
	StockLauncher    = "com.google.android.tvlauncher"
	SetupWizard      = "com.google.android.tungsten.setupwraith"
)

var (
	PackageName = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`)
	Component   = regexp.MustCompile(`^[A-Za-z_][\w.]*/[\w.$]+$`)
	claimLine   = regexp.MustCompile(`^\s*([A-Za-z0-9_.]+)/`)
)

func LastLine(output string) string {
	lines := outputLines(output)
	for index := len(lines) - 1; index >= 0; index-- {
		if line := strings.TrimSpace(lines[index]); line != "" {
			return line
		}
	}

	return ""
}

func outputLines(output string) []string {
	return strings.Split(strings.ReplaceAll(output, "\r", ""), "\n")
}

func Home(dev Device) string {
	output, err := dev.Shell(HomeQuery)
	if err != nil {
		return ""
	}

	return LastLine(output)
}

func PackageOf(component string) string {
	packageName, _, _ := strings.Cut(component, "/")
	return packageName
}

func SetHomeTo(packageName string) string {
	return "cmd package set-home-activity --user 0 $(" + HomeQuery + " " + packageName +
		" | tail -n 1)"
}

func HomeClaimants(dev Device) ([]string, error) {
	output, err := dev.Shell(HomeClaimsQuery)
	if err != nil {
		return nil, err
	}

	seen := map[string]bool{}
	var claimants []string
	for _, line := range outputLines(output) {
		match := claimLine.FindStringSubmatch(line)
		if match == nil || seen[match[1]] {
			continue
		}
		seen[match[1]] = true
		claimants = append(claimants, match[1])
	}

	sort.Strings(claimants)
	return claimants, nil
}

func Packages(dev Device, flags string) (map[string]bool, error) {
	command := strings.TrimSpace("pm list packages " + flags)
	output, err := dev.Shell(command)
	if err != nil {
		return nil, err
	}

	names := map[string]bool{}
	for _, line := range strings.Split(output, "\n") {
		if name, isPackage := strings.CutPrefix(strings.TrimSpace(line), "package:"); isPackage {
			names[strings.TrimSpace(name)] = true
		}
	}

	return names, nil
}
