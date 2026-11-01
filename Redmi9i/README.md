# Grand Line: a minimal, themed, always-on Android phone

Turn a spare, low-end Android phone into a clean, themed device that can also run quietly as a small server. No root, no
unlocked bootloader: everything is done over `adb` on a USB cable plus a few ordinary apps.

The default theme is **One Piece / Straw Hat** (red, gold, ocean blue, Pirata One titles). The art is yours: the repository
ships no copyrighted images and every screen falls back to art drawn in code.

## What is in the box

| | |
| --- | --- |
| **Debloat** | A method, scripts and hard-won rules for removing vendor and Google apps for one user, in rebooted batches, reversibly |
| **Grand Line launcher** | A custom home app. Empty home with a swipe-up drawer; the drawer opens on a panel with a wanted poster of the day, a clock and live weather; one muted looping video wallpaper per page; themed app icons; hidden apps; a poster lock screen and an accessibility-button menu |
| **Files** | An offline file manager with viewers for images, video, audio, PDF, text, docx, xlsx and zip, USB/SD support and the same theme |
| **Termux and sync** | Termux kept alive across reboots and battery management, a launcher helper that starts its services, and two-way rclone sync with Google Drive |
| **Tools** | Scripts to install APKs over USB, push assets, cut transparent icons, encode video loops, prepare posters |
| **Prompts** | `PROMPTS.md`: copy-paste prompts so an AI agent can rebuild all of it on any phone you connect |

## Requirements

- An Android 10 or newer phone you are willing to experiment on, a USB cable, and a computer with `adb` (platform-tools).
- To build the apps: JDK 17, Android SDK (platform 35, build tools), Gradle 8.9.
- For the art pipeline: Python 3 with `pillow`, `numpy`, `scipy`; `ffmpeg` for video.
- Your own images (see `theme-assets/README.md`).

The launcher and Files apps target API 29 and run on newer Android too, though the quirks documented here were found on Android 10.
The debloat list and the Termux battery steps are **vendor specific**: use them as a method, not a recipe.

## Quick start

```bash
# 1. Phone: enable Developer options and USB debugging, connect, accept the prompt. Then:
adb devices

# 2. Build and test both apps
(cd grandline-app && gradle -q testDebugUnitTest assembleDebug)
(cd files-app     && gradle -q testDebugUnitTest assembleDebug)

# 3. Install (taps the "install via USB" prompt on phones that show one)
tools/install-apk.sh grandline-app/app/build/outputs/apk/debug/app-debug.apk
tools/install-apk.sh files-app/app/build/outputs/apk/debug/app-debug.apk
tools/grant-launcher-permissions.sh

# 4. Art (optional; the apps draw fallbacks without it)
tools/push-assets.sh

# 5. Make Grand Line the default home (rollback: set the previous launcher the same way)
adb shell cmd package set-home-activity dev.redmi9i.grandline/.home.HomeActivity
```

On Windows use Git Bash and set `MSYS_NO_PATHCONV=1`, or see `CLAUDE.md` for the path rules. For everything else, hand the
repository to an AI agent and use `PROMPTS.md`.

## Repository map

```text
CLAUDE.md                 project knowledge and hard rules (start here, humans too)
PROMPTS.md                prompts to rebuild the project on any phone
docs/ARCHITECTURE.md      how the launcher and Files are built
docs/DEBLOAT.md           safe debloating, what breaks, the rescue loop
docs/TERMUX-AND-SYNC.md   Termux boot reliability, rclone to Google Drive, Telegram
docs/TROUBLESHOOTING.md   every trap met so far
docs/REFERENCE-DEVICE.md  the phone this was built on, and why it shaped the design
grandline-app/            the launcher (Kotlin, 56 unit tests)
files-app/                the file manager (Kotlin, 43 unit tests)
debloat/                  package triage CSV and PowerShell apply/restore scripts
tools/                    install, permissions, asset push, icon/poster/video preparation
theme-assets/             where your art goes (git-ignored except its README)
```

## Safety, honestly

- The poster lock screen is **decoration**. It is an overlay with no PIN and protects nothing.
- Debloating can make some phones unbootable. Read `docs/DEBLOAT.md` first, back up, and go one batch at a time.
- The launcher holds powerful permissions (`WRITE_SECURE_SETTINGS`, an accessibility service). Both are granted by you over adb and
  used only to keep its own service enabled and to draw its own overlay. Read the code before trusting it on a phone with anything
  important.
- Do not expose the phone's services to the internet. Keep tokens and OAuth secrets on the phone only.

## Status

Built and verified on one phone (see `docs/REFERENCE-DEVICE.md`). The Gradle package names start
with `dev.redmi9i` after that first device; rename them if you fork.

## Credits and licences

- Titles use **Pirata One** (SIL Open Font License), bundled in `res/font/`.
- Weather data: **Open-Meteo** (free, no key; check their terms for your use).
- Third-party apps installed by the instructions (Termux, Brave, Telegram, rclone) keep their own licences.
- Code is MIT licensed; see the `LICENSE` file at the repository root.
- One Piece is owned by its creators and publishers. This is a fan theme with no affiliation; the repository contains none of the artwork.
