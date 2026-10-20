package theme

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestValidConfigHasNoProblems(t *testing.T) {
	report, err := checkConfig([]byte(`{
		"dock_apps": ["com.netflix.ninja", "com.google.android.youtube.tv"],
		"columns": 6, "rows_below_dock": 0, "animations": false,
		"background_color": "#6750A4", "festival_until": "2026-10-21",
		"input_key_keycodes": [170, 178], "app_keys": ["190:com.google.android.youtube.tv"]
	}`))
	if err != nil || len(report.problems) != 0 || len(report.unknown) != 0 {
		t.Errorf("report %+v, err %v", report, err)
	}
}

func TestInvalidValuesAreReported(t *testing.T) {
	report, err := checkConfig([]byte(`{
		"columns": 9, "sky_intensity": 12.5, "animations": "yes", "background_color": "purple",
		"festival_until": "21/10/2026", "dock_apps": ["netflix"], "input_key_keycodes": [0],
		"app_keys": [190], "greeting": 3
	}`))
	if err != nil {
		t.Fatal(err)
	}

	invalidKeys := []string{
		"columns", "sky_intensity", "animations", "background_color", "festival_until",
		"dock_apps", "input_key_keycodes", "app_keys", "greeting",
	}

	for _, key := range invalidKeys {
		if !strings.Contains(strings.Join(report.problems, "\n"), key+":") {
			t.Errorf("%s not reported in %v", key, report.problems)
		}
	}
}

func TestUnknownKeysAreOnlyReported(t *testing.T) {
	report, err := checkConfig([]byte(`{"colums": 6, "columns": 6}`))
	if err != nil || len(report.problems) != 0 || len(report.unknown) != 1 ||
		report.unknown[0] != "colums" {
		t.Errorf("report %+v, err %v", report, err)
	}
}

func TestConfigThatIsNotAnObjectOrTooLargeIsRefused(t *testing.T) {
	for name, data := range map[string][]byte{
		"array":     []byte(`[1, 2]`),
		"broken":    []byte(`{"columns": `),
		"too large": append([]byte(`{"greeting": "`+strings.Repeat("x", 70000)), `"}`...),
	} {
		if _, err := checkConfig(data); err == nil {
			t.Errorf("%s: expected an error", name)
		}
	}
}

func TestApplyRefusesAThemeTheLauncherWouldIgnore(t *testing.T) {
	folder := filepath.Join(t.TempDir(), "broken")
	os.MkdirAll(folder, 0o755)
	os.WriteFile(filepath.Join(folder, "config.json"), []byte(`{"columns": 12}`), 0o644)
	if err := Apply(nil, folder, &bytes.Buffer{}); err == nil ||
		!strings.Contains(err.Error(), "between 1 and 8") {
		t.Errorf("err = %v", err)
	}
}
