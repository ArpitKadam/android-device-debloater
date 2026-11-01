# android-device-debloater

Strip the junk off cheap Android devices and make them feel like yours. Everything runs over `adb`. No root, no unlocked
bootloader, and every change can be undone.

Two complete, device-specific builds live here. Each is a method you can copy to another device, not a one-size-fits-all
recipe: vendor package names differ, so read the porting notes before you disable anything on different hardware.

| Project | Device | What you get |
| --- | --- | --- |
| [Redmi9i](Redmi9i/) | Xiaomi Redmi 9i phone, Android 10 | Reversible debloat, a custom "Grand Line" launcher, an offline file manager, Termux that survives reboots, rclone sync to Google Drive |
| [SmartTV](SmartTV/) | TCL UnionTV, Android 9, 1.4 GB RAM | 34 vendor packages disabled, a glass-look launcher with a lock screen, wallpapers, add-ons, ad-blocking Private DNS, a phone-as-remote web page, one-command undo |

## Redmi9i

A spare low-end phone turned into a clean, themed, always-on device. Kotlin apps (launcher: 56 unit tests, Files: 43),
PowerShell debloat scripts with a restore path, and a Termux setup for running small services.

- Start with [Redmi9i/README.md](Redmi9i/README.md), then [docs/DEBLOAT.md](Redmi9i/docs/DEBLOAT.md) before touching any package.
- [Redmi9i/PROMPTS.md](Redmi9i/PROMPTS.md) holds prompts that let an AI agent rebuild it on another phone.

## SmartTV

A slow, ad-heavy Android TV made fast and clean. Cold starts of the kept apps are about 25 to 35 percent faster, and the
launcher uses 33 MB.

- Start with [SmartTV/README.md](SmartTV/README.md). Undo everything with `SmartTV/restore-tv.ps1`.
- Porting to another TV: [SmartTV/docs/PORTING.md](SmartTV/docs/PORTING.md). Exposure from leaving network adb on:
  [SmartTV/docs/SECURITY.md](SmartTV/docs/SECURITY.md).

## Requirements

- A computer with `adb` (Android platform-tools) and USB or network debugging enabled on the device.
- To build the apps: JDK 17, the Android SDK, Gradle 8.9 (each project ships its own wrapper where it needs one).
- Windows with PowerShell for the debloat and restore scripts; Git Bash for the shell tools. Set `MSYS_NO_PATHCONV=1`
  in Git Bash so adb paths are not mangled.
- Python 3 for the SmartTV tools and phone remote, plus `pillow`, `numpy`, `scipy` and `ffmpeg` for the Redmi9i art pipeline.

## Safety

- Debloating can leave a device unbootable. Back up first, work in small batches, reboot and check between them.
- Prefer `pm disable-user --user 0 <package>` to uninstalling. It is reversible; an uninstall is not.
- Never disable the Android core, system UI, settings, the permission and package installers, or a TV's input and key services.
- The launchers use powerful permissions (an accessibility service, `WRITE_SECURE_SETTINGS`, `DUMP`), granted by you over adb.
  Read the code before installing on a device that holds anything important.
- Network adb gives a shell to any machine holding an accepted key. Keep those devices off shared networks.

## What is not in the repository

Left out on purpose; each project's `.gitignore` explains the rule.

- Device dumps (`backup/`): serial numbers, IDs, Wi-Fi names, and APKs of removed apps. Take your own before changing anything.
- Third-party APKs (`downloads/`). Re-download them from the official source and verify the checksum.
- Wallpapers, posters and other artwork you may not have the rights to share. Add your own under the same names.
- Build output, `local.properties`, adb keys and tokens.

The scripts take the device address as a parameter (`-TvHost`, `TV_HOST`), so no IP or MAC address is hard-coded.

## Credits

- SmartTV builds on the MIT-licensed **cleanyourtv** toolkit; its licence is kept in `SmartTV/reference/UPSTREAM-LICENSE-MIT`.
- Fonts: Pirata One and Outfit, bundled under the SIL Open Font License.
- Weather data: Open-Meteo.
- One Piece is owned by its creators and publishers. The Redmi9i theme is an unaffiliated fan theme and ships none of the artwork.

## Licence

[MIT](LICENSE) © 2026 Arpit Kadam. Forked and bundled code keeps its own licence.
