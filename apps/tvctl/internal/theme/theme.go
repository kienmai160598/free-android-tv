package theme

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

const (
	dir              = "/sdcard/Android/data/" + tv.Launcher + "/files"
	remoteConfig     = dir + "/config.json"
	remoteWallpaper  = dir + "/wallpaper.jpg"
	remoteWallpapers = dir + "/wallpapers"
)

var (
	imageExtensions   = []string{".jpg", ".jpeg", ".png", ".webp"}
	safeWallpaperName = regexp.MustCompile(`^[A-Za-z0-9_.-]+$`)
)

const restartHomeKeepingAccessibility = `a=$(settings get secure enabled_accessibility_services); am force-stop ` + tv.Launcher +
	`; am start -n ` + tv.LauncherActivity + ` >/dev/null; case "$a" in *` + tv.Launcher +
	`*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure enabled_accessibility_services "$a"; settings put secure accessibility_enabled 1 ;; esac`

type theme struct {
	folder     string
	config     string
	wallpaper  string
	wallpapers []string
}

func Apply(dev tv.Device, folder string, out io.Writer) error {
	t, err := loadTheme(folder)
	if err != nil {
		return err
	}
	if err := t.push(dev); err != nil {
		return err
	}
	answer, err := dev.Shell(markRevision() + "; " + restartHomeKeepingAccessibility + "; echo applied")
	if err != nil {
		return err
	}
	fmt.Fprintln(out, tv.LastLine(answer))
	fmt.Fprintf(out, "Theme %s is on the TV. Undo with: tvctl theme --reset\n", filepath.Base(filepath.Clean(folder)))
	return nil
}

func Reset(dev tv.Device, out io.Writer) error {
	answer, err := dev.Shell(strings.Join([]string{
		"mkdir -p " + dir,
		"rm -f " + remoteConfig + " " + remoteWallpaper,
		clearWallpapers(),
		markRevision(),
		restartHomeKeepingAccessibility,
		"echo reset",
	}, "; "))
	if err != nil {
		return err
	}
	fmt.Fprintln(out, tv.LastLine(answer))
	return nil
}

func loadTheme(folder string) (*theme, error) {
	t := &theme{folder: folder, config: filepath.Join(folder, "config.json")}
	data, err := os.ReadFile(t.config)
	if err != nil {
		return nil, fmt.Errorf("no config.json in %s", folder)
	}
	if !json.Valid(data) {
		return nil, fmt.Errorf("%s is not valid JSON", t.config)
	}
	if wallpaper := filepath.Join(folder, "wallpaper.jpg"); isFile(wallpaper) {
		t.wallpaper = wallpaper
	}
	t.wallpapers, err = wallpaperFiles(folder)
	return t, err
}

func (t *theme) push(dev tv.Device) error {
	if _, err := dev.Shell("mkdir -p " + dir); err != nil {
		return err
	}
	if err := dev.Push(t.config, remoteConfig); err != nil {
		return err
	}
	if err := t.pushWallpaper(dev); err != nil {
		return err
	}
	if _, err := dev.Shell(clearWallpapers()); err != nil {
		return err
	}
	for _, image := range t.wallpapers {
		if err := dev.Push(image, remoteWallpapers+"/"+filepath.Base(image)); err != nil {
			return err
		}
	}
	return nil
}

func (t *theme) pushWallpaper(dev tv.Device) error {
	if t.wallpaper == "" {
		_, err := dev.Shell("rm -f " + remoteWallpaper)
		return err
	}
	return dev.Push(t.wallpaper, remoteWallpaper)
}

func wallpaperFiles(folder string) ([]string, error) {
	entries, err := os.ReadDir(filepath.Join(folder, "wallpapers"))
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var images []string
	for _, entry := range entries {
		if entry.IsDir() || !slices.Contains(imageExtensions, strings.ToLower(filepath.Ext(entry.Name()))) {
			continue
		}
		if !safeWallpaperName.MatchString(entry.Name()) {
			return nil, fmt.Errorf("unsupported wallpaper filename: %s", entry.Name())
		}
		images = append(images, filepath.Join(folder, "wallpapers", entry.Name()))
	}
	return images, nil
}

func clearWallpapers() string {
	var globs []string
	for _, extension := range imageExtensions {
		globs = append(globs, remoteWallpapers+"/*"+extension, remoteWallpapers+"/*"+strings.ToUpper(extension))
	}
	return "mkdir -p " + remoteWallpapers + "; rm -f " + strings.Join(globs, " ")
}

func markRevision() string {
	random := make([]byte, 16)
	rand.Read(random)
	return fmt.Sprintf("printf '%%s' '%s' > %s/theme_revision", hex.EncodeToString(random), dir)
}

func isFile(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.Mode().IsRegular()
}
