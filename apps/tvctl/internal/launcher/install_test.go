package launcher

import (
	"bytes"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv/tvtest"
)

const stockHome = tv.StockLauncher + "/.MainActivity"

type fakeTV struct {
	home              string
	stockDisabled     bool
	installFails      bool
	startFails        bool
	setHomeFails      bool
	enableFails       bool
	dropAfterHandover bool
	linkLost          bool
	claims            []string
}

func (f *fakeTV) device() *tvtest.Fake {
	return &tvtest.Fake{Answer: func(command string) (string, error) {
		switch {
		case strings.Contains(command, "pm install"):
			if f.installFails {
				return "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]\n", nil
			}
			return "Success\n", nil
		case strings.Contains(command, "am start"):
			starting := "Starting: Intent { cmp=" + tv.LauncherActivity + " }\n"
			if f.startFails {
				return starting + "Error: Activity not started, unable to resolve Intent\n", nil
			}
			return starting + "Status: ok\nActivity: " + tv.LauncherActivity + "\n", nil
		case strings.Contains(command, "pm disable-user"):
			f.stockDisabled = true
			if f.dropAfterHandover {
				f.linkLost = true
			}
			return "", nil
		case strings.Contains(command, "pm enable"):
			if f.enableFails || f.linkLost {
				return "Error: java.lang.SecurityException\n", nil
			}
			f.stockDisabled = false
			return "", nil
		case strings.Contains(command, "set-home-activity"):
			if !f.setHomeFails && !f.linkLost {
				if strings.Contains(command, "resolve-activity") {
					f.home = stockHome
				} else {
					f.home = tv.LauncherActivity
				}
			}
			return "", nil
		case strings.Contains(command, "query-activities"):
			var b strings.Builder
			for _, claim := range f.claims {
				b.WriteString("  " + claim + "/.Main\n")
			}
			return b.String(), nil
		case strings.Contains(command, "resolve-activity"):
			if f.linkLost {
				return "", tvtest.ErrLinkLost
			}

			if f.stockDisabled && f.home == stockHome {
				return "android\n", nil
			}
			return f.home + "\n", nil
		}
		return "", nil
	}}
}

type outcome struct {
	err      error
	out      string
	warn     string
	commands []string
	handover string
}

func handover(t *testing.T, f *fakeTV, opts Options) outcome {
	t.Helper()
	if f.home == "" {
		f.home = stockHome
	}

	dir := t.TempDir()
	opts.Host = "192.168.1.50"
	opts.HandoverDir = dir
	dev := f.device()
	var out, warn bytes.Buffer
	err := Install(dev, filepath.Join(dir, "app-release.apk"), opts, &out, &warn)
	o := outcome{err: err, out: out.String(), warn: warn.String(), commands: dev.Commands}
	if files, _ := filepath.Glob(filepath.Join(dir, "free-android-tv-handover-*.txt")); len(
		files,
	) == 1 {
		data, _ := os.ReadFile(files[0])
		o.handover = string(data)
	}

	return o
}

func index(commands []string, part string) int {
	return slices.IndexFunc(commands, func(c string) bool { return strings.Contains(c, part) })
}

func TestSuccessHandsHomeOver(t *testing.T) {
	f := &fakeTV{}
	o := handover(t, f, Options{})
	if o.err != nil {
		t.Fatalf("%v\n%s", o.err, o.warn)
	}

	if !strings.Contains(o.out, "Installed.") {
		t.Errorf("output: %s", o.out)
	}

	if index(o.commands, "am start") > index(o.commands, "pm disable-user") {
		t.Error("the launcher is started before the stock Home is disabled")
	}

	if !f.stockDisabled || f.home != tv.LauncherActivity {
		t.Errorf("stock disabled %v, home %s", f.stockDisabled, f.home)
	}

	if o.handover != "" {
		t.Error("no rescue file on success")
	}
}

func TestFailedInstallLeavesStockHomeAlone(t *testing.T) {
	f := &fakeTV{installFails: true}
	o := handover(t, f, Options{})
	if o.err == nil || index(o.commands, "pm disable-user") >= 0 ||
		index(o.commands, "am start") >= 0 {
		t.Errorf("err %v, commands %v", o.err, o.commands)
	}
}

func TestLauncherThatDoesNotStartLeavesStockHomeEnabled(t *testing.T) {
	f := &fakeTV{startFails: true}
	o := handover(t, f, Options{})
	if o.err == nil || !strings.Contains(o.err.Error(), "did not start") ||
		!strings.Contains(o.err.Error(), "was left enabled") {
		t.Fatalf("err = %v", o.err)
	}

	if strings.Contains(o.out, "Installed.") || index(o.commands, "pm disable-user") >= 0 ||
		f.stockDisabled {
		t.Error("the stock launcher was touched")
	}
}

func TestLostConnectionReportsIncompleteRecovery(t *testing.T) {
	f := &fakeTV{dropAfterHandover: true}
	o := handover(t, f, Options{})
	if o.err == nil {
		t.Fatal("expected an error")
	}

	for _, want := range []string{"stopped answering", "recovery is incomplete"} {
		if !strings.Contains(o.warn, want) {
			t.Errorf("missing %q in %s", want, o.warn)
		}
	}

	if index(o.commands, "pm enable --user 0") < 0 {
		t.Error("no rollback was attempted")
	}

	if !strings.Contains(o.handover, "pm enable --user 0 "+tv.StockLauncher) {
		t.Errorf("an unreachable TV needs the rescue commands on disk: %q", o.handover)
	}
}

func TestHomeThatStaysOnAnotherLauncherIsRolledBack(t *testing.T) {
	f := &fakeTV{setHomeFails: true}
	o := handover(t, f, Options{})
	if o.err == nil || !strings.Contains(o.warn, "owns Home again") ||
		!strings.Contains(o.warn, "setupwraith") {
		t.Fatalf("err %v\n%s", o.err, o.warn)
	}

	if f.stockDisabled || o.handover != "" {
		t.Errorf("stock disabled %v, rescue file %q", f.stockDisabled, o.handover)
	}
}

func TestFailedRollbackIsNotReportedAsRecovered(t *testing.T) {
	f := &fakeTV{setHomeFails: true, enableFails: true}
	o := handover(t, f, Options{})
	if o.err == nil || strings.Contains(o.warn, "owns Home again") ||
		!strings.Contains(o.warn, "recovery is incomplete") {
		t.Fatalf("err %v\n%s", o.err, o.warn)
	}

	if o.handover == "" {
		t.Error("no rescue file")
	}
}

func TestStockLauncherChoice(t *testing.T) {
	cases := []struct {
		name   string
		f      *fakeTV
		opts   Options
		stock  string
		errHas string
	}{
		{"asks the TV", &fakeTV{home: stockHome}, Options{}, tv.StockLauncher, ""},
		{
			"Google TV",
			&fakeTV{home: "com.google.android.apps.tv.launcherx/.Main"},
			Options{},
			"com.google.android.apps.tv.launcherx",
			"",
		},
		{"already ours", &fakeTV{home: tv.LauncherActivity}, Options{}, NoStock, ""},
		{
			"chooser with one stock",
			&fakeTV{home: "android", claims: []string{tv.Launcher, tv.StockLauncher}},
			Options{},
			tv.StockLauncher,
			"",
		},
		{
			"chooser with two stock",
			&fakeTV{home: "android", claims: []string{tv.StockLauncher, "com.mitv.tvhome"}},
			Options{},
			"",
			"2 known stock launchers",
		},
		{
			"setup wizard",
			&fakeTV{home: tv.SetupWizard + "/.Recovery"},
			Options{},
			"",
			"not a stock launcher",
		},
		{
			"named",
			&fakeTV{home: "android"},
			Options{Stock: "com.example.home"},
			"com.example.home",
			"",
		},
		{"injection", &fakeTV{}, Options{Stock: "x; reboot"}, "", "not a package name"},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var out bytes.Buffer
			stock, err := chooseStockLauncher(c.f.device(), c.opts.Stock, &out)
			if c.errHas != "" {
				if err == nil || !strings.Contains(err.Error(), c.errHas) {
					t.Errorf("err = %v", err)
				}
				return
			}
			if err != nil || stock != c.stock {
				t.Errorf("stock = %q, %v", stock, err)
			}
		})
	}
}

func TestServicesAreEnabledOnlyWhenAsked(t *testing.T) {
	o := handover(t, &fakeTV{}, Options{InputKeyService: true, NowPlaying: true})
	if o.err != nil {
		t.Fatal(o.err)
	}

	if index(o.commands, ".InputKeyService") < 0 || index(o.commands, "allow_listener") < 0 {
		t.Error("asked-for services were not enabled")
	}

	if index(o.commands, ".HomeKeyService") >= 0 {
		t.Error("HomeKeyService was not asked for")
	}
}
