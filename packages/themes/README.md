# Themes

A theme is a folder with `config.json` (keys: `apps/launcher/AGENTS.md`), optionally `wallpaper.jpg`
and a `wallpapers/` folder for rotation. `scripts/tvctl theme <folder>` pushes it to
`/sdcard/Android/data/com.gathin.tvhome/files/` and restarts Home; `--reset` removes it.

| Theme | |
|---|---|
| `default/` | The built-in look as JSON, a starting point |
| `expressive/` | M3 Expressive, colour seeded from `background_color` |
| `aurora/` | Dark aurora wallpaper |
| `dussehra/` | Festive skin (`festival`), expired |
| `six-columns/` | Six columns, one row under the dock |

Wallpapers: dark, 1920×1080, under ~300 KB; JPEG/PNG/WebP in `wallpapers/`, names `[A-Za-z0-9_.-]`,
up to 100 files of 10 MB; images over 4K after downsampling are not shown.
