# PORTING: using this on another TV

The method is general; the data is for one TV. This page lists the reference device, then every device-specific thing
that must be re-measured or changed.

## The reference device

| Item | Value |
|---|---|
| Model | TCL UnionTV, Android 9 (API 28), Realtek rtd284x |
| CPU | 4 x Cortex-A53, 32-bit only (`armeabi-v7a`), 700 to 900 MHz, governors `ondemand` and `performance` |
| RAM | 1.4 GB, `low_ram=true`, about 600 to 800 MB available at rest |
| Storage | `/data` about 4.2 GB, about 1.4 GB free after everything |
| Panel | 1280 x 720, 213 dpi |
| Security patch | 2020-03 |
| Client type | `TCL-AP-RT41DT-S1` (Asia-Pacific); the closest upstream example is Latin America (`reference/example-debloat`) |
| Remote | IR remote with a Bluetooth voice microphone; all keys arrive from an input device called `mtkinp`; see `work/remote-keys.md` |

## Constants to change for another device

| Where | What | How to find the new value |
|---|---|---|
| `restore-tv.ps1`, `work/glow.ps1`, `work/cpu-fast.ps1` | nothing hard-coded: pass `-TvHost <ip>` or set `TV_HOST`, or `-TvMac` / `TV_MAC` to find the TV through ARP | `arp -a` after connecting |
| `work/debloat.txt`, `work/debloat-pass2.txt`, `work/batches/` | the package lists | Build your own: `pm list packages`, `dumpsys package`, the reference lists as a guide. Never reuse blindly. |
| `work/theme/config.json` | `hidden_apps`, `live_tv_package` | the package that owns HDMI and the tuner on your TV |
| `launcher-fork` | package name `com.gathin.tvhome`, `NowPlaying.PACKAGE` (Spotify) | your own id if firmware has a Home allowlist; another music app |
| `phone-remote/server.py` | `MODEL` (`UnionTV`, used to recognise the TV), `FAST_KEYS` (input nodes and scancodes) | `getprop ro.product.model`; `dumpsys input` for nodes, then send each scancode with `sendevent` and read `interceptKeyTq keycode=` in `logcat` |
| `phone-remote/commands.py` | `APPS` package names | `pm list packages -3` and the leanback launcher entries |
| `work/cpu-hold.sh`, `work/cpu-fast.ps1` | the sysfs governor path | `ls /sys/devices/system/cpu/cpu0/cpufreq/` and test the write as the shell user |
| `tools/disable.ps1` | the stock launcher package (`com.google.android.tvlauncher`) | the Home package from `cmd package resolve-activity` |
| Private DNS | `dns.adguard-dns.com` | any DNS-over-TLS provider; check port 853 works on your network |
| ABI | everything sideloaded must match | `getprop ro.product.cpu.abilist` |

## What to re-measure

1. Which keys apps can see and which the firmware swallows (Task 3 in [PROMPTS.md](../PROMPTS.md)).
2. Whether `set-home-activity` works, or the stock launcher must be disabled.
3. Whether notification listeners work (they do not on `low_ram` Android 9; on other builds they would give a nicer
   now-playing).
4. Whether the vendor resets the CPU governor, and whether the shell user may write it.
5. Which services a remote's buttons depend on before disabling anything (test the Settings button and Input after
   each batch).
6. Whether the remote's microphone reaches an ordinary app (it did not here).

## Device-class assumptions in the code

- Android 9, API 28. The launcher targets API 34 with minSdk 26.
- A 1280 x 720 panel: wallpaper copies and sizes (`84 dp` art, `160 dp` card) were tuned on it.
- A slow GPU: `advanced_effects` is off and no blur is used.
- A remote with a dedicated settings key handled by the firmware.

## Contributing a new device

Add `docs/devices/<model>.md` with the facts table above, the keep list, the measured key map, the closed routes you
found, and the cut and keep lists. Keep addresses, serials and account names out of it.
