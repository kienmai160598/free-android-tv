package debloat

import (
	"fmt"
	"io"
	"regexp"
	"slices"
	"strings"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

var homeAnswerersThatAreNotLaunchers = []string{
	tv.SetupWizard,
	"com.android.tv.settings",
	"com.android.settings",
	"com.android.provision",
}

var wifiNetworkOwner = regexp.MustCompile(`cname=\S*`)

func keepSetupWizardIfItOwnsWifi(dev tv.Device, targets []string, warn io.Writer) []string {
	if !slices.Contains(targets, tv.SetupWizard) {
		return targets
	}
	dump, err := dev.Shell("dumpsys wifi")
	owners := wifiNetworkOwner.FindAllString(dump, -1)
	if err != nil || len(owners) == 0 {
		fmt.Fprintf(warn, "Skipping %s: could not check which app owns the saved Wi-Fi networks (dumpsys wifi failed\n"+
			"or showed none). Try again, or pass --force-setupwraith if the TV uses Ethernet.\n", tv.SetupWizard)
		return without(targets, tv.SetupWizard)
	}
	if slices.ContainsFunc(owners, func(owner string) bool { return strings.Contains(owner, "setupwraith") }) {
		fmt.Fprintf(warn, "Skipping %s: the saved Wi-Fi network belongs to it, and disabling it would delete that\n"+
			"network and take the TV off Wi-Fi. On the TV, forget the network and join it again in\n"+
			"Settings > Network & Internet, then run this again (or pass --force-setupwraith on Ethernet).\n", tv.SetupWizard)
		return without(targets, tv.SetupWizard)
	}
	return targets
}

func keepOnlyHome(dev tv.Device, targets []string, warn io.Writer) []string {
	owner := tv.PackageOf(tv.Home(dev))
	if owner == "" || !slices.Contains(targets, owner) || anotherLauncherStays(dev, owner, targets) {
		return targets
	}
	fmt.Fprintf(warn, "Skipping %s: it owns Home, and no other app that can answer Home is installed and\n"+
		"staying enabled. Make the new launcher Home first (launcher/build-and-install.sh), then run\n"+
		"this list again to disable the old one; or pass --force-home if you can still reach the TV.\n", owner)
	return without(targets, owner)
}

func anotherLauncherStays(dev tv.Device, owner string, targets []string) bool {
	claimants, err := tv.HomeClaimants(dev)
	if err != nil {
		return false
	}
	return slices.ContainsFunc(claimants, func(claimant string) bool {
		return claimant != owner &&
			!slices.Contains(targets, claimant) &&
			!slices.Contains(homeAnswerersThatAreNotLaunchers, claimant)
	})
}

func without(names []string, unwanted string) []string {
	return slices.DeleteFunc(slices.Clone(names), func(name string) bool { return name == unwanted })
}
