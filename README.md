# free-android-tv

Debloat an Android TV or Google TV over Wi-Fi and give it **km-launcher**, a Material 3 Expressive
home screen. No root and nothing uninstalled, so every change can be undone.

- **Debloat:** ready-made lists for 108 TV models switch off telemetry, ads and preloads, and
  record what they changed so it can be put back.
- **km-launcher:** dock and app grid, widgets (weather, air quality, system info, calendar), backdrops
  (moving shapes, a sky that follows the weather, wallpaper), a clock screensaver, an Inputs menu,
  remote buttons that work again, themes. English and Vietnamese.

## Quick start

On the TV: Settings → Device Preferences → About → press **Build** 7 times, then Developer options
→ **USB debugging** on (and Network debugging if the TV has it).

### With a coding assistant

Paste this into Claude Code, Codex or any agent that can run commands:

```text
Read https://github.com/kienmai160598/free-android-tv/blob/main/docs/prompt.md and follow it.
My TV: <brand and model, or "find out">. Its IP: <ip, or "find it on my network">.
```

The agent clones the repo and does everything in [`docs/prompt.md`](docs/prompt.md): it backs up the
TV, debloats it, fixes the time zone and night mode, installs the launcher and maps the remote. It
asks you before anything that depends on how you use the TV.

### By hand

Needs [Go](https://go.dev/dl) 1.27+, plus JDK 17 and Android SDK 34 for the launcher.

```bash
git clone https://github.com/kienmai160598/free-android-tv && cd free-android-tv
export TV_HOST=192.168.1.50                                      # the TV's IP
scripts/tvctl probe                                              # accept "Allow USB debugging?"
scripts/tvctl disable packages/debloat-lists/<your-tv>/debloat.txt
make install                                                     # km-launcher becomes Home
```

When you are done, turn debugging off on the TV, reboot it, and check with `scripts/tvctl closed`.

| To | Run |
|---|---|
| Undo the debloat | `scripts/tvctl restore packages/debloat-lists/<your-tv>/debloat.txt` |
| Change the look | `scripts/tvctl theme packages/themes/<theme>` |
| Use Windows | `scripts\tvctl.ps1` and `apps\launcher\build-and-install.ps1 -TvHost <ip>` |

Model not listed? The agent prompt builds a list for it; the format is in
[`packages/debloat-lists/`](packages/debloat-lists/).

## Supported

| | |
|---|---|
| TVs | Android TV and Google TV 8.0+; Fire TV for debloating only |
| Computers | macOS, Linux, Windows (amd64 and arm64) |
| Launcher languages | English, Vietnamese (follows the TV) |

## Repository

| Path | What | Stack |
|---|---|---|
| `apps/tvctl` | CLI: its own ADB client, debloat and restore, launcher install, themes | Go, standard library only |
| `apps/launcher` | km-launcher (`com.gathin.tvhome`) | Java 17, Android SDK 34 (minSdk 26), AndroidX, Material Color Utilities |
| `packages/contract` | Names and config keys shared by both apps, generated into Go and Java | JSON |
| `packages/debloat-lists`, `packages/themes` | One list per TV model; theme overlays | text, JSON |

`make help` lists the tasks and `make check` runs what CI runs. Conventions are in
[`AGENTS.md`](AGENTS.md).

## Credits and licence

Forked from [**Gathin23/cleanyourtv**](https://github.com/Gathin23/cleanyourtv) by **Gathin**, whose
project and contributors wrote the debloat lists. This fork rewrites the tools in Go, redesigns the
launcher and reorganises the repository.

MIT, see [`LICENSE`](LICENSE), including the original cleanyourtv copyright. Google Sans Flex: SIL
Open Font License. Material Color Utilities: Apache 2.0.
