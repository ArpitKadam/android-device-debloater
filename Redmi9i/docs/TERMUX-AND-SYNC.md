# Termux, boot reliability and Google Drive sync

The phone can act as a small always-on box. Termux provides the userland; the launcher keeps it alive; rclone syncs a
folder with Google Drive.

## Install Termux

Use the GitHub or F-Droid builds, not the Play Store build (a different signing key, so the add-ons will not talk to it). Install **Termux**, **Termux:Boot**
and **Termux:API** from the same source so their signing keys match, and pick the build that matches the phone's ABI
(`adb shell getprop ro.product.cpu.abilist`). On the reference phone the userspace is 32-bit ARM (`armeabi-v7a`) although
the kernel reports `armv8l`, so prebuilt `aarch64` binaries do not run; things that build from source do. Open Termux:Boot
once after installing, or it stays in the "stopped" state and never receives the boot broadcast.

Run commands from the PC: `adb shell run-as com.termux ...` works with the debug builds (export `PATH` and `HOME` first).
Packages used so far: `termux-api`, `git`, `curl`, `rclone`, `cronie`.

## Keeping Termux alive (all three were needed)

1. **Vendor Autostart** on for Termux, Termux:API and Termux:Boot, in the vendor security app. The `appops` route is
   reset at every boot; the security app's database wins.
2. **Open Termux:Boot once** (see above).
3. **Device-idle whitelist:** `adb shell cmd deviceidle whitelist +com.termux`, `+com.termux.boot`, `+com.termux.api`.
   Whitelisted apps are exempt from the idle state that blocks Boot's JobScheduler job. This is the real fix; the
   standby-bucket tricks (`settings put global app_standby_enabled 0`, `am set-standby-bucket com.termux.boot active`)
   were only a workaround and the vendor resets them. The whitelist was lost once after boot-loop recoveries: after any
   failed boot run `adb shell dumpsys deviceidle whitelist | grep termux` and re-add.
4. Set the battery mode for all three to "No restrictions" and lock Termux in recents.

Do not run `pm grant` against Termux again without rechecking the whitelist: a grant on its shared uid coincided with the
whitelist entries disappearing.

## Boot script and the launcher's helper

- `~/.termux/boot/00-start.sh`: takes `termux-wake-lock`, logs a line to `~/boot.log`, starts `crond`.
- Because Termux:Boot still failed on some reboots, the Grand Line launcher also starts services: on each launcher start
  it sends `RUN_COMMAND` for `~/bin/ensure-services.sh` (wake lock, `crond` if not running). Setup: put
  `allow-external-apps = true` in `~/.termux/termux.properties`, then `adb shell pm grant dev.redmi9i.grandline
  com.termux.permission.RUN_COMMAND` (`tools/grant-launcher-permissions.sh`). Verified from a fully stopped Termux with
  no `crond`. Re-grant after `pm clear` or a reinstall that resets permissions.
- A headless soak test (screen off 30 minutes with the wake lock held) was **not** completed: the sleep key event did not
  turn the screen off while charging. Treat long unattended uptime as unproven, and plan SSH or Wi-Fi access if you need
  a true headless server (adb over USB dropped once while the phone sat idle).

## rclone two-way sync to Google Drive

Goal: `/sdcard/PhoneSync` on the phone mirrors one Drive folder, both ways, every 30 minutes.

**Remote.** `~/.config/rclone/rclone.conf` on the phone (`chmod 600`, never copied into the repo): remote `gdrive`,
`scope = drive`, `root_folder_id` of one Drive folder (so the remote cannot see the rest of the Drive), token from running
`rclone authorize "drive"` on a PC with a browser and pasting it into the phone's config.

**Your own OAuth client.** rclone's shared client id is rate limited and is being retired. Create your own: Google Cloud
project, enable the Drive API, Google Auth Platform: Branding, Audience (publish the app to "In production"; if it stays in
"Testing", tokens expire after 7 days), then a Desktop client. Run `rclone authorize drive <client_id> <client_secret>` on
the PC and replace the token in the phone's config, adding `client_id` and `client_secret` lines. Keep the id and secret
out of the repository and out of chat logs. On the reference phone this step is **still pending**: the shared client works
for now, and the Publish button was greyed out during setup.

**Script** `~/bin/phonesync.sh`, run from `crond` (`*/30 * * * *`), starts after the wake lock in the boot script:

- `rclone bisync /sdcard/PhoneSync gdrive:` with the config path given explicitly.
- A lock directory prevents overlapping runs (an overlap once created duplicate files and duplicate Drive folders).
- Bisync aborts if more than 50% of files would be deleted; conflicts keep both versions (`.conflict1`); Google-native docs
  and shortcuts are skipped. Only failed runs are logged, to `~/rclone-sync.log`.
- An empty baseline makes bisync refuse to run; the script then re-runs once with `--resync --resync-mode newer` (merge
  only, no deletes). If bisync ever asks for a resync, run it by hand:
  `rclone bisync /sdcard/PhoneSync gdrive: --resync --resync-mode newer --config ~/.config/rclone/rclone.conf`.
- Deletes go to Drive's trash (30 days). There is no trash view on the phone.

Verified: list, upload, download with identical SHA-256, `rclone check` clean; create, rename and delete in the Files app all
reached Drive after a sync; Drive-side files came down; a same-file edit on both sides kept both versions. The Files app pins
`/sdcard/PhoneSync` on its home screen (long-press to unpin). There is no FUSE without root, so use `sync`, `copy`, `bisync`
or `serve webdav`, not `mount`. Never run `rclone delete` or `purge` on the whole remote; delete only named test files.

## Telegram

Scope is the app only: the official APK from telegram.org (universal build, includes `armeabi-v7a`; verify the signature and
the SHA-256 before installing), installed as `org.telegram.messenger.web` with launcher alias
`org.telegram.messenger.DefaultIcon`. The user signs in with their own account. A Telegram **bot** (long-polling,
allow-listed user id, token in a `chmod 600` file) was planned and dropped; the voice-note pipeline in `docs/ROADMAP.md`
would need it.
