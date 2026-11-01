# SECURITY: what this setup opens up, and how to close it

## ADB stays on

Network ADB (port 5555) on the TV is deliberately left on, because the phone remote needs it. That is the owner's
choice; the repo's upstream advice is to close it when finished.

- **What it allows.** adb gives a full shell as the `shell` user: install and remove apps, change settings, read most
  data. It is not root.
- **Who can use it.** Only a computer holding a key the TV has already accepted. A new key triggers an "Allow USB
  debugging?" prompt that only someone at the TV can accept. Two keys are accepted in the reference setup: the laptop's
  and the phone's (Termux `~/.android/adbkey`).
- **The real exposure** is therefore those two key files. Anyone who copies the phone's key and reaches the TV's
  network controls the TV. Keep the TV off networks shared with strangers, keep Termux backups off, and do not commit or
  share any `adbkey` file.
- **To cut access.** TV Settings, Developer options, Revoke USB debugging authorisations; delete `~/.android/adbkey` in
  Termux; turn network debugging off. `restore-tv.ps1` does not revoke keys (no root on the TV).
- **If the network is ever shared with untrusted devices**, revoke the authorisations and turn network debugging off.

## Phone remote

- The server binds to `127.0.0.1` only: other devices cannot reach it, only the phone's own browser.
- Every POST needs the header `X-Remote: 1` (a foreign web page cannot add it without a CORS preflight, which the server
  never grants), a matching `Host` (stops DNS rebinding) and no foreign `Origin`. Tested with `curl`: no header gives
  403, foreign origin 403, wrong host 404.
- Package names are validated (`[A-Za-z0-9._]+`) before they reach a device shell; typed text is reduced to
  `[A-Za-z0-9 ._@-]`; key repeats are capped at 20. "Close app" never force-stops the launcher or vendor and system
  packages.
- **Privacy.** Chrome's speech recognition sends the audio to Google. There is no local speech engine.
- The TV's address is stored on the phone in `~/.tv-remote-host`.

## The launcher

- No `INTERNET` permission, no network code.
- Needs `android.permission.DUMP` (granted over adb) to read media-session state, and runs as an accessibility service
  to handle Home and power-on keys. Treat both as sensitive grants; remove them with `pm revoke` and by disabling the
  service if you uninstall the launcher.

## Sideloaded apps

Verify before installing: ABI, SHA-256 against the publisher's digest, signing certificate against a second source.
Record the values (CLAUDE.md section 6 has the SmartTube ones).

## Files not to publish

- `backup/` (device dumps, removed apps' APKs) and `work/downloads/` (a third-party APK) are kept in this working copy
  on purpose; decide before sharing the repository.
- Any `adbkey` file, `~/.cleanyourtv/` recovery records (they name IP addresses), and screenshots of the TV.
