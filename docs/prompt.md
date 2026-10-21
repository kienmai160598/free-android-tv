# Task: set up an Android TV with freeTV

You are a coding assistant with a shell. The owner gives you the TV's model and IP address, or asks
you to find them. Debloat the TV, fix its settings and install km-launcher as Home, working on the
real device. Do the steps in order and check each one on the TV itself (key injection plus a
screenshot). Ask the owner only about choices that depend on how they use the TV. Do not stop until
Finish is done.

## Prepare

- Clone `https://github.com/kienmai160598/freeTV` unless you are already in it, and read
  `AGENTS.md` and `docs/troubleshooting.md`.
- Needs Go 1.27+ for `scripts/tvctl`, plus JDK 17 and Android SDK 34 for the launcher. Install what is
  missing or tell the owner how.

## Connect

- Find the TV (hostname, ARP table, or ask for the IP). For a TV in another home, use its Tailscale
  address (`docs/remote.md`) and do not scan the local network.
- Run `TV_HOST=<ip> scripts/tvctl probe`. Before it runs, tell the owner to accept "Allow USB
  debugging?" with "Always allow" ticked.
- Port 5555 closed: Wireless-debugging-only TV, see `docs/troubleshooting.md`.

## Back up

- Save `pm list packages`, `pm list packages -d`, `getprop` and `settings list global|secure|system`.
- Ask what is plugged into the USB ports (some boxes cut USB power while debugging is on).

## Debloat

- If `packages/debloat-lists/<model>/` exists, read its header and use it; otherwise create one.
- Sort packages into telemetry/analytics, manufacturer content and ads (PatchWall, recommendation
  rows), unneeded background services, preloads, streaming apps. Check unclear ones with
  `dumpsys package <pkg>`.
- Never disable: TV/tuner/HDMI input apps and services, the input menu, remote and Bluetooth
  pairing, the keyboard, Play Store, Play Services.
- Ask with a checklist before touching streaming apps, voice, Chromecast, Miracast, the updater.
- Apply with `scripts/tvctl disable <list>` in small batches, screenshot between batches.
- Offer Private DNS ad blocking (Android 9+): `private_dns_specifier dns.adguard-dns.com`,
  `private_dns_mode hostname`; does not block YouTube ads; undo `private_dns_mode opportunistic`.
- Offer replacements for preloads the owner still needs, e.g. Material Files
  (`me.zhanghai.android.files`) for a file manager. A replacement must not catch intents the
  TV's own apps send: after installing it, switch to each HDMI input and Live TV once.

## System fixes

- Time zone: compare `getprop persist.sys.timezone` with the zone the IP locates
  (`get.geojs.io/v1/ip/geo.json`). Fix it in Settings → Date & time; where the firmware hides
  that, use `service call alarm 3 s16 <zone>` (Android 9; check the transaction number on other
  versions).
- Night mode: `cmd uimode night auto`, never `yes`, so the launcher follows the system. Some
  firmware (Google TV) locks it on.
- Picture: if colours look flat, look at the picture mode and the per-source settings in the
  manufacturer's Settings, and leave the final choice to the owner.

## Launcher

- Check the TV accepts sideloaded APKs and third-party HOME apps; if not, stop after debloating.
- Ask which apps go in the dock and in which order; set `dock_apps` in a theme or `config.xml`.
- `TV_HOST=<ip> make install` (it handles the Home handover and rollback). On Google TV, disable
  setupwraith first, only after checking it does not own the saved Wi-Fi network.
- Ask the owner to press every remote button; map dead ones with `input_key_keycodes`,
  `app_keys`, `settings_key_keycodes` and reinstall with `--input-key-service` /
  `--home-key-service`. No Input button: Launcher settings → Menu button opens Inputs. While an
  HDMI input plays, the TV's keys must reach its player, or CEC hands the device back to the TV.
- Walk the owner through Launcher settings: colour, backdrop, screensaver time and clock style,
  motion, widgets (each widget's own settings), Edit Home.

## Finish

- For key services, only the physical button is proof; ask the owner to press it.
- Give the owner the restore command, the install command, and how to change the dock or
  wallpaper.
- Add or update `packages/debloat-lists/<model>/` (format in its README).
- Offer to close ADB: debugging off, revoke authorisations, reboot, `scripts/tvctl closed <ip>`.
