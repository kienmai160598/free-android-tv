package debloat

import (
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"sort"
	"strings"
	"testing"
)

const debloatLists = "../../../../packages/debloat-lists"

func debloatListFolders(t *testing.T) []string {
	entries, err := os.ReadDir(debloatLists)
	if err != nil {
		t.Fatal(err)
	}

	var folders []string
	for _, entry := range entries {
		if entry.IsDir() {
			folders = append(folders, entry.Name())
		}
	}

	return folders
}

func TestEveryDebloatListIsValid(t *testing.T) {
	for _, folder := range debloatListFolders(t) {
		t.Run(folder, func(t *testing.T) {
			path := filepath.Join(debloatLists, folder, "debloat.txt")
			data, err := os.ReadFile(path)
			if err != nil {
				t.Fatal("no debloat.txt")
			}
			if !strings.HasPrefix(string(data), "#") {
				t.Error("first line should be a comment naming the TV, Android version and build")
			}
			names, err := ParseList(path, data)
			if err != nil {
				t.Fatal(err)
			}
			seen := map[string]bool{}
			for _, name := range names {
				if seen[name] {
					t.Errorf("listed twice: %s", name)
				}
				seen[name] = true
			}
		})
	}
}

func TestDebloatListIndexMatchesFolders(t *testing.T) {
	data, err := os.ReadFile(filepath.Join(debloatLists, "README.md"))
	if err != nil {
		t.Fatal(err)
	}

	lines := strings.Split(string(data), "\n")
	start := slices.IndexFunc(
		lines,
		func(l string) bool { return strings.HasPrefix(l, "| Folder |") },
	)
	if start < 0 {
		t.Fatal("no | Folder | table")
	}

	cell := regexp.MustCompile("^\\|\\s*`([^`/]+)/`\\s*\\|")
	var listed []string
	for _, line := range lines[start+2:] {
		if !strings.HasPrefix(line, "|") {
			break
		}
		m := cell.FindStringSubmatch(line)
		if m == nil {
			t.Fatalf("row does not start with a `folder/` cell: %.60s", line)
		}
		listed = append(listed, m[1])
	}
	sort.Strings(listed)
	if folders := debloatListFolders(t); !slices.Equal(listed, folders) {
		t.Errorf("packages/debloat-lists/README.md rows and its folders differ\n"+
			"rows:    %v\nfolders: %v", listed, folders)
	}
}
