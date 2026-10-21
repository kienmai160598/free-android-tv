package debloat

import (
	"bytes"
	"fmt"
	"os"
	"strings"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/tv"
)

func ReadList(path string) ([]string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	return ParseList(path, data)
}

func ParseList(path string, data []byte) ([]string, error) {
	data = bytes.TrimPrefix(data, []byte("\xef\xbb\xbf"))
	var names, bad []string
	for number, line := range strings.Split(string(data), "\n") {
		body, _, _ := strings.Cut(line, "#")
		fields := strings.Fields(body)
		if len(fields) == 0 {
			continue
		}
		if !tv.PackageName.MatchString(fields[0]) {
			bad = append(bad, fmt.Sprintf("  line %d: %s", number+1, fields[0]))
			continue
		}
		names = append(names, fields[0])
	}

	if len(bad) > 0 {
		return nil, fmt.Errorf("refusing %s: not a package name:\n%s",
			path, strings.Join(bad, "\n"))
	}

	if len(names) == 0 {
		return nil, fmt.Errorf("%s: no package names in this list", path)
	}

	return names, nil
}
