# Prompts

Copy-paste prompts for an AI coding agent (written for Claude Code, but any agent with a shell works) that rebuild this
whole project on an Android phone you own. They assume nothing about the phone: the agent connects over adb, finds out what
it is dealing with, and adapts.

## Before you start

1. On the phone: Settings, About phone, tap the build number seven times; then Developer options, turn on **USB debugging**
   (and "Install via USB" or "USB debugging (Security settings)" if your phone has them).
2. Connect the phone to your computer with a USB cable and accept the "Allow USB debugging?" prompt on the phone.
3. Install `adb` (Android platform-tools) on the computer and make sure `adb devices` lists the phone.
4. Open the agent in this repository folder. Read `CLAUDE.md` first; it is the project's operating manual.

Use the prompts **in order, one at a time**. Each one ends with what "done" means and tells the agent to stop and report. Review
that report before pasting the next prompt. Anything marked *optional* can be skipped.

Be aware: debloating and settings changes can make a phone unbootable if done carelessly, and a rescue may need a factory reset.
The prompts push the agent toward small, reversible steps, but you are responsible for your own device. Back up what you care
about first.

---

## 0. Ground rules (paste this first, once per session)

```text
You are helping me turn a spare Android phone into a minimal, themed, always-on device. We work over adb on a USB cable only.
No root, no bootloader unlock, no Wi-Fi adb.

Rules, always:
- Read CLAUDE.md and docs/ in this repo before acting.
- Reversible first. Capture a baseline before any change. Use `pm uninstall -k --user 0` (restorable with
  `cmd package install-existing`), never `pm clear`, never a factory reset, without asking me.
- Small steps: one batch of changes, one reboot, one check. If a step could stop the phone from booting, say so and ask first.
- Never write secrets (OAuth ids, tokens, passwords) into this repository. Keep them on the phone in files with chmod 600.
- Use only images I supply. Do not download copyrighted art.
- Do not run git commands. Give me the commands to run.
- Work on one task at a time and stop when it is done. Wait for me to say "go" before the next one.
- Report plainly: what you changed, what you checked and how, what failed, what you did not do.
- Keep replies short. Write code and docs normally.
```

## 1. Connect and profile the device

```text
Task 1: connect to the phone and work out what it is.

With adb (platform-tools), check `adb devices`. Then gather, read-only: manufacturer, model, codename, Android version and API
level, vendor skin and version, security patch, CPU ABI list (and whether userspace is 32-bit or 64-bit), SoC and core layout,
RAM and swap, storage, display size and density, radios, sensors, whether the bootloader is locked, whether `su` exists, which
users exist (`pm list users`), and the package counts (system, user, disabled).

Work out the constraints that matter: can I run 64-bit binaries, how much RAM is really usable for apps, does the vendor skin
restrict installs over USB, kill background apps, or hide the default-home chooser.

Write the findings to docs/REFERENCE-DEVICE.md as the profile of THIS phone (keep a note on any figure you did not measure).
Do not change anything on the phone.

Done when: the profile exists, every row says whether it was measured or assumed, and you list the three constraints most likely
to cause trouble. Stop.
```

## 2. Baseline backup

```text
Task 2: save the phone's current state so every later change can be undone.

Into backup/ save: `pm list packages -f`, `pm list packages -d`, system and user package lists, `settings list global`,
`settings list secure`, `settings list system`, the default home activity, `pm list users`, and a `getprop` dump.
Add backup/ to .gitignore (it holds device identifiers).

Done when: the files exist, the package count matches `adb shell pm list packages | wc -l`, and nothing on the phone changed. Stop.
```

## 3. Debloat

```text
Task 3: remove vendor, carrier and Google bloat for user 0 only, safely.

Read docs/DEBLOAT.md first: it records what went wrong before (boot-critical packages, a boot loop, the rescue loop).

1. Triage every system package into debloat/packages.csv with columns package, batch, verdict (remove, keep, unsure) and reason.
   Mark as keep anything involved in boot, telephony, SMS, dialer, SystemUI, Settings, WebView, package installer, keyboard,
   connectivity, and any vendor "find device", "package guard", "security center" or "downloads UI" component. Treat names with
   "service", "provider" or "framework" as risky.
2. Show me the table and wait for my approval of the remove list.
3. Apply in batches of 10 to 15 with `pm uninstall -k --user 0`, logging to debloat/applied.log. Wait about 20 seconds after the
   last uninstall, reboot, wait for `sys.boot_completed`, and check that the home screen is up. If the phone does not boot but
   adb is alive, loop `cmd package install-existing --user 0 <package>` until it succeeds.
4. Keep a restore script that reinstalls everything in the log, and test it on one package.
5. Record RAM and package count before and after (`dumpsys meminfo`).

Done when: the phone boots, calls, SMS, Wi-Fi, mobile data and camera still work (ask me to check them), the removed list is
recorded, and the restore script worked once. Stop after each batch for my go-ahead.
```

## 4. Install the base apps

```text
Task 4: install a browser and the Termux apps.

Download APKs to downloads/ (git-ignored) from the official sources, matching the phone's ABI: a browser of my choice, Termux,
Termux:Boot and Termux:API from the same source (GitHub releases or F-Droid, not the Play Store, so their signing keys match).
Verify SHA-256 and signatures where the project publishes them. Treat downloads as untrusted: one folder each, never run code
from inside it.

Install with tools/install-apk.sh (it taps the "install via USB" confirmation some phones show). If installs fail with
INSTALL_FAILED_USER_RESTRICTED, tell me which developer option or dialog to enable.

Do not change the default home yet. Open Termux:Boot once so it registers.

Done when: each app is installed and launches. Stop.
```

## 5. Termux hardening and boot

```text
Task 5: make Termux survive screen-off, battery management and reboot.

Read docs/TERMUX-AND-SYNC.md. Update packages, install termux-api, git, curl, cronie. Then, in order, and checking after each:
vendor autostart on for the three Termux apps (tell me where to tap if it is not scriptable), battery mode "no restrictions",
`cmd deviceidle whitelist +com.termux +com.termux.boot +com.termux.api`, a ~/.termux/boot/00-start.sh that takes the wake lock,
logs a line to ~/boot.log and starts crond, and a ~/bin/ensure-services.sh that does the same idempotently. Set
`allow-external-apps = true` in ~/.termux/termux.properties.

Verify across two reboots: boot.log gains a line, Termux runs, the wake lock is held, the whitelist is still there.
Do not claim a screen-off soak test unless you actually ran one (the screen may not sleep while charging).

Done when: all of the above verified and the exact commands to redo it are in docs/TERMUX-AND-SYNC.md. Stop.
```

## 6. Google Drive sync (optional)

```text
Task 6 (optional): two-way sync of one phone folder with one Google Drive folder, every 30 minutes, using rclone in Termux.

I will create my own Google Cloud OAuth client (Desktop type) and publish the consent screen to "In production". Never put the
client id, secret or token in this repository or in files outside the phone's chmod 600 rclone.conf. If I paste them into the
chat, use them for this task only.

1. `pkg install rclone`. Create remote `gdrive` scoped to ONE Drive folder (`root_folder_id`), scope `drive`.
2. Get the token with `rclone authorize drive <id> <secret>` on my computer and put it in the phone's config.
3. Write ~/bin/phonesync.sh: `rclone bisync <folder> gdrive:` with an explicit --config, a lock directory against overlapping
   runs, abort if over 50% of files would be deleted, conflicts keep both versions, skip Google-native docs, log only failures,
   and a one-time `--resync --resync-mode newer` when the baseline is empty. Schedule it from crond every 30 minutes and start
   crond from the boot script.
4. Test: create, rename, delete on the phone and on Drive; edit the same file on both sides; compare checksums with `rclone check`.
   Delete only named test files, never run delete or purge on the whole remote.

Done when: both directions work, a conflict keeps both versions, and the cron job ran unattended once. Stop.
```

## 7. Build the file manager

```text
Task 7: build a small, fast, offline file manager app ("Files"), Kotlin, plain Android Views, minSdk 29, targetSdk 29, no
network permission, no heavy dependencies. Put it in files-app/ (Gradle, package of your choice).

Features: browse with thumbnails, sort, hidden-file toggle, storage bar per volume; multi-select copy, move, rename, delete,
new folder, properties, share; long operations in a foreground service with progress and cancel; filename search (this folder or
whole drive, recursive, cancellable); viewers for image (pinch zoom), video, audio, PDF (PdfRenderer), text (editable, large files
read-only), docx and xlsx (rendered to HTML in a WebView with JavaScript and file access off), and zip (browse, open, extract,
compress); removable USB/SD volumes with one persisted folder grant per volume (Android 10 rule).

Architecture: a FileRef value type, an FsProvider interface with a local and a SAF implementation behind a router, an
OperationEngine of cancellable coroutines, pure renderers. Keep logic in pure classes and write JVM unit tests (JUnit 4). Check zip
entries for path traversal. Install it with tools/install-apk.sh and verify every feature on the phone with screenshots
(`adb exec-out screencap -p`), including a 5000-file folder (note memory).

Done when: tests pass, every feature is verified on the phone, and limits (legacy doc/xls, 7z/rar, background audio) are listed.
Stop.
```

## 8. Build the launcher

```text
Task 8: build a custom launcher ("Grand Line"), Kotlin, plain Views, minSdk 29, targetSdk 29, in grandline-app/. Read
docs/ARCHITECTURE.md; it describes the design that worked.

Core: home pages in a pager (5 x 4 grid), a 4-slot dock, a swipe-up drawer with live search and an alphabetical list,
long-press menu (remove or add to home, app info, uninstall for user apps, hide), native drag and drop between home, dock and a
remove zone, a settings screen built in code (page count, add-new-apps toggle, clear home, wallpapers per page, hidden apps,
switch launcher). The layout is an immutable model with pure operations and a reconcile function, saved as JSON; an empty
app list from the package manager must never overwrite it. The home screen is EMPTY by default (all apps live in the drawer):
no auto-placing, an empty dock fades out.
Declare only the HOME and DEFAULT categories (not LAUNCHER) and excludeFromRecents. Use a transparent navigation bar with the
wallpaper behind it. Theme: a palette object, a bundled OFL title font, drawn fallback art for anything missing.

Make it the default home with `cmd package set-home-activity`, test a reboot, and document the rollback command. Write unit tests
for the layout model, reconcile and app filter.

Done when: it survives a reboot as the default home, recents still works, tests pass, cold start and memory are measured and
reported. Keep the previous launcher installed as the fallback. Stop.
```

## 9. Theme assets pipeline

```text
Task 9: art loading and the asset pipeline.

The apps read the user's own images from /sdcard/GrandLine/{posters,marks,icons,wallpapers}/ and draw a code fallback when a
file is missing. Implement an Art loader (decode with inSampleSize, cap the cache, never crash on a bad file).
Write tools/: make-posters.py (large wanted-poster PNGs to 720 px JPEG posters and 360 px transparent marks), make-icons.py
(remove a baked-in grey checkerboard from generated icon PNGs by flood fill from the border, keep the largest shape, resize to 256 px),
encode-wallpaper.sh (ffmpeg: no audio, 30 fps, at most 720 px wide, H.264 main, CRF 31), and push-assets.sh.

Use only images I put in theme-assets/. Do not download any art. Add the art folders to .gitignore and document the file names
and sizes in theme-assets/README.md.

Done when: the pipeline turns my sources into the pushed files and the apps show them. Stop.
```

## 10. Crew panel, live wallpaper and poster lock screen

```text
Task 10: the visual features.

a) Drawer panel at the top of the drawer, open by default: poster of the day (a fixed list of 10 characters, index = epochDay mod 10,
   shared by both apps), big clock, date, name. Tapping the poster or clock folds it into a pill (time, temperature, name); it re-opens
   when the drawer closes.
b) Video wallpapers: a TextureView with one muted looping MediaPlayer, cover-cropped, over a still ImageView (a TextureView cannot have
   a background drawable). Stop playback when paused, when the drawer opens and when the screen turns off. Long-press empty home opens
   a thumbnail picker for images and videos. Keep the system wallpaper in sync with a still of the current one so no vendor default
   flashes behind stock recents.
c) Poster lock screen: an accessibility service that shows an overlay (TYPE_ACCESSIBILITY_OVERLAY, covering the status and navigation
   bars) on every screen-off, with a random poster that is never the previous one, the time, and swipe-up to open. Slide only the inner
   content; do not animate the window. It is decoration only (no PIN); say so in the settings text.
   The launcher must re-enable its own service on start (WRITE_SECURE_SETTINGS granted by adb) because a force-stop disables it.
   The system caches the service config: after editing it, reinstall, force-stop and restart the launcher. Do not delete and re-put the
   enabled_accessibility_services setting while the app runs (the service can get stuck unbound). The launcher must also remove the stock
   accessibility menu from that list, in both its full and short component spelling, or it keeps the nav-bar button.
d) The accessibility-button menu: request the navigation-bar button and show a small menu of drawn gold line icons with names
   (power, recents, lock, notifications, quick settings, screenshot, accessibility) using performGlobalAction, replacing the stock
   menu service. Close it before acting; time out after 8 seconds.

Do NOT build a custom recents screen or a live wallpaper service for the recents background: the stock recents draws a static
snapshot and cannot show it. (docs/TROUBLESHOOTING.md has the reasons.)

Verify each on the phone with screenshots. Done when: all four work, a reboot keeps the lock service alive, tests pass. Stop.
```

## 11. Weather

```text
Task 11: weather card inside the drawer panel.

Use Open-Meteo (forecast and geocoding, no API key, INTERNET permission). The user types a city once (tap the card; long-press to
change); never guess a location. Show a drawn sky glyph, temperature, condition, a three-day outlook, feels-like, humidity, wind, the
city and one themed flavour line. Cache the JSON for 30 minutes in preferences; offline keeps the old data. Keep parsing in a pure
class with JVM tests (every weather code group, night and day, malformed JSON).

Done when: a city can be set and changed, offline works, tests pass. Stop.
```

## 12. Launcher keeps Termux alive

```text
Task 12: make the launcher start the Termux services itself.

On launcher start (once per process) send Termux a com.termux.RUN_COMMAND intent for ~/bin/ensure-services.sh (wake lock, crond if not
running). Needs allow-external-apps = true in termux.properties and `pm grant <launcher> com.termux.permission.RUN_COMMAND`
(tools/grant-launcher-permissions.sh). Test from a fully stopped Termux with no crond running.

Done when: after a reboot, crond and the wake lock are present without opening Termux. Stop.
```

## 13. Themed app icons and hiding apps

```text
Task 13: per-app icons and a clean drawer.

Map package names to files in /sdcard/GrandLine/icons/ in an IconProvider; apps without a file get their own icon in a gold-ringed rounded
square. Hide (not remove) apps the system needs but I never want to see (downloads UI, the vendor security app, a duplicate file manager,
the fallback launcher), once, remembering which were hidden by default so Unhide in settings sticks.

Done when: the drawer shows only what I want, with themed icons, and Unhide works. Stop.
```

## 14. Verify, document, hand over

```text
Task 14: final pass.

Run every unit test and build both apps. Walk through each feature on the phone with screenshots and list anything you did not check.
Reboot once and confirm the launcher, lock service, Termux boot and sync survive. Update CLAUDE.md, README.md and docs/ with anything that
changed, with no personal data, secrets or serial numbers. Give me the git commands to commit, but do not run them.

Done when: the checklist is written, failures are stated plainly, and docs match the code. Stop.
```

---

## Optional: on-device speech and decision pipeline (not built)

Paused in the original project. See `docs/ROADMAP.md` for the plan and kill criteria. Use one prompt per stage and measure on the
real phone before building the next.

```text
Task 15 (optional, speech to text): choose and install an offline speech-to-text engine for this phone's ABI (candidates: Vosk small model for
streaming, whisper.cpp tiny/base quantised for voice notes, built from source). Record five short clips, measure real-time factor, word
error, peak RAM and temperature for each engine, pick one, and write the numbers down. Convert opus/ogg with ffmpeg to 16 kHz wav.
```

```text
Task 16 (optional, decision model spike): prove or kill running a small ONNX decision model on this phone. Find a runtime that works for this
ABI (build onnxruntime in Termux, or a tiny Android app with onnxruntime-android). Produce one decision, then 20 repeated calls with median and
p95 latency and a meminfo snapshot. Kill criterion: peak RAM over about 1.3 GB or p95 over about 5 s means this model runs off the phone.
```

```text
Task 17 (optional, text to speech and pipeline): tier 1 termux-tts-speak, tier 2 espeak-ng, tier 3 a Piper voice only if those sound bad. Then wire
Telegram voice note, speech to text, decision, action, reply, with a closed action set I approve first, an allow-list of my user id, and a
clear error message when any stage fails.
```

## Handy one-off prompts

```text
Add an icon for <app>: I put <file>.png in theme-assets/icons. Run it through the icon script if it has a baked-in checkerboard, add the package
to the themed map in MedallionIconProvider, push assets, install, and show me a screenshot of the drawer.
```

```text
Hide <app> from the drawer by default without uninstalling it. Add it to the hidden-by-default set (once, so Unhide sticks) and verify.
```

```text
Something broke. Check docs/TROUBLESHOOTING.md first, then diagnose with adb logcat and dumpsys before changing anything. Tell me the cause
before you fix it.
```
