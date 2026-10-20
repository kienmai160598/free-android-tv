# free-android-tv

Debloat an Android TV or Google TV over Wi-Fi and install **km-launcher**, a Material 3 Expressive
home screen. No root, nothing uninstalled, every change undoable.

## Forked from

[**Gathin23/cleanyourtv**](https://github.com/Gathin23/cleanyourtv) by **Gathin**, MIT licence.
The debloat lists for 107 TV models come from that project and its contributors. This fork
replaces the Python and shell tools with `tvctl` (Go), redesigns the launcher in Material 3
Expressive, and reorganises the repository as a monorepo.

## How to use

1. **On the TV:** Settings → Device Preferences → About → press **Build** 7 times, then
   Developer options → **USB debugging** on (and Network debugging if the TV has it).
2. **On your computer** (macOS, Linux or Windows) with [Go](https://go.dev/dl) 1.27+:

   ```bash
   git clone https://github.com/kienmai160598/free-android-tv && cd free-android-tv
   export TV_HOST=192.168.1.50                       # the TV's IP address
   scripts/tvctl probe                               # accept "Allow USB debugging?" on the TV
   ```

3. **Debloat** with the list for your model, or write one (see
   [`packages/debloat-lists/`](packages/debloat-lists/)):

   ```bash
   scripts/tvctl disable packages/debloat-lists/<your-tv>/debloat.txt
   ```

4. **Install km-launcher** (needs JDK 17 and Android SDK 34):

   ```bash
   make install
   ```

5. **Close ADB** when you are done: turn debugging off on the TV, reboot, then
   `scripts/tvctl closed`.

Undo at any time with `scripts/tvctl restore packages/debloat-lists/<your-tv>/debloat.txt`.
Change the look without rebuilding: `scripts/tvctl theme packages/themes/<theme>`.
On Windows use `scripts\tvctl.ps1` and `apps\launcher\build-and-install.ps1 -TvHost <ip>`.
Your TV is not listed? Give [`docs/prompt.md`](docs/prompt.md) to a coding assistant.

## Tech stack

| Part | Stack |
|---|---|
| `apps/tvctl` | Go 1.27, standard library only; its own ADB client (TCP, RSA auth, shell, sync) |
| `apps/launcher` | Java 17, Android SDK 34 (minSdk 26), Gradle 8.9, AGP 8.5, R8; AndroidX RecyclerView and DynamicAnimation, Material Color Utilities |
| `packages/contract` | One JSON contract, generated into Go and Java constants (`make generate`) |
| CI | GitHub Actions: path-filtered pipeline, style, contract drift, tests, lint, cross-compiled binaries, release on `v*` tags |

## Language support

| | Supported |
|---|---|
| Launcher UI | English, Vietnamese (follows the TV's language) |
| TVs | Android TV and Google TV, Android 8.0+; Fire TV for debloating only |
| Computers running `tvctl` | macOS, Linux, Windows (amd64 and arm64) |

## Development

`make help` lists the tasks; `make check` runs what CI runs. Conventions and the repository map
are in [`AGENTS.md`](AGENTS.md).

## Licence

MIT, see [`LICENSE`](LICENSE), including the original copyright of the cleanyourtv authors.
Google Sans Flex: SIL Open Font License. Material Color Utilities: Apache 2.0.
