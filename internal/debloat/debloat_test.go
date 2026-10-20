package debloat

import (
	"bytes"
	"encoding/json"
	"errors"
	"maps"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"

	"github.com/kienmai160598/free-android-tv/internal/tv"
	"github.com/kienmai160598/free-android-tv/internal/tv/tvtest"
)

const (
	stock     = tv.StockLauncher
	stockHome = stock + "/.MainActivity"
	custom    = tv.Launcher
	host      = "192.168.1.50"
)

type fakeTV struct {
	installed   []string
	disabled    []string
	home        string
	homeFails   bool
	claims      []string
	claimsFail  bool
	wifi        string
	wifiFails   bool
	adbSecure   string
	secureFails bool
	settings    map[string]string
	unreachable bool
}

func newTV() *fakeTV {
	return &fakeTV{
		installed: []string{"com.example.enabled", "com.example.already_off", stock},
		disabled:  []string{"com.example.already_off"},
		home:      stockHome,
		claims:    []string{stock},
		wifi:      " cuid=1000 cname=android.uid.system:1000 allowAutojoin=true",
		adbSecure: "1",
		settings: map[string]string{
			"window_animation_scale":     "0.5",
			"transition_animation_scale": "null",
			"animator_duration_scale":    "1.0",
		},
	}
}

func packageLines(names []string) string {
	var b strings.Builder
	for _, name := range names {
		b.WriteString("package:" + name + "\n")
	}
	return b.String()
}

func (f *fakeTV) device() *tvtest.Fake {
	return &tvtest.Fake{Answer: func(command string) (string, error) {
		if f.unreachable {
			return "", tvtest.ErrLinkLost
		}
		switch {
		case command == "pm list packages":
			return packageLines(f.installed), nil
		case command == "pm list packages -d":
			return packageLines(f.disabled), nil
		case strings.HasPrefix(command, "cmd package query-activities"):
			if f.claimsFail {
				return "", errors.New("query failed")
			}
			var b strings.Builder
			for _, name := range f.claims {
				b.WriteString("  " + name + "/.MainActivity\n")
			}
			return b.String(), nil
		case strings.HasPrefix(command, "cmd package resolve-activity"):
			if f.homeFails {
				return "", errors.New("query failed")
			}
			return f.home + "\n", nil
		case strings.HasPrefix(command, "settings get global "):
			value, ok := f.settings[strings.TrimPrefix(command, "settings get global ")]
			if !ok {
				value = "null"
			}
			return value + "\n", nil
		case command == "dumpsys wifi":
			if f.wifiFails {
				return "partial output", errors.New("dumpsys stopped")
			}
			return f.wifi + "\n", nil
		case command == "getprop ro.adb.secure":
			if f.secureFails {
				return "0\n", errors.New("getprop failed")
			}
			return f.adbSecure + "\r\n", nil
		}
		return "", nil
	}}
}

type world struct {
	t      *testing.T
	home   string
	list   string
	record string
}

func newWorld(t *testing.T, names ...string) *world {
	dir := t.TempDir()
	w := &world{t: t, home: filepath.Join(dir, "home"), list: filepath.Join(dir, "debloat.txt")}
	if len(names) == 0 {
		names = []string{"com.example.enabled", "com.example.already_off", "com.example.absent"}
	}
	w.writeList(names...)
	w.record = RecordPath(w.home, w.list, host)
	return w
}

func (w *world) writeList(names ...string) {
	text := "# My TV\n" + strings.Join(names, "\n") + "\n"
	if err := os.WriteFile(w.list, []byte(text), 0o644); err != nil {
		w.t.Fatal(err)
	}
}

type result struct {
	err  error
	out  string
	warn string
	sent []string
}

func (w *world) disable(f *fakeTV, opts DisableOptions) result {
	dev := f.device()
	var out, warn bytes.Buffer
	err := Disable(dev, w.record, w.list, host, opts, &out, &warn)
	return result{err, out.String(), warn.String(), mutations(dev.Commands)}
}

func (w *world) restore(f *fakeTV) result {
	dev := f.device()
	var out, warn bytes.Buffer
	commands, err := RestoreCommands(w.record, w.list, "", &warn)
	if err == nil {
		err = tv.Batch(dev, commands, &out)
	}
	return result{err, out.String(), warn.String(), dev.Commands}
}

func mutations(commands []string) []string {
	var sent []string
	for _, command := range commands {
		if strings.HasPrefix(command, "pm disable-user") || strings.HasPrefix(command, "pm enable") ||
			command == "pm list packages -d" && len(sent) > 0 || strings.HasPrefix(command, "settings put") {
			sent = append(sent, command)
		}
	}

	return sent
}

func (w *world) loadRecord() *Record {
	data, err := os.ReadFile(w.record)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		w.t.Fatal(err)
	}
	var record Record
	if err := json.Unmarshal(data, &record); err != nil {
		w.t.Fatal(err)
	}
	return &record
}

func must(t *testing.T, r result) {
	t.Helper()
	if r.err != nil {
		t.Fatalf("unexpected error: %v\n%s%s", r.err, r.out, r.warn)
	}
}

func TestSaveRecordsTheStateUndoNeeds(t *testing.T) {
	w := newWorld(t)
	r := w.disable(newTV(), DisableOptions{})
	must(t, r)
	record := w.loadRecord()
	want := map[string]string{
		"com.example.enabled":     "enabled",
		"com.example.already_off": "disabled",
		"com.example.absent":      "absent",

		stock: "enabled",
	}
	if !maps.Equal(record.Packages, want) {
		t.Errorf("packages = %v", record.Packages)
	}
	if record.Home == nil || *record.Home != stockHome {
		t.Errorf("home = %v", record.Home)
	}
	if record.Settings["window_animation_scale"] != "0.5" {
		t.Errorf("settings = %v", record.Settings)
	}
	wantSent := []string{
		"pm disable-user --user 0 com.example.enabled",
		"pm disable-user --user 0 com.example.already_off",
		"pm disable-user --user 0 com.example.absent",
	}
	if !slices.Equal(r.sent[:3], wantSent) {
		t.Errorf("sent %v", r.sent)
	}
}

func TestRetryKeepsTheOriginalRecord(t *testing.T) {
	w := newWorld(t)
	must(t, w.disable(newTV(), DisableOptions{}))
	first := w.loadRecord()

	f := newTV()
	f.installed = []string{"com.example.already_off", stock}
	f.disabled = []string{"com.example.enabled", "com.example.already_off"}
	r := w.disable(f, DisableOptions{})
	must(t, r)
	if !strings.Contains(r.out, "keeping the recovery record") {
		t.Errorf("output: %s", r.out)
	}
	if !maps.Equal(w.loadRecord().Packages, first.Packages) {
		t.Error("the record changed on a retry")
	}
}

func TestFailedSaveDisablesNothing(t *testing.T) {
	w := newWorld(t)
	f := newTV()
	f.unreachable = true
	r := w.disable(f, DisableOptions{})
	if r.err == nil {
		t.Fatal("expected an error")
	}
	if len(r.sent) != 0 {
		t.Errorf("a failed record must not be followed by a mutation: %v", r.sent)
	}
	if w.loadRecord() != nil {
		t.Error("a record was written")
	}
}

func TestCorruptRecordIsNotOverwritten(t *testing.T) {
	w := newWorld(t)
	must(t, w.disable(newTV(), DisableOptions{}))
	os.WriteFile(w.record, []byte("{not json"), 0o644)
	r := w.disable(newTV(), DisableOptions{})
	if r.err == nil || !strings.Contains(r.err.Error(), "not a usable recovery record") {
		t.Fatalf("error = %v", r.err)
	}
	if data, _ := os.ReadFile(w.record); string(data) != "{not json" {
		t.Error("the original file is left for inspection")
	}
	if len(r.sent) != 0 {
		t.Errorf("nothing was to be disabled: %v", r.sent)
	}
}

func TestRestoreReplaysRecordedStates(t *testing.T) {
	w := newWorld(t)
	f := newTV()
	f.settings["animator_duration_scale"] = "3.0"
	must(t, w.disable(f, DisableOptions{}))
	r := w.restore(f)
	must(t, r)
	for _, want := range []string{
		"pm enable com.example.enabled",

		"cmd package set-home-activity --user 0 " + stockHome,
		"settings put global window_animation_scale 0.5",

		"settings delete global transition_animation_scale",
		"settings put global animator_duration_scale 3.0",
	} {
		if !slices.Contains(r.sent, want) {
			t.Errorf("missing %q in %v", want, r.sent)
		}
	}
	for _, unwanted := range []string{"pm enable com.example.already_off", "pm enable com.example.absent"} {
		if slices.Contains(r.sent, unwanted) {
			t.Errorf("sent %q: a package already disabled or absent stays as it was", unwanted)
		}
	}
	if r.sent[len(r.sent)-1] != "pm list packages -d" {
		t.Errorf("last command %q", r.sent[len(r.sent)-1])
	}
	if !strings.Contains(r.warn, "leaving com.example.already_off alone") {
		t.Errorf("warnings: %s", r.warn)
	}
}

func TestValidRecordRestoresTheSameBatch(t *testing.T) {
	w := newWorld(t)
	f := newTV()
	must(t, w.disable(f, DisableOptions{}))
	r := w.restore(f)
	must(t, r)
	want := []string{
		"pm enable com.example.enabled",
		"pm enable " + stock,
		"cmd package set-home-activity --user 0 " + stockHome,
		"settings put global window_animation_scale 0.5",
		"settings delete global transition_animation_scale",
		"settings put global animator_duration_scale 1.0",
		"pm list packages -d",
	}
	if !slices.Equal(r.sent, want) {
		t.Errorf("batch\n got %q\nwant %q", r.sent, want)
	}
}

func TestRestoreWithoutARecordSaysSoAndFallsBack(t *testing.T) {
	w := newWorld(t)
	r := w.restore(newTV())
	must(t, r)
	if !strings.Contains(r.warn, "no recovery record") {
		t.Errorf("warnings: %s", r.warn)
	}
	want := []string{"pm enable com.example.enabled", "pm enable com.example.already_off", "pm enable com.example.absent"}
	if !slices.Equal(r.sent[:3], want) {
		t.Errorf("sent %v", r.sent)
	}
	for _, command := range []string{"pm enable " + stock, "settings put global window_animation_scale 1.0"} {
		if !slices.Contains(r.sent, command) {
			t.Errorf("missing %q", command)
		}
	}
}

func TestEditedListLeavesNewNamesAlone(t *testing.T) {
	w := newWorld(t)
	must(t, w.disable(newTV(), DisableOptions{}))
	w.writeList("com.example.enabled", "com.example.added_later")
	r := w.restore(newTV())
	must(t, r)
	if slices.Contains(r.sent, "pm enable com.example.added_later") {
		t.Error("a name the record never saw was enabled")
	}
	if !strings.Contains(r.warn, "not in the record, leaving alone: com.example.added_later") {
		t.Errorf("warnings: %s", r.warn)
	}
}

func TestRerunWithEditedListRecordsTheNewNames(t *testing.T) {
	w := newWorld(t)
	must(t, w.disable(newTV(), DisableOptions{}))
	first := w.loadRecord()
	w.writeList("com.example.enabled", "com.example.already_off", "com.example.absent", "com.example.added_later")
	f := newTV()
	f.installed = []string{"com.example.enabled", "com.example.already_off", "com.example.added_later", stock}
	f.disabled = []string{"com.example.enabled", "com.example.already_off"}
	must(t, w.disable(f, DisableOptions{}))
	record := w.loadRecord()
	if record.Packages["com.example.added_later"] != "enabled" {
		t.Errorf("added_later = %q", record.Packages["com.example.added_later"])
	}

	for name, state := range first.Packages {
		if record.Packages[name] != state {
			t.Errorf("%s changed from %s to %s", name, state, record.Packages[name])
		}
	}
	if record.Created != first.Created {
		t.Error("created changed")
	}
	r := w.restore(f)
	must(t, r)
	if !slices.Contains(r.sent, "pm enable com.example.added_later") {
		t.Error("a name added to the list later is not left disabled forever")
	}
	if slices.Contains(r.sent, "pm enable com.example.already_off") {
		t.Error("already_off was enabled")
	}
}

func TestCustomLauncherHomeRestoresTheStockLauncher(t *testing.T) {
	w := newWorld(t)
	f := newTV()
	f.installed = append(f.installed, custom)
	f.home = tv.LauncherActivity
	must(t, w.disable(f, DisableOptions{}))
	if home := w.loadRecord().Home; home == nil || *home != tv.LauncherActivity {
		t.Fatalf("home = %v", home)
	}
	r := w.restore(f)
	must(t, r)
	var homes []string
	for _, command := range r.sent {
		if strings.HasPrefix(command, "cmd package set-home-activity") {
			homes = append(homes, command)
		}
	}
	if len(homes) != 1 || !strings.Contains(homes[0], "resolve-activity") || !strings.Contains(homes[0], stock) {
		t.Fatalf("home commands %v", homes)
	}
	enable := slices.Index(r.sent, "pm enable "+stock)
	if enable < 0 || enable > slices.Index(r.sent, homes[0]) {
		t.Error("the stock launcher is enabled before it is made Home")
	}
}

func TestStockLauncherDisabledBeforeCleanupStaysDisabled(t *testing.T) {
	w := newWorld(t, "com.example.enabled", stock)
	f := newTV()
	f.installed = []string{"com.example.enabled", stock, custom}
	f.disabled = []string{stock}
	f.home = tv.LauncherActivity
	f.claims = []string{custom}
	must(t, w.disable(f, DisableOptions{}))
	if w.loadRecord().Packages[stock] != "disabled" {
		t.Fatal("the stock launcher should be recorded as disabled")
	}
	r := w.restore(f)
	must(t, r)
	if slices.Contains(r.sent, "pm enable "+stock) {
		t.Error("enabled a launcher the owner had disabled")
	}
}

func TestStockLauncherEnabledOnceWhenItOwnedHome(t *testing.T) {
	w := newWorld(t)
	f := newTV()
	must(t, w.disable(f, DisableOptions{}))
	r := w.restore(f)
	must(t, r)
	count := 0
	for _, command := range r.sent {
		if command == "pm enable "+stock {
			count++
		}
	}
	if count != 1 {
		t.Errorf("enabled %d times", count)
	}
}

func TestEditedRecordIsRefusedAndLeftAlone(t *testing.T) {
	edits := map[string]func(map[string]any){
		"package name": func(r map[string]any) {
			r["packages"].(map[string]any)["com.example.x; settings put global adb_enabled 0 #"] = "enabled"
		},
		"package state": func(r map[string]any) { r["packages"].(map[string]any)["com.example.enabled"] = "on" },
		"home":          func(r map[string]any) { r["home"] = stockHome + "; settings put global adb_enabled 0" },
		"settings":      func(r map[string]any) { r["settings"] = []string{"window_animation_scale", "0.5"} },
	}
	for field, edit := range edits {
		for _, operation := range []string{"restore", "disable"} {
			t.Run(field+"/"+operation, func(t *testing.T) {
				w := newWorld(t)
				must(t, w.disable(newTV(), DisableOptions{}))
				var record map[string]any
				data, _ := os.ReadFile(w.record)
				json.Unmarshal(data, &record)
				edit(record)
				edited, _ := json.Marshal(record)
				os.WriteFile(w.record, edited, 0o644)

				var r result
				if operation == "restore" {
					r = w.restore(newTV())
				} else {
					r = w.disable(newTV(), DisableOptions{})
				}
				if r.err == nil || !strings.Contains(r.err.Error(), "not a usable recovery record") ||
					!strings.Contains(r.err.Error(), w.record) {
					t.Fatalf("error = %v", r.err)
				}
				if len(mutations(r.sent)) != 0 {
					t.Errorf("reached the TV: %v", r.sent)
				}
				if after, _ := os.ReadFile(w.record); !bytes.Equal(after, edited) {
					t.Error("the file is left for inspection")
				}
			})
		}
	}
}

func TestRecordPathMatchesThePythonTools(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("the recorded path is a Unix path")
	}
	got := filepath.Base(RecordPath("/home", "/home/alice/cleanyourtv/examples/tcl-55c815/debloat.txt", "192.168.1.50"))
	if got != "recovery-192.168.1.50-92b9e625.json" {
		t.Errorf("got %s", got)
	}
	if got := filepath.Base(RecordPath("/home", "/x/l.txt", "")); !strings.HasPrefix(got, "recovery-no-host-") {
		t.Errorf("got %s", got)
	}
}

func TestListThatIsNotPackageNamesIsRefused(t *testing.T) {
	w := newWorld(t)
	os.WriteFile(w.list, []byte("com.example.good\ncom.example.evil;settings put global adb_enabled 0\n"), 0o644)
	for _, operation := range []string{"disable", "restore"} {
		var r result
		if operation == "disable" {
			r = w.disable(newTV(), DisableOptions{})
		} else {
			r = w.restore(newTV())
		}
		if r.err == nil || !strings.Contains(r.err.Error(), "not a package name") ||
			!strings.Contains(r.err.Error(), "com.example.evil;settings") {
			t.Errorf("%s: error = %v", operation, r.err)
		}
		if len(r.sent) != 0 {
			t.Errorf("%s sent %v", operation, r.sent)
		}
	}
}

func TestWindowsBOMAndCRLFAreNormalised(t *testing.T) {
	names, err := ParseList("l", []byte("\xef\xbb\xbf# My TV, saved in Notepad\r\ncom.example.one\r\ncom.example.two   # a note\r\n\r\n"))
	if err != nil || !slices.Equal(names, []string{"com.example.one", "com.example.two"}) {
		t.Errorf("got %q, %v", names, err)
	}
}

func TestEmptyListIsRefused(t *testing.T) {
	if _, err := ParseList("l", []byte("# only a comment\n\n")); err == nil {
		t.Error("expected an error")
	}
}

func homeWorld(t *testing.T) *world {
	return newWorld(t, "com.example.before", stock, "com.example.after")
}

func homeTV() *fakeTV {
	f := newTV()
	f.installed = []string{"com.example.before", "com.example.after", stock}
	f.disabled = nil
	return f
}

func TestTheOwnerOfHomeIsSkippedWhenItIsTheOnlyHome(t *testing.T) {
	r := homeWorld(t).disable(homeTV(), DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("the only Home the TV has must not be disabled")
	}
	want := []string{"pm disable-user --user 0 com.example.before", "pm disable-user --user 0 com.example.after"}
	if !slices.Equal(r.sent[:2], want) {
		t.Errorf("sent %v", r.sent)
	}
	if !strings.Contains(r.warn, "Skipping "+stock) {
		t.Errorf("warnings: %s", r.warn)
	}
}

func TestASecondHomeLetsTheListDisableTheOwner(t *testing.T) {
	f := homeTV()
	f.installed = append(f.installed, custom)
	f.home = tv.LauncherActivity
	f.claims = []string{custom, stock}
	r := homeWorld(t).disable(f, DisableOptions{})
	must(t, r)
	if !slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("the stock launcher is fair game once the new one is Home")
	}
	if strings.Contains(r.warn, "Skipping") {
		t.Errorf("warnings: %s", r.warn)
	}
}

func TestAnotherHomeThatStaysEnabledLetsTheOwnerGo(t *testing.T) {
	f := homeTV()
	f.installed = append(f.installed, custom)
	f.claims = []string{stock, custom}
	r := homeWorld(t).disable(f, DisableOptions{})
	must(t, r)
	if !slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("the custom launcher stays to answer Home")
	}
}

func TestAListThatDisablesEveryHomeKeepsTheOwner(t *testing.T) {
	w := newWorld(t, "com.example.before", stock, custom)
	f := homeTV()
	f.installed = []string{"com.example.before", stock, custom}
	f.claims = []string{stock, custom}
	r := w.disable(f, DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("the app that owns Home stays enabled")
	}
	if !slices.Contains(r.sent, "pm disable-user --user 0 "+custom) {
		t.Error("the other launcher listed is still disabled")
	}
}

func TestTheSetupWizardDoesNotCountAsAnotherHome(t *testing.T) {
	f := homeTV()
	f.installed = append(f.installed, tv.SetupWizard, "com.android.tv.settings")
	f.claims = []string{stock, tv.SetupWizard, "com.android.tv.settings"}
	r := homeWorld(t).disable(f, DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("the setup wizard is not a launcher to fall back on")
	}
	if !strings.Contains(r.warn, "Skipping "+stock) {
		t.Errorf("warnings: %s", r.warn)
	}
}

func TestATVThatCannotListItsHomesIsLeftWithOne(t *testing.T) {
	f := homeTV()
	f.claimsFail = true
	r := homeWorld(t).disable(f, DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("disabled the owner without knowing what else answers Home")
	}
}

func TestForceDisablesTheOwner(t *testing.T) {
	r := homeWorld(t).disable(homeTV(), DisableOptions{ForceHome: true})
	must(t, r)
	if !slices.Contains(r.sent, "pm disable-user --user 0 "+stock) {
		t.Error("--force-home did not disable the owner")
	}
}

func TestTheSkippedLauncherStaysInTheRecoveryRecord(t *testing.T) {
	w := homeWorld(t)
	must(t, w.disable(homeTV(), DisableOptions{}))
	if state := w.loadRecord().Packages[stock]; state != "enabled" {
		t.Errorf("state = %q", state)
	}
}

func wizardWorld(t *testing.T) *world {
	return newWorld(t, "com.example.before", tv.SetupWizard, "com.example.after")
}

func wizardTV(owner string) *fakeTV {
	f := newTV()
	f.wifi = " cuid=10031 cname=" + owner + " allowAutojoin=true"
	return f
}

func TestSetupWizardSkippedWhenItOwnsWifi(t *testing.T) {
	r := wizardWorld(t).disable(wizardTV(tv.SetupWizard+":10031"), DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+tv.SetupWizard) {
		t.Error("disabling it would delete the Wi-Fi network the TV is on")
	}
	want := []string{"pm disable-user --user 0 com.example.before", "pm disable-user --user 0 com.example.after"}
	if !slices.Equal(r.sent[:2], want) || !strings.Contains(r.warn, "Skipping") {
		t.Errorf("sent %v\n%s", r.sent, r.warn)
	}
}

func TestSetupWizardDisabledWhenSystemOwnsWifi(t *testing.T) {
	r := wizardWorld(t).disable(wizardTV("android.uid.system:1000"), DisableOptions{})
	must(t, r)
	want := []string{
		"pm disable-user --user 0 com.example.before",
		"pm disable-user --user 0 " + tv.SetupWizard,
		"pm disable-user --user 0 com.example.after",
	}
	if !slices.Equal(r.sent[:3], want) {
		t.Errorf("sent %v", r.sent)
	}
}

func TestSetupWizardSkippedWhenDumpsysFails(t *testing.T) {
	f := wizardTV("android.uid.system:1000")
	f.wifiFails = true
	r := wizardWorld(t).disable(f, DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+tv.SetupWizard) || !strings.Contains(r.warn, "could not check") {
		t.Errorf("sent %v\n%s", r.sent, r.warn)
	}
}

func TestSetupWizardSkippedWhenNoNetworkIsListed(t *testing.T) {
	f := newTV()
	f.wifi = "Wi-Fi is off"
	r := wizardWorld(t).disable(f, DisableOptions{})
	must(t, r)
	if slices.Contains(r.sent, "pm disable-user --user 0 "+tv.SetupWizard) || !strings.Contains(r.warn, "could not check") {
		t.Errorf("sent %v\n%s", r.sent, r.warn)
	}
}

func TestForceSetupWizard(t *testing.T) {
	r := wizardWorld(t).disable(wizardTV(tv.SetupWizard+":10031"), DisableOptions{ForceSetupWizard: true})
	must(t, r)
	if !slices.Contains(r.sent, "pm disable-user --user 0 "+tv.SetupWizard) {
		t.Error("--force-setupwraith did not disable it")
	}
}

func TestADBSecureWarning(t *testing.T) {
	cases := []struct {
		name  string
		setup func(*fakeTV)
		warns bool
	}{
		{"not secure", func(f *fakeTV) { f.adbSecure = "0" }, true},
		{"secure", func(f *fakeTV) { f.adbSecure = "1" }, false},
		{"missing", func(f *fakeTV) { f.adbSecure = "" }, false},

		{"read fails", func(f *fakeTV) { f.secureFails = true }, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			w := newWorld(t, "com.example.one", "com.example.two")
			f := newTV()
			c.setup(f)
			r := w.disable(f, DisableOptions{})
			must(t, r)
			if got := strings.Contains(r.warn, "ro.adb.secure=0"); got != c.warns {
				t.Errorf("warned = %v\n%s", got, r.warn)
			}
			if c.warns && !strings.Contains(r.warn, "When you are done: close ADB") {
				t.Error("the note points at the README answer")
			}

			want := []string{"pm disable-user --user 0 com.example.one", "pm disable-user --user 0 com.example.two", "pm list packages -d"}
			if !slices.Equal(r.sent, want) {
				t.Errorf("sent %v", r.sent)
			}
		})
	}
}

func TestAMovedListDoesNotFallBackWhenTheTVHasARecord(t *testing.T) {
	w := newWorld(t)
	must(t, w.disable(newTV(), DisableOptions{}))
	moved := filepath.Join(t.TempDir(), "debloat.txt")
	data, _ := os.ReadFile(w.list)
	os.WriteFile(moved, data, 0o644)
	var warn bytes.Buffer
	_, err := RestoreCommands(RecordPath(w.home, moved, host), moved, "", &warn)
	if err == nil || !strings.Contains(err.Error(), "--record") || !strings.Contains(err.Error(), w.record) {
		t.Fatalf("error = %v", err)
	}

	commands, err := RestoreCommands(w.record, moved, "", &warn)
	if err != nil || slices.Contains(commands, "pm enable com.example.already_off") {
		t.Errorf("commands %v, %v", commands, err)
	}
}
