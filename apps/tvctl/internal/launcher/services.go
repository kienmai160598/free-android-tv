package launcher

import (
	"fmt"
	"io"
	"strings"

	"github.com/kienmai160598/freeTV/apps/tvctl/internal/contract"
	"github.com/kienmai160598/freeTV/apps/tvctl/internal/tv"
)

const addAccessibilityService = `services=$(settings get secure enabled_accessibility_services); ` +
	`case "$services" in ` +
	`*%[1]s*) ;; ` +
	`null|'') settings put secure enabled_accessibility_services %[2]s ;; ` +
	`*) settings put secure enabled_accessibility_services "$services:%[2]s" ;; ` +
	`esac; ` +
	`settings put secure accessibility_enabled 1; echo %[1]s enabled`

func enableRequestedServices(dev tv.Device, opts Options, out io.Writer) {
	if opts.InputKeyService {
		enableAccessibilityService(dev, contract.InputKeyService, out)
	}

	if opts.HomeKeyService {
		enableAccessibilityService(dev, contract.HomeKeyService, out)
	}

	if opts.NowPlaying {
		allowNowPlayingListener(dev, out)
	}
}

func enableAccessibilityService(dev tv.Device, component string, out io.Writer) {
	serviceName := strings.TrimPrefix(component, ".")
	service := tv.Launcher + "/" + component

	answer, _ := dev.Shell(fmt.Sprintf(addAccessibilityService, serviceName, service))
	fmt.Fprint(out, answer)
}

func allowNowPlayingListener(dev tv.Device, out io.Writer) {
	listener := tv.Launcher + "/" + tv.Launcher + contract.NowPlayingListener

	answer, _ := dev.Shell("cmd notification allow_listener " + listener +
		" && echo NowPlayingListener allowed")
	fmt.Fprint(out, answer)
}
