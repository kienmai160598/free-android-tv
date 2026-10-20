# free-android-tv

Debloat an Android TV or Google TV over Wi-Fi and install **km-launcher**, a Material 3 Expressive
home screen. No root, nothing uninstalled, every change undoable.

Forked from [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT): the Python tools
are replaced by `tvctl` (Go) and the launcher is redesigned.

```bash
export TV_HOST=192.168.1.50
scripts/tvctl probe
scripts/tvctl disable packages/debloat-lists/<your-tv>/debloat.txt
make install
scripts/tvctl restore packages/debloat-lists/<your-tv>/debloat.txt   # undo
```

Everything else, for people and agents alike: [`AGENTS.md`](AGENTS.md).

MIT, see [`LICENSE`](LICENSE).
