package theme

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/kienmai160598/free-android-tv/internal/tv/tvtest"
)

func writeTheme(t *testing.T, files map[string]string) string {
	t.Helper()
	folder := filepath.Join(t.TempDir(), "aurora")
	for name, content := range files {
		path := filepath.Join(folder, name)
		os.MkdirAll(filepath.Dir(path), 0o755)
		if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	return folder
}

func TestApplyPushesTheThemeAndRestartsHome(t *testing.T) {
	folder := writeTheme(t, map[string]string{
		"config.json":          `{"background_color": "#6750A4"}`,
		"wallpaper.jpg":        "jpg",
		"wallpapers/one.png":   "png",
		"wallpapers/notes.txt": "not an image",
		"wallpapers/two.WEBP":  "webp",
	})
	dev := &tvtest.Fake{Answer: func(string) (string, error) { return "applied\n", nil }}
	var out bytes.Buffer
	if err := Apply(dev, folder, &out); err != nil {
		t.Fatal(err)
	}
	for _, remote := range []string{dir + "/config.json", remoteWallpaper, dir + "/wallpapers/one.png", dir + "/wallpapers/two.WEBP"} {
		if _, ok := dev.Pushed[remote]; !ok {
			t.Errorf("%s was not pushed", remote)
		}
	}
	if len(dev.Pushed) != 4 {
		t.Errorf("pushed %v", dev.Pushed)
	}
	last := dev.Commands[len(dev.Commands)-1]
	if !strings.Contains(last, "theme_revision") || !strings.Contains(last, "am force-stop") {
		t.Errorf("last command %q", last)
	}
	if !strings.Contains(out.String(), "Theme aurora is on the TV") {
		t.Errorf("output %q", out.String())
	}
}

func TestApplyRemovesAWallpaperTheThemeDoesNotHave(t *testing.T) {
	dev := &tvtest.Fake{}
	if err := Apply(dev, writeTheme(t, map[string]string{"config.json": "{}"}), &bytes.Buffer{}); err != nil {
		t.Fatal(err)
	}
	found := false
	for _, command := range dev.Commands {
		found = found || command == "rm -f "+remoteWallpaper
	}
	if !found {
		t.Errorf("commands %v", dev.Commands)
	}
}

func TestBadThemesReachNothing(t *testing.T) {
	cases := map[string]map[string]string{
		"no config":    {"wallpaper.jpg": "jpg"},
		"invalid json": {"config.json": "{nope"},
		"unsafe name":  {"config.json": "{}", "wallpapers/a b;reboot.jpg": "jpg"},
	}
	for name, files := range cases {
		t.Run(name, func(t *testing.T) {
			dev := &tvtest.Fake{}
			if err := Apply(dev, writeTheme(t, files), &bytes.Buffer{}); err == nil {
				t.Fatal("expected an error")
			}
			if len(dev.Commands) != 0 || len(dev.Pushed) != 0 {
				t.Errorf("reached the TV: %v %v", dev.Commands, dev.Pushed)
			}
		})
	}
}

func TestShippedThemesAreValid(t *testing.T) {
	folders, _ := filepath.Glob("../../themes/*/config.json")
	if len(folders) == 0 {
		t.Fatal("no themes found")
	}
	for _, config := range folders {
		folder := filepath.Dir(config)
		if _, err := wallpaperFiles(folder); err != nil {
			t.Errorf("%s: %v", folder, err)
		}
		if err := Apply(&tvtest.Fake{}, folder, &bytes.Buffer{}); err != nil {
			t.Errorf("%s: %v", folder, err)
		}
	}
}
