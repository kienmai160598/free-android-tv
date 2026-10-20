package debloat

import (
	"fmt"
	"io"
	"strings"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

type DisableOptions struct {
	ForceSetupWizard bool
	ForceHome        bool
}

const insecureADBWarning = "Warning: this TV reports ro.adb.secure=0, so network ADB on port " +
	"5555 accepts any device on your network without the \"Allow\" prompt.\n" +
	"When you are done, turn USB debugging and Network/ADB debugging off on the TV, reboot it, " +
	"then check with: scripts/tvctl closed.\n"

func Disable(
	dev tv.Device,
	recordPath, listPath, host string,
	opts DisableOptions,
	out, warn io.Writer,
) error {
	names, err := ReadList(listPath)
	if err != nil {
		return err
	}

	targets := applyGuards(dev, names, opts, warn)
	adbIsInsecure := readADBIsInsecure(dev)

	if err := SaveRecord(dev, recordPath, listPath, host, names, out); err != nil {
		return err
	}

	if err := tv.Batch(dev, disableCommands(targets), out); err != nil {
		return err
	}

	if adbIsInsecure {
		fmt.Fprint(warn, insecureADBWarning)
	}

	return nil
}

func applyGuards(dev tv.Device, names []string, opts DisableOptions, warn io.Writer) []string {
	targets := names
	if !opts.ForceSetupWizard {
		targets = keepSetupWizardIfItOwnsWifi(dev, targets, warn)
	}

	if !opts.ForceHome {
		targets = keepOnlyHome(dev, targets, warn)
	}

	return targets
}

func readADBIsInsecure(dev tv.Device) bool {
	answer, err := dev.Shell("getprop ro.adb.secure")
	return err == nil && strings.TrimSpace(answer) == "0"
}

func disableCommands(names []string) []string {
	commands := make([]string, 0, len(names)+1)
	for _, name := range names {
		commands = append(commands, "pm disable-user --user 0 "+name)
	}

	return append(commands, listDisabledPackages)
}
