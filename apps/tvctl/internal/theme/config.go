package theme

import (
	"encoding/json"
	"fmt"
	"math"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/contract"
	"github.com/kienmai160598/free-android-tv/apps/tvctl/internal/tv"
)

var colorValue = regexp.MustCompile(`^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$`)

type configReport struct {
	problems []string
	unknown  []string
}

func checkConfig(data []byte) (configReport, error) {
	if len(data) > contract.ThemeMaxConfigBytes {
		return configReport{}, fmt.Errorf("is %d bytes; the launcher ignores a config over %d", len(data), contract.ThemeMaxConfigBytes)
	}
	var values map[string]any
	if err := json.Unmarshal(data, &values); err != nil {
		return configReport{}, fmt.Errorf("is not a JSON object: %v", err)
	}
	var report configReport
	for _, name := range sortedKeys(values) {
		key, known := contract.ConfigKeys[name]
		if !known {
			report.unknown = append(report.unknown, name)
			continue
		}
		if problem := valueProblem(key, values[name]); problem != "" {
			report.problems = append(report.problems, fmt.Sprintf("%s: %s", name, problem))
		}
	}
	return report, nil
}

func sortedKeys(values map[string]any) []string {
	names := make([]string, 0, len(values))
	for name := range values {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func valueProblem(key contract.ConfigKey, value any) string {
	switch key.Type {
	case contract.TypeBool:
		return unless(isBool(value), "must be true or false")
	case contract.TypeInt:
		return integerProblem(key, value)
	case contract.TypeString:
		return unless(isString(value), "must be a string")
	case contract.TypeColor:
		text, ok := value.(string)
		return unless(ok && colorValue.MatchString(strings.TrimSpace(text)), "must be #RRGGBB or #AARRGGBB")
	case contract.TypeDate:
		text, ok := value.(string)
		_, err := time.Parse("2006-01-02", strings.TrimSpace(text))
		return unless(ok && err == nil, "must be a date, YYYY-MM-DD")
	case contract.TypePackages:
		return unless(isListOf(value, isPackageName), "must be a list of package names")
	case contract.TypeStrings:
		return unless(isListOf(value, isString), "must be a list of strings")
	case contract.TypeKeycodes:
		return unless(isListOf(value, isPositiveInteger), "must be a list of positive keycodes")
	}
	return "has a type tvctl does not know"
}

func integerProblem(key contract.ConfigKey, value any) string {
	number, ok := value.(float64)
	if !ok || number != math.Trunc(number) {
		return "must be a whole number"
	}
	if int(number) < key.Min || int(number) > key.Max {
		return fmt.Sprintf("must be between %d and %d", key.Min, key.Max)
	}
	return ""
}

func unless(ok bool, problem string) string {
	if ok {
		return ""
	}
	return problem
}

func isBool(value any) bool {
	_, ok := value.(bool)
	return ok
}

func isString(value any) bool {
	_, ok := value.(string)
	return ok
}

func isPackageName(value any) bool {
	text, ok := value.(string)
	return ok && tv.PackageName.MatchString(strings.TrimSpace(text))
}

func isPositiveInteger(value any) bool {
	number, ok := value.(float64)
	return ok && number >= 1 && number == math.Trunc(number)
}

func isListOf(value any, valid func(any) bool) bool {
	items, ok := value.([]any)
	if !ok {
		return false
	}
	for _, item := range items {
		if !valid(item) {
			return false
		}
	}
	return true
}
