# SETUP: reproduce the whole project by hand

Windows 10/11 with PowerShell is what the scripts were written and tested on. Git Bash is used for some commands.
Allow about two hours the first time. Do the phases in order and stop at each check.

## 0. What you need

| Item | Notes |
|---|---|
| Android TV, Android 9 or newer | Developer options and (network) debugging available. 32-bit devices need armeabi-v7a apps. |
| Laptop on the same network | A phone hotspot works but its IPv6/NAT64 network is flaky: download big files with resumable `curl -C -`. |
| JDK 17 | Any 17 build (the project used Microsoft's). Set `JAVA_HOME`. |
| Android SDK | platform 34, build-tools 34.0.0, cmdline-tools, platform-tools. Set `ANDROID_HOME`. |
| Python 3 | For `tools/venv` (adb-shell, rsa, cryptography) and `phone-remote` tests. |
| ffmpeg | Only to downscale wallpapers and screenshots. |
| A phone (optional) | Android with Termux, Termux:Boot and Chrome, for the phone remote. |

`adb` here lives at `C:\platform-tools\adb.exe` and is not on PATH; scripts call it by full path.

## 1. Toolchain (task T1)

1. Install the JDK and SDK, then `sdkmanager "platforms;android-34" "build-tools;34.0.0"`.
2. Create the venv and install the pinned packages:
   ```
   python -m venv tools\venv
   tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
   ```
3. The first Gradle run downloads Gradle 8.9. If the network is flaky, fetch it with `curl -C -` and verify its
   SHA-256 against the value published by Gradle, then put it in the wrapper cache. After that always build with
   `--offline`.
4. Check: `java -version` shows 17, `sdkmanager --list_installed` lists both, and the TV connects (next step).

## 2. Connect to the TV

1. On the TV: Settings, About, tap the build number seven times; then Developer options, turn on USB debugging and
   (if offered) network debugging. On most Android TV builds network debugging listens on port 5555.
2. Find its address (router page, or `arp -a` and match the TV's MAC). The address changes with each network.
   The scripts take `-TvHost <ip>` or `TV_HOST`; set `TV_MAC` (aa-bb-cc-dd-ee-ff) once and they find the TV through ARP.
3. `adb connect <tv ip>:5555`. Accept "Allow USB debugging?" on the TV and tick Always allow. The authorisation stays
   when the address changes.
4. `set TV_HOST=<tv ip>` and run `tools\venv\Scripts\python.exe -I tools\tv.py --probe`.

`tools/tv.py` is a small adb client in Python (no adb binary needed). `tv.py --batch` reads commands from stdin, which
is the way to send strings that PowerShell would mangle.

## 3. Back up (task T2)

Save, from the TV, into `backup/`: `pm list packages` in each variant (all, `-3`, `-s`, `-e`, `-d`), `getprop`,
`settings list global|secure|system`, the Home candidates, and `pull` the APK of every app you may uninstall. Then
check `.\restore-tv.ps1 -DryRun` prints a sane plan. Do this before any change.

## 4. Debloat (tasks T4, T5)

1. `work/debloat.txt` is the master list with a header explaining the rules; `work/debloat-pass2.txt` is a short
   second pass. Compare with `reference/example-debloat/debloat.txt` for the reference device's equivalent.
2. Edit the list for your device and your taste. Never add: Android core, TV middleware, HDMI/tuner/input services,
   vendor key and remote services, Bluetooth, USB, storage (full list in [CLAUDE.md](../CLAUDE.md)).
3. Split the list into batches in `work/batches/b1.txt ...`. Apply one batch at a time:
   `tools\disable.ps1 -TvHost <ip> work\batches\b1.txt`. After each batch check Home, the Settings overlay and the
   Input button, then `logcat -d -b crash`.
4. Reboot once and compare `pm list packages -d` with the plan (the reference device kept 34 disabled).
5. Uninstalling is only for apps you name; the APK is in `backup/apks` first.

## 5. Launcher (task T6)

```
cd launcher-fork
cmd /c ".\gradlew.bat --no-daemon --offline --console=plain testReleaseUnitTest assembleRelease 2>&1"
adb -s <ip>:5555 install -r app\build\outputs\apk\release\app-release.apk
adb -s <ip>:5555 shell pm grant com.gathin.tvhome android.permission.DUMP
..\work\glow.ps1 none
```

Or use `launcher-fork\build-and-install.ps1`, which also makes it Home and disables the stock launcher with a
confirmation step. Confirm it is Home with `dumpsys activity activities | grep mFocusedApp` before disabling the stock
launcher. Then test from five different apps and after a reboot. Details: [LAUNCHER.md](LAUNCHER.md).

## 6. Look (tasks T7, T8)

- Wallpapers: `work\wallpapers.ps1` renames the sources to `w01..` and writes 720p copies to `work\theme\wallpapers`.
  Push everything with `tools\theme.ps1 -TvHost <ip> work\theme` (slow), or only the config with `work\glow.ps1`.
- The lock screen is part of the launcher; its behaviour is controlled by `work/theme/config.json` (see LAUNCHER.md).

## 7. Add-ons and apps (tasks T9, T10)

- Add-ons: each folder in `addons-local` is an Android project. Build with its `gradlew.bat --no-daemon --offline
  assembleRelease`, push the APK with `tools\b64push.py`, `pm install -r`. Open the TV's launcher settings and answer
  "Show card" for each.
- Apps: use the Play Store when it works. A sideloaded app must match the device ABI; verify the SHA-256 and the
  signing certificate before installing and record both in `CLAUDE.md` (section 6).

## 8. Private DNS (task T11)

```
adb shell settings put global private_dns_specifier dns.adguard-dns.com
adb shell settings put global private_dns_mode hostname
```
Check `dumpsys connectivity` still says VALIDATED and `ping doubleclick.net` returns `127.0.0.1` or `0.0.0.0`.
Revert: `private_dns_mode opportunistic`. If the network blocks port 853 every lookup fails: revert first.

## 9. Speed (task T12)

```
for each kept package:  adb shell cmd package compile -m speed -f <package>
.\work\cpu-fast.ps1 on        (and again after every reboot)
```
Compile takes 30 to 280 seconds per app and about 500 MB for eight apps; repeat after app updates.
Measure Home with `tools\bench.py --keys 36 --series 3` (it takes about 9 minutes).

## 10. Phone remote (tasks T13 to T15)

See [../phone-remote/README.md](../phone-remote/README.md). In short: Termux and Termux:Boot from F-Droid, serve the
folder from the laptop, run `setup.sh` in Termux, open `http://localhost:8765` in Chrome, accept the adb prompt on the
TV once.

## Final check

```
adb shell dumpsys window | grep mCurrentFocus              the launcher
adb shell settings get secure enabled_accessibility_services   contains com.gathin.tvhome/.HomeKeyService
adb shell pm list packages -d                              the disabled list
adb shell logcat -d -b crash | grep -c "Process:"          0
adb shell settings get global private_dns_mode             hostname
```
