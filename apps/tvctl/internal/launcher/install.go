package launcher

import (
	"errors"
	"fmt"
	"io"
	"strings"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

const NoStock = "none"

const remoteAPK = "/data/local/tmp/tvhome.apk"

const setupWizardHint = "On Google TV this usually means " + tv.SetupWizard + " also claims " +
	"Home: add it to your debloat list (see docs/troubleshooting.md), apply the list, " +
	"then run this again."

type Options struct {
	Host            string
	Stock           string
	InputKeyService bool
	HomeKeyService  bool
	NowPlaying      bool
	HandoverDir     string
}

func Install(dev tv.Device, apk string, opts Options, out, warn io.Writer) error {
	stock, err := chooseStockLauncher(dev, opts.Stock, out)
	if err != nil {
		return err
	}

	fmt.Fprintf(out, "Stock launcher: %s\n", stock)

	if err := installAPK(dev, apk, out); err != nil {
		return err
	}

	if err := startLauncher(dev, out); err != nil {
		return fmt.Errorf("%w; %s was left enabled", err, stock)
	}

	handHomeOver(dev, stock, out)
	if err := confirmHome(dev, stock, opts, out, warn); err != nil {
		return err
	}

	enableRequestedServices(dev, opts, out)
	fmt.Fprintln(out, "Installed. Press Home on the remote to see it.")
	return nil
}

func installAPK(dev tv.Device, apk string, out io.Writer) error {
	if err := dev.Push(apk, remoteAPK); err != nil {
		return fmt.Errorf("pushing %s: %w", apk, err)
	}

	answer, err := dev.Shell("pm install -r " + remoteAPK + "; rm -f " + remoteAPK)
	fmt.Fprint(out, answer)
	if err != nil || !strings.Contains(answer, "Success") {
		return errors.New("pm install failed; the stock launcher was left untouched")
	}

	return nil
}

func startLauncher(dev tv.Device, out io.Writer) error {
	answer, err := dev.Shell("am start -W -n " + tv.LauncherActivity)
	fmt.Fprint(out, answer)

	started := err == nil && strings.Contains(answer, "Status: ok") && !hasErrorLine(answer)
	if !started {
		return errors.New("the new launcher did not start (see above)")
	}

	return nil
}

func hasErrorLine(output string) bool {
	for _, line := range strings.Split(output, "\n") {
		if strings.HasPrefix(strings.ToLower(strings.TrimSpace(line)), "error") {
			return true
		}
	}

	return false
}

func handHomeOver(dev tv.Device, stock string, out io.Writer) {
	disableStock := "echo stock launcher left alone"
	if stock != NoStock {
		disableStock = "pm disable-user --user 0 " + stock
	}

	tv.Batch(dev, []string{
		"pm grant " + tv.Launcher + " android.permission.ACCESS_FINE_LOCATION 2>/dev/null; " +
			"echo location-granted",
		"cmd package compile -m speed -f " + tv.Launcher,
		disableStock,
		"cmd package set-home-activity --user 0 " + tv.LauncherActivity,
	}, out)
}

func confirmHome(dev tv.Device, stock string, opts Options, out, warn io.Writer) error {
	home := tv.Home(dev)
	if strings.HasPrefix(home, tv.Launcher+"/") {
		fmt.Fprintf(out, "Home now resolves to %s\n", home)
		return nil
	}

	if home == "" {
		fmt.Fprintln(warn, "ERROR: the TV stopped answering while Home was being switched, "+
			"so the outcome is unknown.")
	} else {
		fmt.Fprintf(warn, "ERROR: Home resolves to %s, not to the new launcher.\n", home)
	}

	rollBackToStock(dev, stock, opts, warn)
	if home != "" {
		fmt.Fprintln(warn, setupWizardHint)
	}

	return errors.New("Home was not handed over")
}
