# Troubleshooting and lessons

Every trap hit while building this, with the fix. Grouped by area.

## adb and installs

| Symptom | Cause and fix |
| --- | --- |
| `INSTALL_FAILED_USER_RESTRICTED` | Developer options "Install via USB" is off, or the phone shows "Install this app via USB?" for about 10 seconds and nobody tapped. Turn the toggle on (some vendors need an account signed in for it) or use `tools/install-apk.sh`, which taps the dialog. Also happens if the vendor package guard was removed |
| Install seems to fail but the app is there | An interrupted `adb install` can still apply. Check `pm list packages` before retrying |
| adb drops while the phone idles | Seen once over USB. Reconnect; for headless use, plan another access path |
| `adb push` to `/sdcard/...` creates `C:/Program Files/Git/sdcard/...` errors | Git Bash rewrote the device path. `export MSYS_NO_PATHCONV=1` |
| `adb.exe` cannot find a local file | Pass Windows paths (`C:/...`), not `/c/...` |
| Long shell snippets die with "unexpected EOF" | Quotes in big heredocs. Put the commands in a script file |
| Gradle wrapper fails its URL check | Use a locally unpacked Gradle 8.9, or `--no-validate-url` when regenerating |

## Debloat and boot

See `docs/DEBLOAT.md`: boot-critical packages, the one-batch-one-reboot rule, the `install-existing` rescue loop, the keep list.
A wait of about 20 s after the last uninstall is needed before a reboot, or the change is lost.

## Termux

| Symptom | Fix |
| --- | --- |
| Boot script does not run after reboot | Autostart for all three Termux apps, open Termux:Boot once, `deviceidle whitelist` entries, then rely on the launcher helper (`docs/TERMUX-AND-SYNC.md`) |
| Whitelist gone after a failed boot | Re-add it; check with `dumpsys deviceidle whitelist | grep termux` |
| `crond` not running | `ensure-services.sh` starts it; the launcher calls it on start. Needs `allow-external-apps = true` and the `RUN_COMMAND` grant |
| rclone says it needs `--resync` | Run the manual resync command in `docs/TERMUX-AND-SYNC.md` |

## Launcher

| Symptom | Cause and fix |
| --- | --- |
| Home got refilled with every app | An old re-seed guard misfired after an interrupted install. `autoPlace` is now off by default; an empty app list never rewrites the saved layout |
| Crash "TextureView doesn't support displaying a background drawable" | Never set a background on the video view; use a still `ImageView` underneath |
| Grand Line appears as a card in recents | The `LAUNCHER` category creates a normal task. Declare only `HOME` and `DEFAULT`, and `excludeFromRecents` |
| A vendor wallpaper flashes behind recents | Stock recents is see-through. `SystemWallpaper.sync` keeps the system wallpaper equal to the home still |
| Want recents with a live or custom background | Not possible here. Stock recents draws a static dimmed snapshot of the system wallpaper taken when it opens, and the Recents button raises no key event a service can intercept. A custom recents cover was built and removed (stuck covers, a split second of stock recents first) |
| Kotlin: `TextClock.format12Hr` unresolved | The names are `format12Hour` and `format24Hour` |
| aapt error in `strings.xml` | An unescaped apostrophe. Reword or escape |
| Python edit script writes a literal newline into a Kotlin string | `\\n` vs `\n` inside the generated source. Use the editor tool for strings with escapes |

## Lock overlay and accessibility button

| Symptom | Cause and fix |
| --- | --- |
| Overlay trails or leaves a gap at the top | Do not animate the window; slide only the inner content. Place the window at `y = -statusBarHeight` with height `realHeight + statusBarHeight` |
| Service stops after the launcher is force-stopped | Force-stop disables accessibility services. `LockEnabler` re-adds it on launcher start (needs `WRITE_SECURE_SETTINGS`); or run `tools/grant-launcher-permissions.sh` |
| Changed `lock_service.xml`, no effect | The system caches the config. Reinstall, `am force-stop` the app, start the launcher (it re-enables the service). `settings put ""` fails with "Bad arguments" |
| The button opens the stock menu with Google Assistant again | The stock `AccessibilityMenuService` got back into `enabled_accessibility_services` and our service is not bound (`dumpsys accessibility | grep Bound` shows the stock one). Start the launcher: `LockEnabler` removes the stock entry. Or run `tools/grant-launcher-permissions.sh` |
| `Bound services:{}` and the service sits in "Binding" | Caused by deleting and re-putting the enabled list while the app was running. `am force-stop` the app and start the launcher |
| The accessibility button is not in the navigation bar | It shows only while an accessibility service that requests it is enabled, at the bottom right of the three-button bar. Tap that spot, not the status bar icon |
| Menu shows in screenshots | Close it first, then act; `global()` waits 150 ms |
| A tap on the card counts as "outside" | Make the card `isClickable = true` |

## Files app

| Symptom | Cause and fix |
| --- | --- |
| Toolbar action bar in multi-select sits above a thin leftover toolbar | Cosmetic; known, not fixed |
| Cannot write to a USB or SD drive | Android 10 needs one folder grant per volume through the system picker; the app asks on the first write |

## Theme assets

| Symptom | Fix |
| --- | --- |
| Generated icon PNGs show a grey checkerboard | The checkerboard was baked in as pixels (no alpha). `tools/make-icons.py` removes it |
| A corner sparkle mark from an image generator | Same script keeps only the largest shape |
| Video too big or stutters | `tools/encode-wallpaper.sh`: no audio, 30 fps, at most 720 px wide, CRF 31 |
| Weather says "Tap to set your city" | Tap the card and type a city; it is geocoded with Open-Meteo |
