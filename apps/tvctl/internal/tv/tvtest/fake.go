package tvtest

import "errors"

var ErrLinkLost = errors.New("adb: connection reset by peer")

type Fake struct {
	Answer   func(command string) (string, error)
	Commands []string
	Pushed   map[string]string
}

func (f *Fake) Shell(command string) (string, error) {
	f.Commands = append(f.Commands, command)
	if f.Answer == nil {
		return "", nil
	}

	return f.Answer(command)
}

func (f *Fake) Push(local, remote string) error {
	if f.Pushed == nil {
		f.Pushed = map[string]string{}
	}

	f.Pushed[remote] = local
	return nil
}
