# CLAUDE.md: operating guide for AI agents working on this project

Read this first. It replaces the earlier TASK, ARCHITECTURE and CONTEXT notes. Companion files: `README.md` (what and
status), `PROMPTS.md` (the prompts that drive each phase), `docs/` (SETUP,
LAUNCHER, RESTORE, SECURITY, TROUBLESHOOTING, PORTING). A user's own global CLAUDE.md still applies on top of this one.

## 1. Mission

Debloat and restyle a low-end Android TV over adb, reversibly: disable vendor junk, install a glass-look launcher with
a lock screen, wallpapers, add-ons, ad-blocking Private DNS, ahead-of-time compiled apps, and a phone that works as a
voice and button remote. Everything must be undone by one command (`restore-tv.ps1`). Nothing is rooted or flashed.

## 2. How to work here

- **Gated workflow.** Do one task from section 8 at a time: implement, verify with real commands, document, hand over,
  stop. Start the next only when the user says "Go for Task n". Never fold tasks together or start ahead.
- **Real decisions go to the user.** Use the question tool for choices that are the user's (what to remove or keep, how
  something should look, which trade-off). Otherwise decide, recommend, and log it.
- **Record decisions when they happen** in section 6 of this file (or the matching `docs/` page): decision,
  alternatives, why (evidence), consequences. Update this file when structure or status changes. Put what changed and how it was verified in the
  hand-over message; there is no separate explanation file.
- **Evidence over assertion.** Do not claim a result without a command that shows it. For anything the accessibility
  helper or the firmware handles (Home, Settings button, Input, lock keys), ask the user to press the real remote;
  an injected key is not proof.
- **Measure, then keep.** Every extra feature is benchmarked (`tools/bench.py`, `dumpsys meminfo`, free RAM) before and
  after. Cut or simplify anything that makes Home janky or free RAM low.
- **No git.** This folder is not a repository for the agent's purposes: do not commit, branch, push or tag. Never add
  `Co-Authored-By` or any AI attribution anywhere.
- **No secrets in shareable files.** No IP or MAC addresses, serial numbers, account names, keys or tokens in any
  document or script that could be published. Use `<tv ip>` style placeholders.
- **Tokens and images.** Never open an image unless the user asks for that specific file. If you must see the screen,
  take a screenshot, downscale it with ffmpeg to 480 to 640 px and crop. Never open `TV-Wallpapers/`,
  `reference/theme-glass/` or `work/theme/wallpapers/`. Prefer Grep and Glob, read only the lines you need.
- **Long commands.** The user interrupts slow or repeated calls. Prefer the fast paths in section 9, say what a slow
  command is for, run it in the background, and never run two builds at once.

## 3. Safety rules (not negotiable)

These come from the upstream toolkit's safety rules (`reference/UPSTREAM-AGENTS.md`) and from incidents here.

1. **Back up before the first change**: package lists, disabled list, `getprop`, settings, APKs of anything to remove.
2. **Disable, do not uninstall**: `pm disable-user --user 0 <pkg>`, one package per shell call, in small batches.
   Uninstall only apps the user names. After each batch check Home, the Settings overlay and the Input button, then
   `logcat -d -b crash`. After the last batch wait 15 s, reboot once, compare `pm list packages -d` with the plan.
3. **Never disable**: `android`, `com.android.systemui`, `com.android.settings`, `com.android.tv.settings`,
   `com.android.providers.*`, the permission controller, package installer, ext services and shared libraries,
   `com.android.shell`, `com.android.phone`; the TV middleware (`com.cltv.mal`, `com.mediatek.tis`,
   `com.mediatek.tv.*`); and the vendor key, input and source services (on the reference device
   `com.tcl.keycustomfunctionservice`, `com.tcl.tv`, `tvinput`, `tvpassthrough`, `sourcemananger`,
   `tcl_bt_rcu_service`). `tools/disable.*` refuses such lists.
4. **Never launch** a package named factory, production, AT or fac menu, not even to look. Use `dumpsys package <name>`.
   (`com.tcl.factory.view` on the reference device.)
5. **Home guard**: disable the stock launcher only after the new one is installed and
   `dumpsys activity activities | grep mFocusedApp` shows it. Never override with `FORCE_HOME` or `FORCE_SETUPWRAITH`
   without the user.
6. **Do not touch the device outside the current task's gate.**
7. **Keep stock YouTube enabled** until the user says otherwise (they have Premium).
8. **ADB stays on** by the user's decision (phone remote). Treat the accepted keys (laptop, phone) as secrets.
9. Sideloaded apps: match the ABI (`armeabi-v7a`), verify SHA-256 against the publisher's digest and the signing
   certificate against a second source, record both in section 6 of this file first.

## 4. Reference device

Read it again over adb when you start; do not trust this table for a different device (see `docs/PORTING.md`).

| Item | Value |
|---|---|
| Model | TCL UnionTV, Android 9 (API 28), Realtek rtd284x, 32-bit ARM only (`armeabi-v7a`) |
| CPU, RAM, storage | 4 x Cortex-A53 at 700 to 900 MHz; 1.4 GB, `low_ram=true`; `/data` 4.2 GB (about 1.4 GB free at the end) |
| Panel | 1280 x 720 at 213 dpi |
| Client type | `TCL-AP-RT41DT-S1`; closest upstream example `reference/example-debloat` |
| Network | the TV's address changes with every network. Find it: `arp -a` (match its MAC), probe port 5555, `adb connect <ip>:5555`; authorisation survives. The laptop scripts take `-TvHost <ip>` or `TV_HOST`, or `-TvMac`/`TV_MAC` to find it through ARP. |
| adb | `C:\platform-tools\adb.exe`, not on PATH |
| Remote | IR keys through an input device called `mtkinp`, voice over Bluetooth; key map in `work/remote-keys.md` |
| Companion | an Android phone running Termux (the reference phone is 32-bit Android 10 on MIUI) |

Remote keys in one paragraph: the standard keys (D-pad, OK, Back, Home, List = MENU, Input = TV_INPUT, volume, mute)
reach apps; the vendor keys ("T" = 4020, keyboard-dots = 4056 dead, Settings = 4077 overlay, Netflix = 4062,
App-grid = 4082, Mic = 4048) are intercepted by the vendor key service and cannot be remapped. App-grid and "T" are dead
by design once the stock launcher is disabled.

## 5. Architecture

| Component | Where | Role |
|---|---|---|
| Launcher fork | `launcher-fork/` (`com.gathin.tvhome`) | glass Home, dock, Inputs and Wi-Fi pills, now-playing card, clock screensaver, lock screen, accessibility helper. Details: `docs/LAUNCHER.md` |
| Add-ons | `addons-local/{weather,brightness,capacity-stats}` | module cards on Home; weather forked with an Indian city list |
| Laptop tools | `tools/` | upstream scripts unchanged: `tv.py` (adb client), `b64push.py`, `disable.ps1`, `restore.ps1`, `recovery.py`, `theme.ps1`, `theme_restart.py`, `bench.py`, `venv/` |
| Debloat | `work/debloat.txt` (master), `work/debloat-pass2.txt`, `work/batches/b1..b4` | packages disabled; recovery records in `~/.cleanyourtv/` |
| Backup and undo | `backup/`, `restore-tv.ps1` | pre-change state, removed apps' APKs, one-command undo |
| Theme and wallpapers | `work/theme/` (config, 720p `w01..w20`), `TV-Wallpapers/` (sources), `work/glow.ps1`, `work/wallpapers.ps1` | pushed config and daily shuffle |
| CPU speed-up | `work/cpu-fast.ps1`, `work/cpu-hold.sh` | keep-alive loop on the TV holding the `performance` governor |
| Private DNS | TV setting `dns.adguard-dns.com` | tracker and ad domains |
| Phone remote | `phone-remote/` | Termux server, Chrome page, command rules (`COMMANDS.md`) |
| Reference | `reference/` | upstream MIT licence and safety rules, glass theme, example debloat list |

Data flow:

```text
laptop --adb(<tv ip>:5555)--> TV     debloat, installs, themes, benchmarks, CPU loop
phone Chrome page: hold to talk -> Google speech -> text -> localhost server (Termux) -> adb -> TV adbd
TV apps --DoT 853--> dns.adguard-dns.com
```

Invariants:

- Every change to the TV is undone by `restore-tv.ps1`; the backup exists before the first change.
- The launcher has no `INTERNET` permission; network code lives off the TV.
- The phone server listens on loopback only and checks a custom header, the Host and the Origin on every POST.
- The stock launcher is disabled only after ours is confirmed as Home.
- Files in this folder never leave the machine without the clean-up in `README.md` ("Before you publish").

## 6. What was built and decided

**Debloat.** 34 vendor and partner packages disabled in four batches (survive reboot); uninstalled MX Player, Voot,
Discovery+, Zee5 and JioCinema (APKs in `backup/apks/`); the firmware updater app cut; kept Netflix, Prime Video,
Hotstar, VLC, the Play Store, the Assistant packages, the Chromecast receiver and the TV app (HDMI and tuner; its tile
is hidden). `partnersetup` was re-enabled after the Play Store crashed. The firmware had already disabled its boot-ad
service.

**Launcher.** The stock launcher is disabled (the firmware ignores `set-home-activity`; a redirect broke volume repeat).
Lock screen at boot, every wake and after 2 minutes idle; shutter reveal; wallpaper per show. Accessibility helper with
`key_filter: false`, `power_on_home`, `power_on_home_always`. Wrap-around grid, Wi-Fi name pill, no colour glow.

**Now-playing.** `dumpsys media_session` polled every 4 s with the DUMP permission, media keys for control, Spotify
only. Pause brings Spotify to the front first (a background pause is undone after about 31 s).

**Wallpapers.** 20 images renamed `w01..w20` by script, copies at 1280 x 720, daily shuffle on Home.

**Add-ons.** Capacity Stats and Brightness as built (night 21:00 to 07:00 at 50 percent with 60 percent warmth, day 70
percent, Dimming switched on by the user); Weather forked (about 130 Indian cities, saved city Mumbai).

**Apps.** Spotify and Crunchyroll from the Play Store; SmartTube 32.68 (`org.smarttube.stable`, armv7) sideloaded after
SHA-256 `2130b23b17ed5cfd324baec81750a2be22059212b6ee17449f21cae2cb3a60d9` and signer SHA-256
`109735aa24fe85e2e1623ebb3b1ecc4bf4263f4c6374e4411d646bfa2eda482a` checks (APK kept in `work/downloads/smarttube/`);
stock YouTube stays enabled. Dock: Netflix, YouTube, Prime, Hotstar, Spotify, Crunchyroll; SmartTube is a grid tile.

**Private DNS.** `private_dns_specifier dns.adguard-dns.com`, `private_dns_mode hostname`. Ad domains resolve to
0.0.0.0, normal names resolve, network stays validated; a new lookup costs 60 to 90 ms more than the hotspot's DNS.

**Performance.** `cmd package compile -m speed -f` on VLC, Spotify, Crunchyroll, SmartTube, Hotstar, Netflix, Prime and
YouTube (27 to 280 s each, about 510 MB). Cold starts with the `performance` governor: VLC 3.5 to 2.2 s, Spotify 4.2 to
3.0, Netflix 4.8 to 3.5, SmartTube 3.5 to 3.2. Launcher baseline: key response p50 26 ms, jank 0.56 percent, cold start
1.3 s, PSS 33 MB. MSAA was already off. The pendrive with VLC, Bluetooth headphones and the screensaver drift were
checked.

**Voice.** The remote's microphone cannot feed a third-party app (spike returned zero samples), so the phone is the
microphone. Chrome's speech recognition (en-IN), keyword rules, no Needle or Vosk.

**Phone remote.** Stdlib Python server in Termux on 127.0.0.1:8765, one web page, adb to the TV, TV rediscovery by a
/24 scan for port 5555 and the model name, native `sendevent` keys (0.7 to 1 s per command), `am force-stop` for
"close", autostart through Termux:Boot. ADB stays on; the risk and the revoke steps are in `docs/SECURITY.md`.

## 7. Phone remote in brief

Files: `server.py`, `commands.py` (tables `APPS` and `KEYS`, the parser, the drop-down list, generated docs),
`index.html`, `setup.sh`, `COMMANDS.md` (generated: `python commands.py > COMMANDS.md`), `test_commands.py`
(`python -m unittest test_commands`; it fails if `COMMANDS.md` is stale). A phrase matches the whole sentence after
filler words (go, to, take, me, press, button, the, please, ...) and counts are dropped, so "go to home" equals "home".
`close <app>` runs `am force-stop`; plain `close` closes the app in front unless it is protected (launcher, `android`,
`com.android.*`, vendor packages), in which case it goes Home. Update the phone: serve `phone-remote/` from the
laptop (`python -m http.server 8000 --bind <laptop ip>`), run `setup.sh` in Termux, stop the file server afterwards.

## 8. Backlog and status

All 15 tasks are done and verified. The gate for each is in `PROMPTS.md`.

| Task | What | State |
|---|---|---|
| T1 | Laptop toolchain (JDK 17, SDK 34, venv) | done |
| T2 | Backup and `restore-tv.ps1` | done |
| T3 | Remote key capture (`work/remote-keys.md`) | done |
| T4 | Debloat list | done |
| T5 | Apply debloat in batches, reboot check | done |
| T6 | Launcher, stock launcher disabled | done |
| T7 | Wallpapers | done |
| T8 | Lock screen and clock fixes | done |
| T9 | Add-ons | done |
| T10 | Apps | done |
| T11 | Private DNS | done |
| T12 | Performance and checks | done |
| T13 | Mic spike (result: use the phone's mic) | done |
| T14 | Phone remote | done |
| T15 | Autostart, ADB policy, cleanup | done |

Loose ends (ask the user): Brightness add-on defaults, whether the add-on "Show card" prompts were answered, an
optional SmartTube slot in the dock, the repository clean-up in `README.md` before publishing.

After a TV reboot or power cut run `.\work\cpu-fast.ps1 on`.

## 9. Workflows

**Rebuild and deploy the launcher (about 75 s).**

1. Kill stray java: `Get-Process java | Stop-Process -Force`.
2. In `launcher-fork`, with `JAVA_HOME` and `ANDROID_HOME` set:
   `cmd /c ".\gradlew.bat --no-daemon --offline --console=plain testReleaseUnitTest assembleRelease 2>&1"`.
   Always `--offline`; one build at a time.
3. `adb -s <ip>:5555 install -r launcher-fork\app\build\outputs\apk\release\app-release.apk`.
4. `adb ... shell pm grant com.gathin.tvhome android.permission.DUMP`.
5. `.\work\glow.ps1 none` pushes `work/theme/config.json`, restarts the launcher and re-enables the accessibility helper
   that Android drops on every reinstall. The APK size gate is 589,824 bytes.

**Push the whole theme (with wallpapers, slow):** `tools\theme.ps1 -TvHost <ip> work\theme`.

**Add-on:** build in its folder, push the APK with `tools\b64push.py`, `pm install -r`.

**Quick state check:**

```text
dumpsys window | grep mCurrentFocus                          the launcher
settings get secure enabled_accessibility_services           contains com.gathin.tvhome/.HomeKeyService
pm list packages -d | wc -l                                  about 34
logcat -d -b crash | grep -c "Process:"                      0
settings get global private_dns_mode                         hostname
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor    performance after cpu-fast on
```

**Undo everything:** `.\restore-tv.ps1` (`-DryRun` first), reboot, check Home. See `docs/RESTORE.md`.

**Benchmark:** `TV_HOST=<ip> tools\venv\Scripts\python.exe -I tools\bench.py --keys 36 --series 3 --out-dir work\bench-x`
(about 9 minutes; do not repeat it casually).

## 10. Gotchas

The full list with fixes is `docs/TROUBLESHOOTING.md`. The ones that bite most:

- Git Bash: set `MSYS_NO_PATHCONV=1`; Windows tools need `C:/...` paths. PowerShell mangles adb shell strings with
  `$(...)`, quotes and `|`: use `tv.py --batch` or Bash.
- Reinstalling the launcher drops the accessibility helper: always finish with `work\glow.ps1 none`.
- `adb shell input keyevent` takes 1.7 s; `sendevent` takes 0.4 s.
- The CPU governor resets in about 20 s unless the keep-alive loop runs; the loop dies at reboot.
- Notification listeners, Spotify media browsers, the remote's microphone and writing the governor from an app are
  closed routes on this device. Do not try them again.
- The TV's address changes per network; the phone remote rediscovers it, the laptop scripts need `-TvHost` or ARP.

## 11. Documentation duties

- Section 6 of this file: add the decision the moment it is made.
- `docs/SECURITY.md`: update when access or exposure changes.
- `README.md`: keep the feature table, status and the "Before you publish" list true.
- `docs/*`: update with the code; `docs/LAUNCHER.md` for launcher behaviour, `docs/TROUBLESHOOTING.md` for every new
  gotcha or closed route.
- `phone-remote/COMMANDS.md`: generated, never hand-edited.
- This file: update sections 5, 6 and 8 when structure or status changes.
