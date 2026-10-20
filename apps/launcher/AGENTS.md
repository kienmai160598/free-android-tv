# km-launcher

Android TV home screen, package `com.gathin.tvhome`, minSdk 26, target 34. Plain Java with AndroidX
RecyclerView and DynamicAnimation plus `app/libs/material-color-utilities` (no Kotlin, no Compose).
Built for 1 GB TVs: no blur, no video, no polling, no background work beyond what is listed here.
Release APK: `app/build/outputs/apk/release/km-launcher.apk`, R8 on, debug-signed, CI fails over 700 KB.
Locales: English and Vietnamese only.

## Components (names are stored on TVs, never rename or move)

| Component | Role |
|---|---|
| `.MainActivity` | Home: header, widget shelf, dock, grid, Inputs/network/settings pills |
| `.LauncherSettingsActivity`, `.QuickSettingsActivity`, `.HomeSettingsActivity` | On-TV settings, gear-key panel, default-home picker |
| `.InputKeyService` | Accessibility: Input button (`KEYCODE_TV`, `KEYCODE_TV_INPUT`, `input_key_keycodes`), `app_keys` |
| `.HomeKeyService` | Accessibility: Home that sends `KEYCODE_ALL_APPS`, `settings_key_*`, `app_keys` |
| `.NowPlayingListener` | Notification listener used only to read media sessions |
| `.ClockDream` | Clock screensaver |

## Code map

- Motion and shape: `Motion` (expressive spring tokens), `Shapes`, `Expressive`, `FocusIndicator`, `GlassView` (springs' model), `LabelReveal`, `MenuHighlight`.
- Colour and skin: `Palette` (Tonal Spot from `background_color` or wallpaper), `M3Skin` (styles views by `android:tag`), `M3Switch`, `M3Progress`, `ShapeBadge`, `SettingsBanner`.
- Home content: `AppAdapter`, `Order`, `DockSlots`, `DockDecoration`, `GridNavigation`, `LibraryApps`, `AppLibraryDialog`.
- Widgets: `WidgetShelf`, `WidgetCard`, `Weather` (Open-Meteo, the only network use), `LunarCalendar`, `Shamsi`.
- Backgrounds: `WallpaperPlayer`, `WallpaperRotation`, `WallpaperSize`, `Sky`/`SkyView`, `ExpressiveBackdrop`.
- Festive skin: `Festival`, `DiyaDrawable`, `BellDrawable`, `MarigoldDrawable`, `PetalShowerView`.
- Config: `Config` reads `res/values/config.xml`, overlaid by `/sdcard/Android/data/com.gathin.tvhome/files/config.json` (a theme).
- Module cards: `Cards`, `CardFeature`. Remote keys: `RemoteKeys`. VPN pill badge: `VpnBadge`.

## Config keys (`config.xml`, or a theme's `config.json`)

Defined in `packages/contract/contract.json` (types, ranges) and read through the generated
`Contract.java`; `tvctl theme` validates a theme against the same definitions. A new key: add it to
the contract, `make generate`, read it in `Config` with `Contract.Key.*`.

Unknown or mistyped theme keys are ignored; a theme that does not parse is ignored whole.

| Key | Meaning |
|---|---|
| `dock_apps`, `fill_dock`, `hidden_apps` | Dock order; fill free dock slots with grid apps; never-shown packages |
| `columns` (1-8), `rows_below_dock` (0-3), `dock_row`, `dock_title`, `reorder_button` | Grid and dock layout |
| `live_tv_package`, `live_tv_action`, `source_panel_service`, `source_panel_action`, `input_action` | Live TV and input switching on odd firmware |
| `input_key_keycodes`, `app_keys` (`"190:pkg"`), `settings_key_keycodes`, `settings_key_scancodes`, `settings_key_panel` | Remote buttons for the key services |
| `animations`, `advanced_effects`, `reduced_motion` | Motion level; reduced for weak GPUs (MediaTek MT5895) |
| `background_color`, `wallpaper`, `wallpaper_rotation_minutes` (0-1440), `wallpaper_dim` (0-90), `sky_intensity` (0-100) | Backdrop; seeds the colour scheme |
| `widgets`, `weather_city`, `show_stats` | Widget shelf; empty city means locate by IP |
| `app_library`, `module_cards`, `show_home_settings_shortcut` | Optional features |
| `greeting_name`, `shamsi_date`, `vpn_app` | Header and network pill |
| `festival`, `festival_until`, `greeting`, `accent_color` | Festive skin (theme only) |

On-TV choices (columns, motion) win until "Follow theme"; pushing or resetting a theme clears them.
App library choices live in `library_apps.txt` / `library_home_removed.txt` (128 entries each).

## Module cards (spec 1)

An app may declare a tile on its `MAIN`+`LEANBACK_LAUNCHER` activity: a second filter with category
`com.gathin.tvhome.category.DOCK_CARD` and `<meta-data android:name="com.gathin.tvhome.card"
android:resource="@xml/...">` whose root is `<card spec="1" label="..." caption="..." accent="#RRGGBB"/>`.

- Literal values only. `label` 1-24 chars of `A-Za-z0-9 .&+'-`, `caption` up to 32; no `verified`/`official`;
  not a launcher UI name, `HDMI*`, `AV`, or another installed app's label (folded: lower case, alnum only).
- Limits: 32 cards read, 8 shown, 128 decisions, 64 XML events, depth 2, 16 attributes, exactly one signer.
- Nothing shows until the owner answers the prompt (Keep hidden is default). The decision in
  `files/cards.json` (v2) is pinned to the SHA-256 of the signer certificate and the folded label;
  a changed key or label asks again. An invalid file moves to `cards.json.bad`.
- Any failure disables cards for the session; rejections log under tag `TvHomeCards`.

## Verifying on a TV

- Inject keys and `screencap`; Live TV/HDMI/Netflix capture as empty images, press Home first.
- Key services: enabled ≠ bound. Check `settings --user current get secure enabled_accessibility_services`
  and the Bound services section of `dumpsys accessibility`; `input keyevent` bypasses accessibility
  filtering, so only the physical button proves the service. TCL may deny `APP_AUTO_START`
  (`appops set com.gathin.tvhome APP_AUTO_START allow`).
- The network pill shows the SSID only with `ACCESS_FINE_LOCATION` granted (`tvctl install` grants it).

Build and checks: `make check launcher` from the repo root.
