# freeTV

Debloat Android TV / Google TV over network ADB, then install km-launcher as Home.
Fork of Gathin23/cleanyourtv (MIT).

## Layout

| Path | What | Read |
|---|---|---|
| `apps/tvctl/` | Go CLI: ADB client, disable/restore, launcher install, themes | `apps/tvctl/AGENTS.md` |
| `apps/launcher/` | km-launcher, Java/Gradle, package `com.gathin.tvhome` | `apps/launcher/AGENTS.md` |
| `packages/contract/` | `contract.json`: names, theme layout and config keys shared by Go and Java; `make generate` writes `apps/tvctl/internal/contract/contract_gen.go` and `Contract.java` | |
| `packages/debloat-lists/` | One folder per TV model, `debloat.txt` + index | its `README.md` |
| `packages/themes/` | `config.json` overlays pushed with `tvctl theme` | its `README.md` |
| `scripts/tvctl` | Builds `apps/tvctl/bin/tvctl` on first use, then runs it | |
| `docs/prompt.md` | The end-to-end task prompt for debloating a new TV | |
| `docs/troubleshooting.md` | Device quirks that cost time before | read before touching a TV |
| `docs/remote.md`, `docs/devices/` | TV in another home; notes for untested models | |

## Commands

```bash
make help | style | test | check | launcher | install    # check = what CI runs
TV_HOST=<ip> scripts/tvctl probe | shell <cmd> | batch | push | pull | screencap <png>
TV_HOST=<ip> scripts/tvctl disable|restore packages/debloat-lists/<tv>/debloat.txt
TV_HOST=<ip> make install                          # build + tvctl install --input-key-service
TV_HOST=<ip> scripts/tvctl closed                  # port 5555 must be closed when done
```

Build env (macOS): `JAVA_HOME=/opt/homebrew/opt/openjdk@17`,
`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`.

## Working on a real TV

1. `probe`; the TV shows "Allow USB debugging?" once per key (`~/.android/adbkey`).
2. Back up `pm list packages`, `pm list packages -d`, `getprop`, `settings list global|secure|system`.
3. Disable only via a list and `tvctl disable` (records state, guards Home and Wi-Fi). Never uninstall.
   Ask the owner before disabling streaming apps, voice, Chromecast, Miracast or updates.
4. Install with `make install`; it starts the launcher before disabling the stock one and rolls back.
5. Verify every step: `input keyevent ...`, then `screencap`, and look at the image.
6. Close ADB at the end and confirm with `tvctl closed`.

## Code convention

- No comments (generated-file headers excepted); names and small functions carry the meaning.
- Names say what a thing is: `serviceStream`, `negativeInverse`, `remoteWallpaper`, never
  `s`, `n`, `tmp`. One-letter names only for Go method receivers.
- Related statements form a block; a blank line separates blocks that do different things
  (after a finished `if`/`for`/`switch`, before the final `return`).
- Lines stay within 100 characters (tabs count as 4). Long messages become named constants
  built from short string parts; long argument lists continue on the next line.
- DRY where it removes real duplication, SOLID: one responsibility per function and file.
- `scripts/check-style` (part of `make test` and CI) enforces gofmt and the line limit for Go,
  scripts, the Makefile and workflows. The launcher's Java follows the same rules.

## Rules

- A package name reaches a TV shell only after matching `^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`.
- Never disable the app that owns Home before another launcher owns it; never disable setupwraith
  while it owns the saved Wi-Fi network (`dumpsys wifi | grep -o 'cname=[^ ]*'`).
- Anything both apps must agree on (package and component names, theme file layout, config keys,
  their types and ranges) lives only in `packages/contract/contract.json`. Edit it, run
  `make generate`, never edit the generated files; CI fails when they drift.
- Manifest component names in `com.gathin.tvhome` are stored on TVs; never rename or move them.
- `local/` (gitignored) holds notes about the owner's own TV; never commit it.
