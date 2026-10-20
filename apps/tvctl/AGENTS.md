# tvctl

Go 1.22, standard library only. Module `github.com/kienmai160598/free-android-tv/apps/tvctl`.

| Package | Responsibility |
|---|---|
| `cmd/tvctl` | Flags, usage, one file per command group; env fallbacks `TV_HOST`, `TV_PORT`, `FORCE_HOME`, `FORCE_SETUPWRAITH`, `STOCK_LAUNCHER`, `INPUT_KEY_SERVICE`, `HOME_KEY_SERVICE`, `NOW_PLAYING` |
| `internal/adb` | Wire protocol over TCP: CNXN/AUTH (RSA, Android public key format), streams, `shell:`/`exec:`, sync push/pull |
| `internal/tv` | `Device` interface (`Shell`, `Push`), `Session` (lazy connect, reconnect after an error, md5-checked push with base64-over-shell fallback), Home queries |
| `internal/tv/tvtest` | In-memory `Fake` device for tests |
| `internal/debloat` | List parsing, guards, recovery record, restore commands |
| `internal/launcher` | Install and Home handover with rollback |
| `internal/theme` | Push a theme folder, restart Home keeping accessibility services |
| `internal/netcheck` | Is port 5555 closed |

## Invariants

- A list is refused as a whole if any entry is not a bare package name; BOM and CRLF are stripped.
- `disable`: guards first (setupwraith while it owns Wi-Fi; the Home owner while no other launcher
  would answer Home; settings apps and setupwraith never count as launchers), then the recovery
  record is written, then `pm disable-user --user 0` per package, then `pm list packages -d`.
- Recovery record: `~/.cleanyourtv/recovery-<host>-<sha1(resolved list path)[:8]>.json`, JSON with
  `created, home, host, list, packages{name: enabled|disabled|absent}, settings, version: 1`,
  written atomically. First record wins; later runs only add new names. A record that fails
  validation is never overwritten and blocks the run.
- `restore` replays the record (sorted `pm enable` of enabled ones, stock launcher unless it was
  disabled, recorded Home or resolve-and-set for the stock one, animation scales or delete for
  `null`). No record: falls back to enabling the whole list, unless other records exist for that
  host, then it asks for `--record`.
- `install`: push APK to `/data/local/tmp`, `pm install -r`, `am start -W` must say `Status: ok`
  with no `Error` line, then disable stock and set Home, then confirm Home; else roll back and, if
  that fails too, write `~/free-android-tv-handover-<host>.txt`.
- Every behaviour that changes what reaches a TV has a test against `tvtest.Fake`; protocol changes
  are tested against the fake adbd in `internal/adb/adb_test.go` (4 KB packets like Android 5).

`make test` runs gofmt, vet and tests.
