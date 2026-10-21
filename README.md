<div align="center">

<h1>📺 freeTV</h1>

<p><b>A cleaner Android TV. No root.</b><br>
Debloat it over Wi-Fi and give it a Material 3 Expressive home screen.</p>

<p>
<a href="#quick-start">Quick start</a> ·
<a href="packages/debloat-lists/">Supported TVs</a> ·
<a href="docs/prompt.md">Agent prompt</a>
</p>

<img src="docs/media/home.gif" alt="km-launcher on a TCL Google TV: the dock, widgets and header" width="880">

</div>

<br>

**Debloat.** Lists for 108 TV models switch off telemetry, ads and preloads. Nothing is uninstalled,
and every change can be put back.

**Launcher.** A dock and app grid, widgets for weather, air quality, the system and the lunar
calendar, a sky that follows the weather, and a clock screensaver.

**Remote.** The Input, Netflix, YouTube and gear buttons keep working once the stock home is gone.

## Quick start

Turn on **USB debugging** on the TV (About → press Build 7 times → Developer options), then either
paste this into a coding agent such as Claude Code or Codex:

```text
Read https://github.com/kienmai160598/freeTV/blob/main/docs/prompt.md and follow it.
My TV: <brand and model, or "find out">. Its IP: <ip, or "find it on my network">.
```

or run it yourself (Go 1.27+, JDK 17, Android SDK 34):

```bash
git clone https://github.com/kienmai160598/freeTV && cd freeTV
export TV_HOST=192.168.1.50
scripts/tvctl probe                                   # accept the prompt on the TV
scripts/tvctl disable packages/debloat-lists/<your-tv>/debloat.txt
make install
```

Undo with `scripts/tvctl restore packages/debloat-lists/<your-tv>/debloat.txt`. Change the look with
`scripts/tvctl theme packages/themes/<theme>`. On Windows, use `scripts\tvctl.ps1`. When you are done,
turn debugging off and reboot the TV.

## Screens

| | |
|---|---|
| ![Home](docs/media/home.png) | ![Rows scrolling under the header](docs/media/scrolled.png) |
| ![Launcher settings](docs/media/settings.png) | ![Clock screensaver](docs/media/dream.png) |

## Under the hood

`apps/tvctl` is a Go CLI with its own ADB client. `apps/launcher` is km-launcher, in Java. The
debloat lists and themes live in `packages/`. Conventions are in [`AGENTS.md`](AGENTS.md), and
`make check` runs what CI runs.

<br>

<div align="center">
<sub>MIT · forked from <a href="https://github.com/Gathin23/cleanyourtv">Gathin23/cleanyourtv</a>, whose contributors wrote the debloat lists · Google Sans Flex (SIL OFL) · Material Color Utilities (Apache 2.0)</sub>
</div>
