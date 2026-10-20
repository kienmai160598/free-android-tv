# free-android-tv

Debloat Android TV / Google TV over network ADB and install km-launcher, a Material 3 Expressive
home screen. Fork of Gathin23/cleanyourtv (MIT).

## Layout

| Path | What | Stack |
|---|---|---|
| `apps/tvctl/` | The CLI: ADB client, debloat/restore, launcher install, themes | Go, standard library only |
| `apps/launcher/` | km-launcher (`com.gathin.tvhome`) | Java, Gradle, JDK 17, Android SDK 34 |
| `packages/debloat-lists/` | One folder per TV model with its `debloat.txt`, indexed in its README | data |
| `packages/themes/` | Themes `tvctl theme` pushes to the launcher | data |
| `scripts/` | `tvctl` wrappers that build the binary on first use | sh, PowerShell |
| `docs/` | Prompt, contributing, troubleshooting, module cards | Markdown |

## Commands

`make help` lists them. `make test` (Go), `make check` (Go + launcher tests and lint),
`make launcher` (APK), `make install` (TV_HOST required).

## Rules

- Code has no comments; names and small functions carry the meaning. DRY where it helps, SOLID.
- `apps/tvctl`: anything that changes what reaches a TV gets a test against `internal/tv/tvtest`.
  A debloat list is refused unless every entry is a bare package name.
- `apps/launcher`: manifest components keep their names in `com.gathin.tvhome`; the TV stores them.
  Keep the APK small, CI fails above 700 KB.
- Packages are disabled with `pm disable-user`, never uninstalled.
