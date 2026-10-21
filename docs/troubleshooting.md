# Troubleshooting

## Connecting

- macOS blocks the Google `adb` binary ("No route to host") under Local Network privacy; `tvctl` is not blocked.
- Some TVs have no Wireless debugging toggle: USB debugging alone opens port 5555 (Xiaomi).
- Some Google TV sets never open 5555 from USB debugging: pair once (`adb pair <ip>:<port> <code>`,
  `adb connect`, `adb tcpip 5555`); the pairing port shows in `dns-sd -B _adb-tls-pairing._tcp`.
  `adb tcpip` lasts until reboot; Wireless debugging turns off whenever Wi-Fi drops.
- ISP boxes with both debugging switches removed or greyed out cannot be reached at all.
- TV on another VLAN: a temporary firewall rule for tcp/5555 only, removed afterwards.
- Unaccepted keys queue one "Allow USB debugging?" prompt each; dismiss them before key injection.
- Mi Box S cuts USB power to peripherals while USB debugging is on.
- Git Bash rewrites `/data/...` paths; use `scripts/tvctl.ps1` or `MSYS_NO_PATHCONV=1`.

## Working on the TV

- Chained `pm` commands may stop after the first; run one per shell call (`tvctl batch` does).
- Some MediaTek Xiaomi `screencap` print a line before the PNG; save to `/data/local/tmp/s.png` and pull.
- Play Protect blocks apps targeting old SDKs (`INSTALL_FAILED_VERIFICATION_FAILURE`); target 34.
- Some China-market TCL sets refuse every sideload or any HOME app not signed by them: debloat only.
- `set-home-activity` can print `Success` and change nothing; always re-query Home.
- Check which packages really have a HOME activity before disabling by name
  (`cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`).

## Google TV setup wizard (`com.google.android.tungsten.setupwraith`)

- Its `.RecoveryActivity` answers HOME (priority 1); once the stock launcher is gone it takes Home and
  system_server crash-loops (`logcat -b crash`: `FATAL EXCEPTION IN SYSTEM PROCESS: android.display`).
  Disabling the component alone is refused; disable the package. Escape a loop with
  `pm enable --user 0 com.google.android.apps.tv.launcherx`.
- Disabling it deletes the Wi-Fi network saved during first setup and the TV goes offline for good.
  Check `dumpsys wifi | grep -o 'cname=[^ ]*'` first; if it names setupwraith, forget and re-join the
  network in Settings (owner becomes `android.uid.system`) or use Ethernet.
- Re-enable it before a factory reset; keeping it in the list makes `tvctl restore` do that.

## After the handover

- No on-screen keyboard (Xiaomi): `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.
- "Select a Home app" after restore + re-apply: another HOME claimant was disabled; `tvctl install`
  picks the single known stock launcher among the candidates, else pass `--stock`.
- Wrong time zone on old firmware: `settings put global auto_time_zone 0; cmd alarm set-timezone <Zone>`,
  or a fixed `GMT+hh:mm` when the firmware's tz data predates a rule change.
- Updated system apps (voice assistant on Xiaomi Android 9) can re-enable themselves at boot.

## Xiaomi China firmware keeps Home

- `set-home-activity` is stored but ignored: Home resolves to `com.mitv.tvhome`, or to Settings'
  `FallbackHome` once that is disabled. HomeKeyService stands in either way; with the stock launcher
  disabled (`pm disable-user --user 0 com.mitv.tvhome`, undo with `pm enable`) only a black
  FallbackHome flashes. It stayed disabled across a reboot on MiTV-ASTP0 (Android 9).
- `FallbackHome` shares its task with TV Settings, so Settings opening also reports FallbackHome.
- An injected `input keyevent KEYCODE_HOME` stops app switches for 5 s: the launcher opened by the
  service shows only after that. The remote's Home goes through the service and has no delay.
- After `adb install -r` the accessibility services may stay unbound until
  `enabled_accessibility_services` is written again (`tvctl install` does).
