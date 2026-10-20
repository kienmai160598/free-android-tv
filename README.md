# free-android-tv

Debloat an Android TV or Google TV over Wi-Fi and replace its ad-filled home screen with a small
Material 3 Expressive launcher. No root, nothing uninstalled, and undo restores exactly what was
there before.

## Forked from

[**Gathin23/cleanyourtv**](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT). This fork
replaces the Python and shell tools with `tvctl`, a single Go binary, and redesigns the launcher in
Material 3 Expressive. The debloat lists in [`examples/`](examples/) come from the upstream project.

## Quick start

On the TV: Settings → Device Preferences → About → press **Build** 7 times → Developer options →
**USB debugging** on. On your computer, with [Go](https://go.dev/dl) 1.22+:

```bash
export TV_HOST=192.168.1.50                         # the TV's IP
tools/tvctl probe                                   # accept "Allow USB debugging?" on the TV
tools/tvctl disable examples/<your-tv>/debloat.txt  # debloat
launcher/build-and-install.sh --input-key-service   # install the launcher (JDK 17 + Android SDK 34)
tools/tvctl restore examples/<your-tv>/debloat.txt  # undo, any time
```

Windows: `tools\tvctl.ps1` and `launcher\build-and-install.ps1 -TvHost <ip>`. TV not in
`examples/`? Give [`PROMPT.md`](PROMPT.md) to a coding assistant and it finds the bloat for you.

## tvctl

| Command | |
|---|---|
| `probe`, `shell <cmd>`, `batch < file` | Inspect and run commands on the TV |
| `push`, `pull`, `screencap <file.png>` | Files and screenshots (push is md5-checked) |
| `disable <list>` / `restore <list>` | Apply or undo a debloat list |
| `install <apk>` | Install the launcher and hand Home over, with rollback |
| `theme <folder>` / `theme --reset` | Push a theme from [`themes/`](themes/) |
| `closed [ip]` | Confirm network ADB is off again |

Run `tools/tvctl` for all flags. It speaks ADB itself (no adb binary, no Python) and uses
`~/.android/adbkey`, so a TV that trusts adb is not asked again.

**Safety:** `disable` records each package's state before changing anything, so `restore` puts back
exactly that. It refuses lists with anything but package names, never disables the only app that
answers Home, and keeps Google TV's setup wizard while it owns the Wi-Fi network.

## The launcher

Plain Java, built for 1 GB TVs: spring motion, M3 shapes, a dynamic colour scheme, a dock and app
grid, and widgets (weather, memory, now playing, lunar calendar). English and Vietnamese. Configure
it in [`config.xml`](launcher/app/src/main/res/values/config.xml) or with a theme.

## When you are done: close ADB

Turn USB and network debugging off on the TV, reboot, then check `tools/tvctl closed <ip>`.

More: [troubleshooting](docs/TROUBLESHOOTING.md) · [remote TVs](docs/REMOTE.md) ·
[contributing a TV list](CONTRIBUTING.md)

## Development

```
cmd/tvctl/   internal/adb  internal/tv  internal/debloat  internal/launcher  internal/theme
launcher/    examples/     themes/      tools/
```

```bash
go vet ./... && go test ./...
cd launcher && ./gradlew testReleaseUnitTest lintRelease assembleRelease
```

## Licence

MIT, see [`LICENSE`](LICENSE). Google Sans Flex: SIL OFL. Material Color Utilities: Apache 2.0.
