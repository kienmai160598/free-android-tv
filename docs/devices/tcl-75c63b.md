# TCL 75c63b (no tested debloat list)

Firmware: `ro.product.model` Smart TV Pro, device BeyondTV4, Android 11, `V8-R51MT05-LF1V652`,
platform rtd288o. Same generic identifiers as `tcl-65c725` and `tcl-43c645`; do not reuse their lists blindly.

- Network ADB on 5555 worked after accepting the RSA prompt. Saved Wi-Fi owned by `android.uid.system`.
- HOME candidates: launcherx (priority 2), setupwraith `.RecoveryActivity` (1), tv.settings FallbackHome (-1000).
  `set-home-activity` printed Success but Home stayed on launcherx; disabling the RecoveryActivity
  component alone was refused. Disabling the packages `com.google.android.tungsten.setupwraith` and
  `com.google.android.apps.tv.launcherx` handed Home over (both restored with `pm default-state`).
- Live TV: `live_tv_package` must be `com.tcl.tv` (`.TVActivity`); `com.tcl.tvinput` provides the
  tuner/HDMI services but has no launcher activity. Keep both enabled. No `input_action` needed.
- Untested: reboot, standby, dedicated remote keys, casting, multi-day stability.
