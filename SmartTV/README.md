# SmartTV: debloat and restyle a low-end Android TV

A reversible toolkit, and an AI-agent playbook, for turning a slow, ad-heavy Android TV (Android 9, 1.4 GB RAM, 720p)
into a fast, clean one. Everything is done over `adb`. Nothing is rooted, nothing is flashed, and one command puts the
TV back the way it was.

It was built and verified on a TCL UnionTV (see [docs/PORTING.md](docs/PORTING.md) for the exact device and what to
change for another one). The tools are written for that TV, but the method is general.

## What you get

| Area | Result |
|---|---|
| Debloat | 34 vendor and partner packages disabled (they survive reboot), 5 pre-installed apps uninstalled, the firmware updater cut. Nothing the TV needs to run is touched. |
| Launcher | A glass-look Home (fork of an open-source TV launcher): dock, Inputs pill, Wi-Fi pill, 6-column app grid with wrap-around navigation, a now-playing card, a clock screensaver that drifts. |
| Lock screen | Wallpaper with a big clock and a greeting, shown at boot, on every wake from standby and after 2 minutes idle. Any key lifts it with a shutter animation. While a song plays it becomes a song card with previous, play and next on the D-pad. |
| Wallpapers | 20 images at 720p, a new one on every lock and daily on Home. |
| Add-ons | Storage stats, Weather (about 130 Indian cities, saved city), Brightness (night schedule, warmth). |
| Apps | Netflix, Prime Video, Hotstar, YouTube, SmartTube, Spotify, Crunchyroll, VLC. |
| Ad blocking | Private DNS to AdGuard DNS (DNS over TLS). Blocks ad and tracker domains; it cannot remove in-stream ads. |
| Speed | All kept apps compiled ahead of time, the CPU held at its top clock, cold starts about 25 to 35 percent faster. |
| Phone remote | A web page on a phone (Termux + Chrome): hold to talk, D-pad, volume, media, app buttons, "open Netflix", "close app", "go to home". |
| Undo | `restore-tv.ps1` puts everything back: packages, apps, DNS, accessibility, CPU. |

## Status

All 15 planned tasks are done and verified on the real TV. Detail and evidence for every choice is in
[CLAUDE.md](CLAUDE.md) section 6.

| Phase | Tasks | State |
|---|---|---|
| 1 Debloat, launcher, restore | T1 toolchain, T2 backup and restore, T3 remote keys, T4 list, T5 apply, T6 launcher | done |
| 2 Look | T7 wallpapers, T8 lock screen | done |
| 3 Add-ons, apps, tuning | T9 add-ons, T10 apps, T11 Private DNS, T12 performance | done |
| 4 Voice and phone remote | T13 mic spike, T14 phone remote, T15 policy and autostart | done |

Open loose ends: Brightness add-on defaults are not chosen, the add-on "Show card" prompts need answering on the TV,
and SmartTube is a grid tile, not in the dock. The TV remote's own mic button cannot feed a third-party app on this
firmware (tested), so voice comes from the phone.

## Repository layout

```
README.md  CLAUDE.md  PROMPTS.md                   what, how an agent works here and why, prompts
docs/                                              SETUP, LAUNCHER, RESTORE, SECURITY, TROUBLESHOOTING, PORTING
restore-tv.ps1                                     one-command undo
tools/                                             adb client, disable/restore, recovery, theme push, bench, venv
launcher-fork/                                     the launcher (Android project, package com.gathin.tvhome)
addons-local/                                      weather (forked), brightness, capacity-stats
phone-remote/                                      Termux server, page, command rules, COMMANDS.md
work/                                              debloat lists and batches, theme, wallpapers, scripts, remote key map
backup/                                            pre-change state of the TV (package lists, settings, APKs)
reference/                                         upstream licence and safety rules, glass theme, example debloat list
TV-Wallpapers/                                     source wallpaper images
```

## Quick start

With an AI agent: open the repo in the agent, paste the opening block from [PROMPTS.md](PROMPTS.md), connect the TV by
USB debugging or network debugging, and go phase by phase. The agent reads [CLAUDE.md](CLAUDE.md) for the rules.

By hand: follow [docs/SETUP.md](docs/SETUP.md). The short version:

1. Install JDK 17, the Android SDK (platform 34, build-tools 34) and platform-tools. Create `tools/venv`.
2. Turn on developer options and debugging on the TV and `adb connect <tv ip>:5555`.
3. Back up (`backup/`), then apply `work/batches/*` with `tools/disable.ps1`, one batch at a time.
4. Build and install `launcher-fork`, then push `work/theme/config.json` with `work/glow.ps1`.
5. Set Private DNS, compile the apps, start the CPU loop (`work/cpu-fast.ps1 on`).
6. Put `phone-remote/` on a phone with `setup.sh`.

Undo everything: `.\restore-tv.ps1`, then reboot once. See [docs/RESTORE.md](docs/RESTORE.md).

## Safety in one paragraph

Packages are only disabled with `pm disable-user --user 0`, one per call, in small batches, after a backup, and never
the Android core, the TV middleware or the vendor key and input services. Factory and test packages are never
launched. The stock launcher is only disabled after the new one is confirmed as Home. The full rules are in
[CLAUDE.md](CLAUDE.md#safety-rules-not-negotiable).

## Known limits

- DNS filtering cannot block ads inside a video stream.
- Network ADB stays on so the phone remote works. Anyone holding an accepted adb key on the same network controls the
  TV; read [docs/SECURITY.md](docs/SECURITY.md) and keep the TV off shared networks.
- The phone remote sends your voice to Google through Chrome's speech recognition.
- The CPU governor loop and the TV address are lost at reboot or network change (the remote finds the TV again by
  itself; run `work/cpu-fast.ps1 on` after a reboot).
- 32-bit ARM only: use armeabi-v7a builds of any app you sideload.

## Before you publish this repository

This repo came from a working install. Two folders are kept as they are on purpose; know what is in them.

- `backup/` has `getprop`, settings and package dumps (serials, IDs, Wi-Fi names, account hints) and the APKs of the
  uninstalled apps.
- `work/downloads/` holds a sideloaded third-party APK. Its SHA-256 and signer are in [CLAUDE.md](CLAUDE.md) section 6.
- `TV-Wallpapers/` and `work/theme/wallpapers/` are images you may not have the rights to share.
- Recovery records live in `~/.cleanyourtv/`, outside the repo, and name IP addresses.
- No scripts contain the TV's address or MAC: they take `-TvHost` / `TV_HOST` or `-TvMac` / `TV_MAC`.

More on access and exposure: [docs/SECURITY.md](docs/SECURITY.md).

## Credits and licences

- Built on the open-source **cleanyourtv** toolkit (MIT, licence copy in
  [reference/UPSTREAM-LICENSE-MIT](reference/UPSTREAM-LICENSE-MIT)). `tools/` is its scripts unchanged; `launcher-fork/`
  and `addons-local/` are forks of its launcher and add-ons. Keep its licence notice with any redistribution.
- `launcher-fork/FONT-LICENSE-OFL.txt` covers the bundled font.
- The phone remote relies on Termux, Android's `adb` and Chrome's speech recognition; none are bundled.
