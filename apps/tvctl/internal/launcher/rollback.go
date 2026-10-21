package launcher

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/tv"
)

const rescueCommands = "# freeTV: the launcher handover on %[1]s did not finish. " +
	"From the repo root:\n" +
	"scripts/tvctl --host %[1]s shell 'pm enable --user 0 %[2]s'\n" +
	"scripts/tvctl --host %[1]s shell '%[3]s'\n" +
	"# then check: scripts/tvctl --host %[1]s shell '%[4]s'\n"

func rollBackToStock(dev tv.Device, stock string, opts Options, warn io.Writer) {
	if stock == NoStock {
		fmt.Fprintln(warn, "ERROR: Home is not the new launcher, and no stock launcher was "+
			"disabled; nothing to roll back.")
		return
	}

	dev.Shell("pm enable --user 0 " + stock)
	dev.Shell(tv.SetHomeTo(stock))

	home := tv.Home(dev)
	if strings.HasPrefix(home, stock+"/") {
		fmt.Fprintf(warn, "%s owns Home again.\n", stock)
		return
	}

	if home == "" {
		home = "nothing"
	}

	fmt.Fprintf(warn, "ERROR: recovery is incomplete; Home still resolves to '%s'.\n", home)

	rescueFile := writeRescueCommands(stock, opts)
	if rescueFile == "" {
		fmt.Fprintf(warn, "Run again once the TV answers: pm enable --user 0 %s, "+
			"then set its Home activity.\n", stock)
		return
	}

	fmt.Fprintf(warn, "The commands that finish it are in %s; the TV still needs ADB for them.\n",
		rescueFile)
}

func writeRescueCommands(stock string, opts Options) string {
	directory := opts.HandoverDir
	if directory == "" {
		directory, _ = os.UserHomeDir()
	}

	fileName := "freeTV-handover-" + tv.FileNameSafe(opts.Host) + ".txt"
	path := filepath.Join(directory, fileName)

	commands := fmt.Sprintf(rescueCommands, opts.Host, stock, tv.SetHomeTo(stock), tv.HomeQuery)
	if err := os.WriteFile(path, []byte(commands), 0o644); err != nil {
		return ""
	}

	return path
}
