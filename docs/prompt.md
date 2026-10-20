# Task: debloat a TV and install km-launcher

Input from the owner: the TV model. Work on the real device in this repo, step by step, verify each
step with a screenshot from the TV, and ask the owner before anything that depends on their use.

## Connect

- Find the TV (hostname, ARP table, or ask for the IP); for a TV in another home use its Tailscale
  address (`docs/remote.md`) and do not scan the local network.
- Use `scripts/tvctl` (`probe` first). Tell the owner when "Allow USB debugging?" will appear.
- Port 5555 closed: Wireless-debugging-only TV, see `docs/troubleshooting.md`.

## Back up

- Save `pm list packages`, `pm list packages -d`, `getprop` and `settings list global|secure|system`.
- Ask what is plugged into the USB ports (some boxes cut USB power while debugging is on).

## Debloat

- If `packages/debloat-lists/<model>/` exists, read its header and use it; otherwise create one.
- Sort packages into telemetry/analytics, manufacturer content and ads (PatchWall, recommendation
  rows), unneeded background services, preloads, streaming apps. Check unclear ones with
  `dumpsys package <pkg>`.
- Never disable: TV/tuner/HDMI input apps and services, the input menu, remote and Bluetooth pairing,
  the keyboard, Play Store, Play Services.
- Ask with a checklist before touching streaming apps, voice, Chromecast, Miracast, the updater.
- Apply with `scripts/tvctl disable <list>` in small batches, screenshot between batches.
- Offer Private DNS ad blocking (Android 9+): `private_dns_specifier dns.adguard-dns.com`,
  `private_dns_mode hostname`; does not block YouTube ads; undo `private_dns_mode opportunistic`.

## Launcher

- Check the TV accepts sideloaded APKs and third-party HOME apps; if not, stop after debloating.
- Ask which apps go in the dock and in which order; set `dock_apps` in a theme or `config.xml`.
- `TV_HOST=<ip> make install` (it handles the Home handover and rollback). On Google TV, disable
  setupwraith first, only after checking it does not own the saved Wi-Fi network.
- Ask the owner to press every remote button; map dead ones with `input_key_keycodes`, `app_keys`,
  `settings_key_keycodes` and reinstall with `--input-key-service` / `--home-key-service`.

## Finish

- Verify with key injection + screenshots; for key services, only the physical button is proof.
- Give the owner the restore command, the install command, and how to change the dock or wallpaper.
- Add or update `packages/debloat-lists/<model>/` (format in its README).
- Close ADB: debugging off, revoke authorisations, reboot, `scripts/tvctl closed <ip>`.
