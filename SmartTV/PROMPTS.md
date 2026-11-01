# PROMPTS.md: prompts for an AI agent

Copy-paste prompts that take an AI coding agent from "a phone cable and a TV" to a finished, reversible setup. They
assume nothing about the TV: the agent finds out what it is. Run them in order, one at a time, and read the result
before saying go to the next. `CLAUDE.md` holds the standing rules; point the agent at it first.

## 0. Opening block (paste once at the start of every session)

```
You are working in this repository to debloat and restyle an Android TV that I will connect over adb.
Read CLAUDE.md first, then README.md and docs/. Follow CLAUDE.md's rules exactly, especially the safety
rules and the gated workflow: do one task, verify it with real commands, record what you decided in CLAUDE.md (section 6),
report what you changed and how you checked it, then stop and wait for me to say "Go for Task n".

I will turn on USB debugging (or network debugging) on the device and connect it. Find everything out yourself:
the model, Android version, CPU architecture, RAM, storage, screen size, installed packages, which launcher is
Home, how the remote's keys arrive. Do not ask me for facts you can read with adb. Ask me only for decisions that
are mine (what to remove, what to keep, what it should look like).

Do not use git. Do not add AI attribution anywhere. Keep secrets, IPs and device identifiers out of any file that
could be shared. Never open image files unless I ask for that specific file; if you must see one, downscale it
first.
```

## 1. Connect and survey

```
Task 1: connect and survey. Check that adb works and the device is authorised (`adb devices`). If the device is
over the network, find its address and `adb connect` to it; if it is on USB, offer to switch it to TCP so it keeps
working from other machines. Read, without changing anything: model, Android and security patch level, ABI list,
RAM and whether it is a low-RAM build, /data size and free space, display size and density, the current Home app,
and every installed package with its enabled/disabled state. Check whether `adb shell` runs as the shell user and
what it can and cannot write. Install or verify the laptop toolchain this repo needs (JDK 17, Android SDK platform
34 and build-tools 34, platform-tools, a Python venv from tools/requirements.txt). Write the device facts into
CLAUDE.md under "Reference device", without addresses or serial numbers. Done when `tools/tv.py --probe` connects
and the facts are recorded.
```

## 2. Backup and undo first

```
Task 2: back up and write the undo before any change. Save to backup/: package lists (all, user, system, enabled,
disabled), getprop, global/secure/system settings, the Home candidates, and the APK of every app I might remove.
Write restore-tv.ps1: one command that finds the device, replays the recovery records for each applied batch newest
first, reinstalls removed apps from backup/apks, restores Private DNS, the accessibility list and the CPU governor,
and has a -DryRun mode. Run the dry run and show me the plan. Done when backup/ is complete and the dry run prints a
sane plan.
```

## 3. Learn the remote

```
Task 3: capture the remote. With `getevent -lt` running, ask me to press each remote button once and name it. Record
for each button the input device, scan code and Android key code in work/remote-keys.md. Note which keys an app can
see, which the firmware intercepts, and which cannot be remapped. No changes to the device.
```

## 4. Debloat list

```
Task 4: propose a debloat list. Compare the installed packages against reference/example-debloat/debloat.txt and any
list you can justify from package names, requested permissions and what each one actually does (use
`dumpsys package <name>`, never launch a package to find out). Write work/debloat.txt with a header explaining the
rules and one comment per group. Mark clearly what stays: the Android core, the TV middleware, HDMI/tuner/input
services, the vendor key and input services, Bluetooth, USB and storage. Show me the list and the keep list and wait
for my edits. I decide what is cut.
```

## 5. Apply debloat

```
Task 5: apply the approved list in small batches (work/batches/b1.txt ...). For each batch: disable with
`pm disable-user --user 0`, one package per call; wait; check Home, the Settings overlay and the Input button;
check logcat for crashes. Then reboot once and compare `pm list packages -d` with the plan. Uninstall only the apps I
named explicitly. Fix anything that breaks (re-enable the package, clear app data if needed) and note it in
CLAUDE.md (section 6). Done when the device survives a reboot and every kept app launches.
```

## 6. Launcher

```
Task 6: install the launcher in launcher-fork. Build it offline (`gradlew --no-daemon --offline`), install it, make it
Home, and confirm it with `dumpsys activity activities | grep mFocusedApp`. Only then disable the stock launcher.
Map the remote buttons from Task 3. Done when Home works from five different apps and after a reboot, Inputs works,
the D-pad reaches every edge, and holding volume still repeats. Anything handled by the accessibility helper must be
confirmed by me pressing the real remote, not by an injected key.
```

## 7. Wallpapers and lock screen

```
Task 7: wallpapers. Rename my wallpaper images to w01..wNN with a script (never open the images), downscale copies to
the panel size into work/theme/wallpapers/, push them, and set a daily shuffle.

Task 8: lock screen. The launcher fork must show a wallpaper with a centred clock and date at boot, on every wake
from standby, and after a few idle minutes on Home; any key lifts it with a shutter animation; a new wallpaper each
time. If a song is playing it shows a song card and the D-pad keys control the song. Benchmark Home with
tools/bench.py before and after; cut anything that makes Home janky. Done when it works from the real remote.
```

## 8. Add-ons and apps

```
Task 9: build and install the add-ons in addons-local (capacity-stats, weather, brightness). Weather should list my
cities and remember one. Brightness should have a night schedule. Tell me which add-on prompts I must answer on the
TV.

Task 10: install the apps I name. Prefer the Play Store. For anything sideloaded, take the exact release matching the
device's ABI, verify the SHA-256 against the publisher's digest and the signing certificate against a second
source, and record both in CLAUDE.md (section 6) before installing. Tell me which apps I must sign in to.
```

## 9. Ad blocking, speed, checks

```
Task 11: Private DNS. Set an ad-blocking DNS-over-TLS provider (default: dns.adguard-dns.com), confirm the network
is still validated and that a known ad domain is blocked while normal names resolve. Measure lookup latency with and
without it. Document the one-line revert and the failure mode (a network that blocks port 853).

Task 12: performance. Compile every kept app ahead of time (`cmd package compile -m speed -f <pkg>`), measure cold
starts before and after, check CPU governors and try the performance governor (it may be reset by a vendor daemon;
if so, use a keep-alive loop and make restore-tv.ps1 stop it), look at GPU/MSAA settings, check free RAM, and ask me to
test a USB drive with the video player, Bluetooth headphones, and the screensaver. Done when each check has a number
or my confirmation.
```

## 10. Phone remote

```
Task 13: voice spike. Find out whether a third-party app can read audio from the remote's microphone button (log
what happens at the key press, then try an AudioRecord from a throw-away app). If it cannot, use the phone as the
microphone and say so in CLAUDE.md (section 6). Remove the throw-away app afterwards.

Task 14: phone remote. Build phone-remote/: a stdlib-only Python server for Termux that serves one web page and runs
adb commands; the page has hold-to-talk using the browser's speech recognition, a D-pad, volume, media, an app grid
and a typed box with a drop-down of stored commands. Put the command rules in commands.py as tables (apps, keys),
generate COMMANDS.md from them, and test the parser. Make the server find the TV again when its address changes.
Measure the latency of an adb command and use the fastest working key path. Lock it down: loopback only, a custom
header, host and origin checks, validated package names. Test it from the laptop first, then on the phone.

Task 15: policy and autostart. Autostart the server at phone boot (Termux:Boot), write down the ADB policy and its
risk, make restore-tv.ps1 undo everything that was added to the TV, write phone-remote/README.md, stop any test
servers, and check the device one last time (crashes, accessibility helper, DNS, RAM).
```

## 11. Documentation and release

```
Final task: documentation. Make sure README.md, CLAUDE.md and docs/ match what is on the device and in
the repo. Check that no IP address, MAC address, serial number, account name or private key appears in any file that
would be published, and list every private or third-party file that must stay out (backup/, downloaded APKs,
wallpapers). Run every unit test. Report what is left.
```

## Resuming in a new session

```
Read CLAUDE.md (especially section 6 and the task table) and README.md. Then run the quick state check from CLAUDE.md
(focused window, accessibility helper, disabled count, crash count, Private DNS, CPU governor) and tell me in five
lines where we are. Do not change anything until I name the next task.
```

## Useful one-off prompts

- **Undo everything:** "Run restore-tv.ps1 with -DryRun, show me the plan, and on my yes run it for real and reboot the
  device."
- **Something broke:** "Home or an app misbehaves after the last change. Use logcat and dumpsys to find the cause, revert
  only that change, and record what happened in CLAUDE.md (section 6)."
- **Add a voice command:** "Add `<phrase>` to the tables in phone-remote/commands.py, regenerate COMMANDS.md, add a test,
  and tell me how to update the phone."
- **Change the look:** "Change `<setting>` in work/theme/config.json, push it with work/glow.ps1, and check Home."
- **Port to another TV:** "Follow docs/PORTING.md for this device and list every constant that must change before you
  touch anything."
