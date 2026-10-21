# km-launcher

Android TV home screen, package `com.gathin.tvhome`, minSdk 26, target 34. Plain Java with AndroidX
RecyclerView and DynamicAnimation plus `app/libs/material-color-utilities` (no Kotlin, no Compose).
Built for 1 GB TVs: no blur, no video, no polling, no background work beyond what is listed here.
Release APK: `app/build/outputs/apk/release/km-launcher.apk`, R8 on, debug-signed, CI fails over 700 KB. A `v*` tag publishes it to a GitHub release (`.github/workflows/release.yml`), signed with the key in the `KM_KEYSTORE_BASE64` secret and the `KM_KEYSTORE_PASSWORD`, `KM_KEY_ALIAS`, `KM_KEY_PASSWORD` secrets; `-PkmVersion` sets versionName from the tag.
Locales: English and Vietnamese only.

## Components (names are stored on TVs, never rename or move)

| Component | Role |
|---|---|
| `.MainActivity` | Home: header, widget shelf, dock, grid, Inputs/network/settings pills |
| `.LauncherSettingsActivity`, `.QuickSettingsActivity`, `.HomeSettingsActivity` | On-TV settings (side sheet), gear-key panel, default-home picker |
| `.RunningAppsActivity` | Running apps side sheet: switch to or force-stop apps, through `LocalAdb` |
| `.InputKeyService` | Accessibility: Input button (`KEYCODE_TV`, `KEYCODE_TV_INPUT`, `input_key_keycodes`, Menu if `menu_opens_inputs`), `app_keys`, `running_apps_keycodes`; no system switcher: Home's Inputs menu |
| `.HomeKeyService` | Accessibility: Home that sends `KEYCODE_ALL_APPS`, `settings_key_*`, `app_keys`; stands in for Home where the firmware keeps it (Xiaomi CN): `KEYCODE_HOME` and any other HOME activity coming to the front (the stock launcher, or Settings' FallbackHome once the stock one is disabled) open this one, 300 ms after it settles |
| `.ClockDream` | Clock screensaver; style from Launcher settings (shapes, glow, minimal, large) |
| `.InstallStatusReceiver` | Not exported: the PackageInstaller session's result; shows the system confirm dialog |

## Code map

- Motion and shape: `Motion` (expressive spring tokens), `Shapes`, `Expressive`, `FocusIndicator`, `GlassView` (springs' model), `LabelReveal`, `MenuHighlight`, `ShelfHighlight`, `SpringItemAnimator` (Edit Home), `SideSheet`, `FocusScrollView` (side sheets: every
  press moves focus one row, the row is kept centred on a spring).
- Colour and skin: `Palette` (Tonal Spot from `background_color` or wallpaper), `M3Skin` (styles views by `android:tag`), `M3Switch`, `M3Progress`, `ShapeBadge`, `SettingsBanner`.
- Home content: `HomeGrid` (full height; the header floats over its top `underlap()`, and rows scrolled under it fade into `headerScrim`), `AppAdapter`, `Order`, `DockSlots`, `DockDecoration`, `GridNavigation`, `LibraryApps`, `AppLibraryDialog`.
- Widgets: `WidgetShelf`, `WidgetCard`, `Weather` and `AirQuality` (Open-Meteo; the place from ipwho.is or geojs.io), `CpuMeter` (cpuidle in sysfs, since `/proc/stat` is hidden; `/proc/uptime` without cpuidle; else `AdbCpuFeed`, /proc/stat through `LocalAdb` once the owner turns the CPU line on), `LunarCalendar`, `Shamsi`.
- Network pill: `SpeedTest` (Cloudflare `speed.cloudflare.com`, only when asked from the Wi-Fi menu).
- Running apps: `LocalAdb` (adb client to `127.0.0.1:5555` with its own key in `files/adbkey`; the TV asks once to allow it), `BackgroundApps` (`ps` and recent tasks, `am force-stop`). Port 5555 is gone after a reboot until `adb tcpip 5555` runs again.
- Backgrounds: `WallpaperPlayer`, `WallpaperRotation`, `WallpaperSize`, `Sky`/`SkyView` (tinted by the
  weather widget's last report, which `WeatherShapes` draws in M3E shapes), `ExpressiveBackdrop`,
  `HomeLook` (the colour and backdrop chosen on the TV). Screensaver choices: `Screensaver`.
- Festive skin: `Festival`, `DiyaDrawable`, `BellDrawable`, `MarigoldDrawable`, `PetalShowerView`.
- Inputs on Xiaomi's China TVs: `XiaomiInputs` (their player's source numbers, from the port labels).
- Config: `Config` reads `res/values/config.xml`, overlaid by `/sdcard/Android/data/com.gathin.tvhome/files/config.json` (a theme).
- Module cards: `Cards`, `CardFeature`. Remote keys: `RemoteKeys`. VPN pill badge: `VpnBadge`.
- Updates: `LauncherRelease` (GitHub `releases/latest` of kienmai160598/freeTV, checked once when
  Launcher settings opens), `ReleaseInstaller` (streams `km-launcher.apk` into a PackageInstaller
  session, checks the asset's SHA-256), `InstallStatusReceiver`. Needs the `REQUEST_INSTALL_PACKAGES`
  appop (`tvctl install` sets it); releases must stay signed with the key local builds use.

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
| `running_apps_keycodes` (`config.xml` only, not in the contract) | Buttons that open the running apps panel; 284 `KEYCODE_ALL_APPS`, 187 `KEYCODE_APP_SWITCH` |
| `animations`, `advanced_effects`, `reduced_motion` | Motion level; reduced for weak GPUs (MediaTek MT5895) |
| `background_color`, `wallpaper`, `wallpaper_rotation_minutes` (0-1440), `wallpaper_dim` (0-90), `sky_intensity` (0-100) | Backdrop; seeds the colour scheme |
| `widgets`, `weather_city`, `show_stats` | Widget shelf; empty city means locate by IP |
| `app_library`, `module_cards`, `show_home_settings_shortcut` | Optional features |
| `greeting_name`, `shamsi_date`, `vpn_app` | Header and network pill |
| `festival`, `festival_until`, `greeting`, `accent_color` | Festive skin (theme only) |

On-TV choices (columns, motion, colour, backdrop) win until "Follow theme"; pushing or resetting a theme
clears them. The colour is stored as `background_color`; `backdrop` (shapes, sky, plain) and `dream_style`
are on-TV only, not in the contract. The screensaver time is the system's `screen_off_timeout`, written
with `WRITE_SECURE_SETTINGS` (`tvctl install` grants it).
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
