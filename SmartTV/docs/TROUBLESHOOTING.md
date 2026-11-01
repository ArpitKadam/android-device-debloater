# TROUBLESHOOTING: things that cost time, and routes that are closed

Each entry is something that was hit and solved (or proven impossible) on the reference device. Read this before
trying an idea twice.

## Tools and shells

| Problem | Cause and fix |
|---|---|
| Git Bash turns `/data/...` into `C:/Program Files/Git/data/...` | Set `MSYS_NO_PATHCONV=1`. Windows programs (ffmpeg, Windows Python) need `C:/...` paths, not `/c/...`. |
| PowerShell breaks `adb shell` strings that contain `$(...)`, quotes or `\|` | Send them through `tools\venv\Scripts\python.exe -I tools\tv.py --batch` with a here-string, or use Bash. |
| `adb shell input keyevent` takes about 1.7 s per call | It starts a Java VM each time. Use `sendevent` to the input node (about 0.4 s), see `phone-remote/server.py` `FAST_KEYS`. Node numbers and scancodes are device-specific and were measured. |
| `uiautomator dump` fails ("null root node") | The accessibility helper is on. Read the screen from a downscaled screenshot crop instead. |
| `dumpsys notification` takes 30 s or more | Run it in the background. |
| Foreground `sleep` is blocked in the agent's Bash | Use a short loop of checks or a background command. |
| Gradle build fails on the first run offline | The wrapper cache is cold. Download Gradle once with resumable `curl -C -`, verify its SHA-256, then always build `--offline`. |
| Two builds at once corrupt each other | Kill stray `java` first; one build at a time. |
| PowerShell treats a javac stderr note as fatal | Run Gradle through `cmd /c "..."`. |
| Windows blocks `python -m http.server` on port 8080 | Another service owns it (`WinError 10013`). The phone remote uses 8765. |
| Port 8000 file server unreachable from the phone | Bind it to the laptop's LAN address (`--bind <ip>`), allow it through the Windows firewall, and stop it afterwards. |

## The TV

| Problem | Cause and fix |
|---|---|
| The TV's address changed | It changes per network. Find it with `arp -a` (match the MAC), probe port 5555, `adb connect`. The authorisation survives. The phone remote scans the /24 itself; the laptop scripts need `-TvHost` or `TV_MAC`. |
| Clocks freeze | The firmware stops the `TIME_TICK` alarm. The fork's `ClockNudge` re-reads every 10 s. |
| Accessibility helper gone after an install | Android drops it on every reinstall. `work\glow.ps1 none` re-enables it. Check `settings get secure enabled_accessibility_services`. |
| Play Store crashes at launch with `NoSuchElementException ... TvMainActivity` | Re-enable `com.google.android.partnersetup` and `pm clear com.android.vending` (it is not proven which of the two fixed it). |
| Volume stops repeating when held | The accessibility key filter is on. Keep `key_filter: false` and use `power_on_home`. |
| The performance CPU governor reverts to `ondemand` after about 20 s | A vendor daemon resets it. Only a keep-alive loop holds it: `work\cpu-fast.ps1 on` starts one (`work/cpu-hold.sh`) on the TV; it does not survive a reboot. An app cannot write the governor (SELinux). |
| Spotify un-pauses itself | A media-pause key sent while it is in the background is undone about 31 s later. Pause with Spotify in front (the card does this). |
| Live TV shows for about 3 s at a cold boot | The firmware starts it before any app can. Accepted. |
| Remote buttons dead (App grid, "T") | The firmware's key service hard-codes them to the stock launcher and partner app. By design after the stock launcher is disabled. See `work/remote-keys.md`. |
| Home came up blank after changes | Check `dumpsys activity activities | grep mFocusedApp`; re-enable the stock launcher package; see [RESTORE.md](RESTORE.md). |
| DNS broke on one network | That network blocks port 853. `settings put global private_dns_mode opportunistic`. |

## Closed routes (do not retry)

- **Notification listeners on a `low_ram` Android 9 build.** `cmd notification allow_listener` is silently ignored and
  the listener never binds. Use `dumpsys media_session` with the DUMP permission instead.
- **Spotify via a media browser.** It refuses third-party media-browser clients.
- **Accessibility notification events** fire only on the first post, so they cannot follow a song.
- **The remote's microphone from a third-party app.** The audio path exists (the Assistant opens a 16 kHz recorder on
  the voice-recognition source, the audio HAL selects the remote's microphone and a root daemon starts the stream), but
  an ordinary app's `AudioRecord` opens fine and every `read` returns 0. Getting samples would mean being the Assistant
  or speaking the vendor daemon's private protocol. Use a phone's microphone.
- **Writing the CPU governor from the launcher** (permission denied, SELinux).
- **`set-home-activity`** is ignored by the reference firmware; a stock-launcher redirect was 2 to 3 s late and broke
  volume repeat.
- **Oracle Cloud Always Free** as a voice server was abandoned (account sign-in errors, idle reclamation risk).
- **DNS cannot block in-stream ads** (Hotstar and similar); DNS only stops ad and tracker domains.

## The phone

| Problem | Cause and fix |
|---|---|
| Termux `adb` fails with `cannot locate symbol "_ZNSt6__ndk113__hash_memory..."` | Package mirror out of sync. `pkg upgrade -y` (or `termux-change-repo` to a better mirror), then `adb version`. |
| Play Protect blocks installing Termux:Boot | Tap More details, Install anyway (or turn the scan off briefly). The F-Droid build is signed with the same key as Termux. |
| MIUI: `INJECT_EVENTS permission` error from `adb shell input` on the phone | Turn on "USB debugging (Security settings)" in Developer options. |
| "TV not found" | Phone and TV on different networks, or the TV is off or asleep and dropped off the network. Check `adb devices` in Termux. |
| `unauthorized` | Accept the "Allow USB debugging?" prompt on the TV and tick Always allow. |
| Voice does nothing | Chrome needs microphone permission for the page and an internet connection (Google does the recognition). |
| Remote not running after a phone reboot | Install and open Termux:Boot once; set battery to No restrictions and enable Autostart (MIUI) for Termux and Termux:Boot. |
| Keys do nothing after a TV firmware update | The input node numbers in `FAST_KEYS` may have changed; re-measure or fall back to `input keyevent`. |
