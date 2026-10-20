package launcher

import (
	"fmt"
	"io"
	"slices"
	"strings"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

var knownStockLaunchers = []string{
	"com.google.android.tvlauncher",
	"com.google.android.apps.tv.launcherx",
	"com.google.android.leanbacklauncher",
	"com.mitv.tvhome",
}

const androidChooser = "android"

const homeQueryFailed = "could not ask the TV which launcher owns Home (%v). " +
	"Check the host and the debugging prompt on the TV, or pass --stock <package> yourself"

const chooserIsAmbiguous = "Home is Android's launcher chooser and %d known stock launchers " +
	"claim Home: pick a default on the TV first, or pass --stock <package> or --stock none"

var unknownHomeOwnerHelp = strings.Join([]string{
	"Home currently resolves to '%s', which is not a stock launcher this tool knows.",
	"  android                                  = Android's chooser; pick a default on the TV",
	"  com.google.android.tungsten.setupwraith  = Google TV's setup wizard; see troubleshooting",
	"  anything else                            = another launcher is the default",
	"Pass --stock <package to disable> or --stock none",
}, "\n")

func chooseStockLauncher(dev tv.Device, chosen string, out io.Writer) (string, error) {
	if chosen != "" {
		return validChosenLauncher(chosen)
	}

	answer, err := dev.Shell(tv.HomeQuery)
	if err != nil {
		return "", fmt.Errorf(homeQueryFailed, err)
	}

	owner := tv.PackageOf(tv.LastLine(answer))
	switch {
	case slices.Contains(knownStockLaunchers, owner):
		return owner, nil
	case owner == tv.Launcher:
		fmt.Fprintln(out, "Home already belongs to this launcher; nothing will be disabled.")
		return NoStock, nil
	case owner == androidChooser:
		return stockLauncherBehindChooser(dev, out)
	default:
		return "", unknownHomeOwner(owner)
	}
}

func validChosenLauncher(chosen string) (string, error) {
	if chosen != NoStock && !tv.PackageName.MatchString(chosen) {
		return "", fmt.Errorf("not a package name: %q", chosen)
	}

	return chosen, nil
}

func stockLauncherBehindChooser(dev tv.Device, out io.Writer) (string, error) {
	claimants, _ := tv.HomeClaimants(dev)
	var found []string
	for _, known := range knownStockLaunchers {
		if slices.Contains(claimants, known) {
			found = append(found, known)
		}
	}

	if len(found) != 1 {
		return "", fmt.Errorf(chooserIsAmbiguous, len(found))
	}

	fmt.Fprintf(out, "Home is Android's chooser; the stock launcher among the candidates is %s.\n",
		found[0])
	return found[0], nil
}

func unknownHomeOwner(owner string) error {
	if owner == "" {
		owner = "nothing"
	}

	return fmt.Errorf(unknownHomeOwnerHelp, owner)
}
