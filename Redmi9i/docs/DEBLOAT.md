# Debloating without root

Removes vendor, Google and carrier apps for the current user only. It frees RAM and battery and removes ads. It does
not free storage: `pm uninstall -k` keeps the APK in the system partition. Everything here is reversible until you
factory-reset.

## The method

1. **Baseline first.** Save `pm list packages -f`, `pm list packages -d` (disabled), `settings list global|secure|system`,
   the default home activity and `getprop` into `backup/`. The `backup/` folder holds device-identifying data
   (serials, account hints), so it is git-ignored. Expected result: the package count matches `adb shell pm list packages`.
2. **Triage** every system package into `debloat/packages.csv` with columns `package, batch, verdict, reason`
   (verdict `remove`, `keep` or `unsure`). Look for second users (`pm list users`): a vendor "second space" is a separate
   user, and removals here use `--user 0`.
3. **Apply a batch** of about 10 to 15 packages:
   `adb shell pm uninstall -k --user 0 <package>`. `debloat/apply-batch.ps1 -Batch N` does it from the CSV and appends to
   `debloat/applied.log`. Android persists package state roughly 10 seconds after the last change, so wait about 20
   seconds before rebooting (the script does).
4. **Reboot and confirm** boot (`debloat/test-batch.ps1 -Batch N` applies, reboots and waits for `sys.boot_completed`).
   Then check calls, SMS, Wi-Fi, mobile data and the camera by hand.
5. **Restore** any package with `adb shell cmd package install-existing --user 0 <package>`, or everything in the log
   with `debloat/restore.ps1`. The scripts are PowerShell; the underlying commands are plain adb.

A disabled app (`pm disable-user --user 0 <pkg>`, undo with `pm enable --user 0 <pkg>`) is invisible in the launcher and
survives reboot. `pm hide` is not available to the shell (needs `MANAGE_USERS`). Use disable for packages that refuse to
uninstall (`Failure [-1000]`).

## What goes wrong

On the reference phone, removing 16 Google and vendor apps in two batches at once caused a **boot loop into the
vendor recovery**. The only recovery offered was Wipe Data, which also erased shared storage and every installed app.
Cause found by removing one at a time: the vendor "find device" service is boot-critical (removing it crash-loops
`system_server`). Later, the downloads UI provider did the same.

**Rescue that avoided a second wipe:** while the phone is looping but adb is still alive, run in a loop
`adb shell cmd package install-existing --user 0 <package>` every 0.7 seconds until it succeeds (it worked on the 29th
try), starting the loop before tapping Reboot in recovery.

## Keep list on the reference phone (and why)

| Package | Why it must stay |
| --- | --- |
| `com.xiaomi.finddevice` | Boot-critical. Removing it loops the phone into recovery |
| `com.android.providers.downloads.ui` | Removing it also boot-looped. Its Downloads icon cannot go; hide it in the launcher |
| `com.miui.guardprovider` | The vendor install-confirmation dialog crashes without it; every `adb install` then fails with `INSTALL_FAILED_USER_RESTRICTED` |
| `com.miui.securitycenter` | Holds Autostart and battery settings that Termux and Termux:Boot need; also the install check |
| `com.android.contacts` | The skin re-enables it on every boot |
| Dialer, telephony, SMS stack, SystemUI, Settings, WebView, package installer, keyboard, connectivity | Core |
| Mi Cloud (3), `google.android.feedback`, `xiaomi.mircs` | Left alone; removal gains little |
| `com.miui.home` | Not disabled. On Android 10 recents and navigation belong to the vendor launcher component; disabling it risks a SystemUI crash loop. A third-party launcher works alongside it |

Other phones will have a different list. Find yours the same way: one package per reboot for anything with "service",
"provider" or "framework" in its name, and keep the rescue loop ready.

## Result on the reference phone

270 packages down to 204, 6 user-installed apps (Termux x3, Brave, Lawnchair, later Telegram, Files, Grand Line). Removed:
store and ad apps, Facebook preinstalls, WPS and games, YouTube, Maps, Gmail, Pay, One, Lens, Wellbeing, Device Health,
Android Auto, Notes, Google app, Cleaner, Assistant, Gallery, Chrome (after Brave was installed), Calculator, recorders,
Calendars, SIM toolkit, Clock, Themes. The full list with reasons is `debloat/packages.csv`; what was applied is
`debloat/applied.log`.

**Play Store auto-installs:** with a Google account signed in, account restore and Play Auto Install pushed games onto the
phone. Fix: remove `com.google.android.partnersetup` and `com.google.android.apps.restore` and disable the Play Store
(`pm disable-user --user 0 com.android.vending`; undo with `pm enable --user 0 com.android.vending`). Google Pay
reappeared after the wipe cycles and is disabled.

## Other tweaks that stuck

- Lock screen disabled: `adb shell locksettings set-disabled true` (works only while no PIN is set).
- The "cover the sensor" popup that appears on screen-on is a vendor window; `settings put global
  enable_screen_on_proximity_sensor 0` disables it.
- Install a replacement launcher before changing the default home. Never disable the vendor home first: no launcher
  means no home screen.
- Installing apps over adb needs "Install via USB" turned on in Developer options (some vendors require signing in to
  an account for that toggle; do it before removing the SIM).

## Launcher swap notes (before the custom launcher)

The first replacement home app was Lawnchair 15 (Beta) with Brave as the browser; both stay installed (Lawnchair is the fallback
launcher). Lawnchair became the default home and survived a reboot; recents kept opening the vendor's SystemUI recents. Rollback
at any time: `adb shell cmd package set-home-activity <vendor-home-package>/<its launcher activity>`. Lawnchair's dock search bar
was disabled (Home settings, Dock, Search bar). If you want an icon pack for Lawnchair, Lawnicons (Apache-2.0) is the closest
open-source fit. Making Brave the default browser is done by hand in Settings. The custom launcher later replaced all of this
(`docs/ARCHITECTURE.md`).

