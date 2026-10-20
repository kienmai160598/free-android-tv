package main

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func tvctl(t *testing.T, args ...string) (int, string, string) {
	t.Helper()
	t.Setenv("TV_HOST", "")
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("USERPROFILE", home)
	var out, errOut bytes.Buffer
	code := run(args, strings.NewReader(""), &out, &errOut)
	return code, out.String(), errOut.String()
}

func TestUsageErrors(t *testing.T) {
	for _, args := range [][]string{
		nil,
		{"frobnicate"},
		{"closed", "127.0.0.1", "not-a-port"},
		{"closed", "a", "b", "c"},
		{"push", "one"},
		{"disable"},
	} {
		if code, _, _ := tvctl(t, args...); code != 2 {
			t.Errorf("tvctl %v: exit %d, want 2", args, code)
		}
	}
}

func TestNoHostSaysHowToSetIt(t *testing.T) {
	code, _, errOut := tvctl(t, "probe")
	if code != 1 || !strings.Contains(errOut, "TV_HOST") {
		t.Errorf("exit %d: %s", code, errOut)
	}
}

func TestBadListIsRefusedBeforeConnecting(t *testing.T) {
	list := filepath.Join(t.TempDir(), "debloat.txt")
	os.WriteFile(list, []byte("com.example.evil;reboot\n"), 0o644)

	code, _, errOut := tvctl(t, "disable", list)
	if code != 1 || !strings.Contains(errOut, "not a package name") {
		t.Errorf("exit %d: %s", code, errOut)
	}
}

func TestRestoreDryRunNeedsNoTV(t *testing.T) {
	list := filepath.Join(t.TempDir(), "debloat.txt")
	os.WriteFile(list, []byte("# TV\ncom.example.one\n"), 0o644)
	code, out, errOut := tvctl(t, "restore", "--dry-run", list)
	if code != 0 || !strings.HasPrefix(out, "pm enable com.example.one\n") ||
		!strings.Contains(errOut, "no recovery record") {
		t.Errorf("exit %d\n%s\n%s", code, out, errOut)
	}
}
