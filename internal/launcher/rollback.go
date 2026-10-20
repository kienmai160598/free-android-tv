package launcher

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/kienmai160598/free-android-tv/internal/tv"
)

func rollBackToStock(dev tv.Device, stock string, opts Options, warn io.Writer) {
	if stock == NoStock {
		fmt.Fprintln(warn, "ERROR: Home is not the new launcher, and no stock launcher was disabled; nothing to roll back.")
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
	if rescueFile := writeRescueCommands(stock, opts); rescueFile != "" {
		fmt.Fprintf(warn, "The commands that finish it are in %s; the TV still needs ADB for them.\n", rescueFile)
	} else {
		fmt.Fprintf(warn, "Run again once the TV answers: pm enable --user 0 %s, then set its Home activity.\n", stock)
	}
}

func writeRescueCommands(stock string, opts Options) string {
	dir := opts.HandoverDir
	if dir == "" {
		dir, _ = os.UserHomeDir()
	}
	path := filepath.Join(dir, "free-android-tv-handover-"+tv.FileNameSafe(opts.Host)+".txt")
	commands := fmt.Sprintf("# free-android-tv: the launcher handover on %[1]s did not finish. From the repo root:\n"+
		"tools/tvctl --host %[1]s shell 'pm enable --user 0 %[2]s'\n"+
		"tools/tvctl --host %[1]s shell '%[3]s'\n"+
		"# then check: tools/tvctl --host %[1]s shell '%[4]s'\n",
		opts.Host, stock, tv.SetHomeTo(stock), tv.HomeQuery)
	if err := os.WriteFile(path, []byte(commands), 0o644); err != nil {
		return ""
	}
	return path
}
