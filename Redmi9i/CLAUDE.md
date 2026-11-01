# CLAUDE.md

Project knowledge for AI coding agents (and humans) working in this repository. Your global operating rules still
apply; this file adds what is specific here. Read it fully before touching the phone or the code.

## What this project is

A toolkit that turns a spare, low-end Android phone into a minimal, themed, always-on device, without root and
without unlocking the bootloader. Everything is done over `adb` on a USB cable plus a few ordinary apps.

| Part | Where | What it is |
| --- | --- | --- |
| Debloat | `debloat/`, `docs/DEBLOAT.md` | Reversible removal of vendor and Google apps for user 0, in rebooted batches |
| Grand Line launcher | `grandline-app/` | Own home app (Kotlin, plain Views): drawer, widgets, weather, themed icons, live video wallpapers, poster lock screen, accessibility-button menu |
| Files | `files-app/` | Own file manager and viewers (image, video, audio, PDF, text, docx, xlsx, zip), same theme |
| Termux and sync | `docs/TERMUX-AND-SYNC.md` | Termux hardening, boot, rclone two-way sync to Google Drive |
| Theme assets | `theme-assets/`, `tools/` | Folder layout for the user's own art, plus scripts that prepare and push it |
| Prompts | `PROMPTS.md` | Device-agnostic prompts to rebuild all of this with an AI agent |

The theme is "One Piece / Straw Hat" (red `#D9232D`, gold `#F2B632`, ocean blue `#0B3C6E`, deep sea `#06223F`,
parchment `#F6E9C8`, Pirata One titles). The art is **not in the code**: it is the owner's own images, loaded from
`/sdcard/GrandLine/` at run time, with code-drawn fallbacks. The Gradle package prefix `dev.redmi9i.*` is
historical (the first target phone); it is only a name.

Documents: `README.md` (overview), `docs/ARCHITECTURE.md` (how the apps work), `docs/DEBLOAT.md`,
`docs/TERMUX-AND-SYNC.md`, `docs/TROUBLESHOOTING.md` (every trap met so far), `docs/ROADMAP.md` (what is open or
on hold), `docs/REFERENCE-DEVICE.md` (the phone it was built on), `docs/superpowers/` (original design specs and
plans for the launcher and Files app, kept as history), `theme-assets/README.md` (asset names and sizes).

## Hard rules for this repo

1. **Reversible first.** Capture a baseline (`pm list packages -f`, settings dumps, `getprop`) before any change.
   Remove packages with `pm uninstall -k --user 0` only (restorable with `cmd package install-existing`). Never
   `pm clear` or factory-reset without the owner saying so.
2. **One batch, one reboot, one check.** Small debloat batches (about 10 to 15), reboot after each, confirm boot.
   Removing a boot-critical package loops the phone into recovery and the only cure may be a data wipe.
3. **Never remove or disable** (learned the hard way, see `docs/DEBLOAT.md`): the vendor "find device" service,
   the vendor package guard, the downloads UI provider, the vendor security app (holds Autostart and
   battery settings Termux needs, and the install confirmation), the contacts provider, the dialer, telephony,
   SMS, SystemUI, Settings, WebView, the package installer, the keyboard and connectivity. Hide them from the
   launcher instead. On the reference phone the names are `com.xiaomi.finddevice`, `com.miui.guardprovider`,
   `com.android.providers.downloads.ui`, `com.miui.securitycenter`, `com.android.contacts`.
4. **Secrets never enter the repo.** OAuth client id/secret, rclone tokens, bot tokens, Wi-Fi passwords: only on
   the phone, in files with `chmod 600`. If the owner pastes a secret in chat, use it for the one task and do not
   write it to disk in the project. Do not run `rclone delete` or `purge` on a whole remote; delete only named
   test files.
5. **Art rules.** Use only images the owner supplies. Never fetch copyrighted art from the web. One Piece
   artwork is copyrighted: the repository must not ship it (see `.gitignore`), and the code must keep working
   with no images present.
6. **Stay on USB adb.** No Wi-Fi adb or open ports. adb installs may need a tap on the phone (see
   `tools/install-apk.sh`).
7. **The poster lock screen is decoration only.** It is an overlay with no PIN; it protects nothing. Say so
   whenever you describe it.
8. **No git operations** unless the owner asks in the current conversation (global rule). Hand over commands.
9. **Gated workflow.** One task at a time; the next only after the owner says "Go for Task n". Replies terse.
   Documentation is these files; do not create `EXPLANATION.md` or `DECISIONS.md` (the owner dropped them).
10. **Do not guess personal data.** For example, the weather city is typed by the owner in the weather card.

## Build, test, install

Toolchain: Gradle 8.9 (the wrapper's URL check can fail behind some networks; a local Gradle 8.9 works the same),
Android Gradle Plugin 8.7.3, Kotlin 2.0.21, `compileSdk 35`, `minSdk 29`, `targetSdk 29`, JDK 17. Android SDK path
goes in each app's `local.properties` (not committed). Plain Android Views, no Compose, no third-party runtime
libraries beyond AndroidX basics.

```bash
cd grandline-app && gradle -q testDebugUnitTest assembleDebug     # 56 JVM tests
cd files-app     && gradle -q testDebugUnitTest assembleDebug     # 43 JVM tests
tools/install-apk.sh grandline-app/app/build/outputs/apk/debug/app-debug.apk
tools/install-apk.sh files-app/app/build/outputs/apk/debug/app-debug.apk
tools/grant-launcher-permissions.sh        # once, and after any pm clear or reinstall that resets grants
tools/push-assets.sh                       # posters, marks, icons, wallpapers to /sdcard/GrandLine/
```

Make the launcher the default home (the chooser can be hidden by the vendor skin):

```bash
adb shell cmd package set-home-activity dev.redmi9i.grandline/.home.HomeActivity
# rollback to the previous launcher:
adb shell cmd package set-home-activity app.lawnchair/.LawnchairLauncher     # or the vendor home
```

Check what is the default: `adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME`.

On Windows with Git Bash: pass Windows paths (`C:/...`) for local files to `adb.exe`, and set
`MSYS_NO_PATHCONV=1` so `/sdcard/...` device paths are not rewritten. Keep long shell snippets in script files;
big heredocs with many quotes break the shell parser. Edit sources with the editor tools, not `sed` pipelines.

## How it works, in short (full detail in `docs/ARCHITECTURE.md`)

- **Launcher** (`HomeActivity`): pager of home pages (5 x 4 grid), a 4-slot dock, a swipe-up drawer with the crew
  panel (poster of the day, clock, weather), search and an alphabetical list. Home is empty by design: apps live in
  the drawer. Layout is pure data (`LayoutModel.kt`, `Reconcile.kt`), saved as JSON in preferences. Hidden apps never
  show; some are hidden by default (once, so Unhide sticks).
- **Wallpapers**: still images or one muted looping video (`VideoWallpaperView`, `TextureView` + `MediaPlayer`
  over a still frame). `SystemWallpaper.sync` copies the current still into Android's own wallpaper so the vendor
  default never flashes behind stock recents.
- **Lock overlay and accessibility menu** (`LockService`, an accessibility service): draws a random wanted poster
  over the screen on every wake; the nav-bar accessibility button opens `QuickMenuView` (icons with names).
  `LockEnabler` re-enables the service whenever the launcher starts.
- **Termux boot**: `TermuxBoot` asks Termux (RUN_COMMAND intent) to run `~/bin/ensure-services.sh` once per
  launcher process, so background services start even when the vendor skin blocks Termux:Boot.
- **Weather**: Open-Meteo forecast and geocoding (no key), cached 30 minutes, drawn glyphs.
- **Files**: `FsProvider` abstraction (local files and SAF for USB/SD), operation engine with a foreground service,
  search engine, thumbnail loader, viewers per type.
- **Theme assets**: `Art` loads from `/sdcard/GrandLine/`: `posters/` (black JPEG, 720 px), `marks/` (transparent
  PNG, 360 px), `icons/` (256 px PNG), `wallpapers/` (jpg, png, mp4, webm; `drawer.jpg`). Crew of the day is
  `members[epochDay mod 10]`; the launcher and Files app use the same rule.

## Gotchas that cost time (full list in `docs/TROUBLESHOOTING.md`)

- `adb install` can fail with `INSTALL_FAILED_USER_RESTRICTED` when the phone shows an "install via USB" dialog
  nobody taps; `tools/install-apk.sh` taps it. An interrupted install can still apply.
- Android restarts an enabled accessibility service after a kill or reboot, but **force-stop disables it**. The
  launcher re-adds it (needs `WRITE_SECURE_SETTINGS`) and removes the stock accessibility menu from the list, which
  would otherwise own the nav-bar button. The service config is cached: after editing `lock_service.xml`, reinstall and
  `am force-stop` the app, then start it again. Do not `settings delete`/put the list while the app runs: the system can get
  stuck "binding" the service (`dumpsys accessibility` shows `Bound services:{}`).
- `TYPE_ACCESSIBILITY_OVERLAY` windows draw above the status and navigation bars. Sliding an overlay window with
  `translationY` leaves trails; slide only inner content.
- A `TextureView` cannot have a background drawable (crash): keep a still `ImageView` underneath.
- Stock recents on the reference phone cannot be restyled or given a live background: it paints a static dimmed
  snapshot of the system wallpaper, and the nav Recents button raises no key event a service can intercept. The
  custom recents experiments were removed on purpose. Do not retry them.
- A launcher that declares the `LAUNCHER` category creates a normal task that shows as a recents card; `HomeActivity`
  declares only `HOME` and `DEFAULT`.
- The device-idle whitelist (`cmd deviceidle whitelist +com.termux`) can be lost after a failed boot; re-check it.
- An empty app list from the package manager must never overwrite the saved layout (guard exists; keep it).

## Status

Done and verified on the phone: baseline backup, debloat, launcher swap, Termux hardening, rclone sync, Telegram
app, Files app (and its restyle), Grand Line launcher with crew panel, weather, live wallpapers, poster lock,
accessibility menu, themed icons. Open or on hold: see `docs/ROADMAP.md` (speech and decision-model pipeline,
OAuth app publication for rclone, USB-drive test of Files, soak tests).
