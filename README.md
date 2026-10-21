<div align="center">

# 📺 freeTV

**Debloat your Android TV. Give it a Material 3 Expressive home screen.**

No root · nothing uninstalled · everything undoable

[![Pipeline](https://github.com/kienmai160598/freeTV/actions/workflows/pipeline.yml/badge.svg)](https://github.com/kienmai160598/freeTV/actions/workflows/pipeline.yml)
![Root](https://img.shields.io/badge/root-not_needed-7c4dff)
![TV models](https://img.shields.io/badge/TV_models-108-00bfa5)
![Android](https://img.shields.io/badge/Android_TV-8.0%2B-3ddc84?logo=android&logoColor=white)
![Licence](https://img.shields.io/badge/licence-MIT-ff6d00)

</div>

## ✨ What you get

| | |
|---|---|
| 🧹 **Debloat** | Telemetry, ads and preloads off, with a record to put them back |
| 🏠 **km-launcher** | Dock, app grid, Edit Home, themes |
| 🌤️ **Widgets** | Weather, air quality, system info, calendar |
| 🎨 **Backdrops** | Moving shapes, a sky that follows the weather, wallpaper |
| 🕐 **Screensaver** | Clock in four styles |
| 🎛️ **Remote** | Inputs menu, Netflix / YouTube / gear buttons working again |

## 🚀 Quick start

**1. On the TV:** About → press **Build** 7× → Developer options → **USB debugging** on.

**2a. 🤖 Let an agent do it:** paste into Claude Code, Codex or similar:

```text
Read https://github.com/kienmai160598/freeTV/blob/main/docs/prompt.md and follow it.
My TV: <brand and model, or "find out">. Its IP: <ip, or "find it on my network">.
```

**2b. 🛠️ Or by hand** (Go 1.27+, JDK 17, Android SDK 34):

```bash
git clone https://github.com/kienmai160598/freeTV && cd freeTV
export TV_HOST=192.168.1.50
scripts/tvctl probe                                              # accept on the TV
scripts/tvctl disable packages/debloat-lists/<your-tv>/debloat.txt
make install
```

| | |
|---|---|
| ↩️ **Undo** | `scripts/tvctl restore packages/debloat-lists/<your-tv>/debloat.txt` |
| 🎨 **Theme** | `scripts/tvctl theme packages/themes/<theme>` |
| 🔒 **Close ADB** | debugging off, reboot, `scripts/tvctl closed` |
| 🪟 **Windows** | `scripts\tvctl.ps1`, `apps\launcher\build-and-install.ps1 -TvHost <ip>` |

## 🧩 Inside

| | | |
|---|---|---|
| `apps/tvctl` | Go CLI with its own ADB client | ![Go](https://img.shields.io/badge/Go-00ADD8?logo=go&logoColor=white) |
| `apps/launcher` | km-launcher, English + Vietnamese | ![Java](https://img.shields.io/badge/Java_17-E76F00?logo=openjdk&logoColor=white) |
| `packages/` | Debloat lists, themes, shared contract | ![JSON](https://img.shields.io/badge/JSON-5e5e5e?logo=json&logoColor=white) |

Runs on 🍎 macOS · 🐧 Linux · 🪟 Windows. Developers: [`AGENTS.md`](AGENTS.md), `make check`.

## 💜 Credits

Forked from [**Gathin23/cleanyourtv**](https://github.com/Gathin23/cleanyourtv). The debloat lists
come from Gathin and the cleanyourtv contributors. [MIT](LICENSE); Google Sans Flex under the
SIL OFL, Material Color Utilities under Apache 2.0.
