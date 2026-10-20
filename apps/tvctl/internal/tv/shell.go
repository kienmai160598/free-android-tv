package tv

import (
	"fmt"
	"io"
	"regexp"
	"strings"
)

func Quote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}

func Batch(dev Device, commands []string, out io.Writer) error {
	for _, command := range commands {
		answer, err := dev.Shell(command)
		fmt.Fprintf(out, "$ %s\n%s", command, answer)
		if answer != "" && !strings.HasSuffix(answer, "\n") {
			fmt.Fprintln(out)
		}
		if err != nil {
			return err
		}
	}

	return nil
}

var unsafeFileNameRune = regexp.MustCompile(`[^A-Za-z0-9._-]`)

func FileNameSafe(s string) string {
	return unsafeFileNameRune.ReplaceAllString(s, "_")
}
