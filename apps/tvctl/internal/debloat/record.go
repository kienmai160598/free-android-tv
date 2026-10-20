package debloat

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"slices"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

const recordVersion = 1

type Record struct {
	Created  string            `json:"created"`
	Home     *string           `json:"home"`
	Host     string            `json:"host"`
	List     string            `json:"list"`
	Packages map[string]string `json:"packages"`
	Settings map[string]any    `json:"settings"`
	Version  int               `json:"version"`
}

func RecordPath(home, listPath, host string) string {
	hostTag := tv.FileNameSafe(host)
	if host == "" {
		hostTag = "no-host"
	}
	listHash := sha1.Sum([]byte(resolvedPath(listPath)))
	fileName := fmt.Sprintf("recovery-%s-%s.json", hostTag, hex.EncodeToString(listHash[:])[:8])
	return filepath.Join(home, ".cleanyourtv", fileName)
}

func resolvedPath(path string) string {
	absolute, err := filepath.Abs(path)
	if err != nil {
		return path
	}
	if real, err := filepath.EvalSymlinks(absolute); err == nil {
		return real
	}
	return absolute
}

func LoadRecord(path string) (*Record, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, unusableRecord(path, err.Error())
	}
	var record Record
	if err := json.Unmarshal(data, &record); err != nil {
		return nil, unusableRecord(path, err.Error())
	}
	if problem := record.validationProblem(); problem != "" {
		return nil, unusableRecord(path, problem)
	}
	return &record, nil
}

func (r *Record) validationProblem() string {
	if r.Version != recordVersion || r.Packages == nil {
		return "unknown format"
	}
	for name, state := range r.Packages {
		if !tv.PackageName.MatchString(name) {
			return fmt.Sprintf("not a package name: %q", name)
		}
		if !slices.Contains([]string{stateAbsent, stateDisabled, stateEnabled}, state) {
			return fmt.Sprintf("unknown state for %s: %q", name, state)
		}
	}
	if r.Home != nil && !tv.Component.MatchString(*r.Home) {
		return fmt.Sprintf("not a Home component: %q", *r.Home)
	}
	return ""
}

func unusableRecord(path, reason string) error {
	return fmt.Errorf("%s: not a usable recovery record (%s).\n"+
		"It is left where it is: move it aside deliberately, a retry must not replace the state "+
		"recorded before the first change", path, reason)
}

func (r *Record) missing(names []string) []string {
	var missing []string
	for _, name := range names {
		if _, ok := r.Packages[name]; !ok {
			missing = append(missing, name)
		}
	}
	return missing
}

func (r *Record) countState(state string) int {
	count := 0
	for _, recorded := range r.Packages {
		if recorded == state {
			count++
		}
	}
	return count
}

func writeRecord(path string, record *Record) error {
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return writeFileAtomically(path, append(data, '\n'))
}

func writeFileAtomically(path string, data []byte) error {
	temporary := path + ".tmp"
	file, err := os.Create(temporary)
	if err != nil {
		return err
	}
	if _, err := file.Write(data); err != nil {
		file.Close()
		return err
	}
	if err := file.Sync(); err != nil {
		file.Close()
		return err
	}
	if err := file.Close(); err != nil {
		return err
	}
	return os.Rename(temporary, path)
}
