# LAUNCHER: the fork in `launcher-fork/`

Package `com.gathin.tvhome`. It is a copy of an open-source Android TV launcher (cleanyourtv's launcher) with the
additions below. APK about 559 KB (the project's size gate is 589,824 bytes), launcher process about 33 MB PSS. It has
no `INTERNET` permission.

## What the fork adds

| Feature | Class | How it works |
|---|---|---|
| Lock screen | `LockActivity` | Translucent activity over Home with a wallpaper, a centred clock, date and greeting. Any key except volume, power and home lifts it with a shutter animation (translate up). A new wallpaper is picked each time and written to Home's own preference, so Home shows the same one afterwards. |
| When it appears | `HomeKeyService` | Raised at screen-off (so a wake shows it at once), at cold boot over the firmware's Live TV window (about 3 s of Live TV first, accepted), and after 2 minutes idle on Home (`home_idle_clock_minutes: 2`). |
| Key helper | `HomeKeyService` (accessibility service) | `key_filter: false`, `power_on_home`, `power_on_home_always`. Sends the user to Home at boot and screen-on. Volume key repeat works with this configuration (it broke with key filtering on). Android drops the service on every reinstall; `work/glow.ps1` turns it back on. |
| Clock fix | `ClockNudge` | Re-reads every TextClock every 10 s, because the firmware stops the `TIME_TICK` alarm and clocks froze. |
| Greetings | `Greetings` | English phrase per hour span ("Burning the midnight oil" to "Good night"). Unit tested. |
| Wrap-around grid | `GridNavigation.wrapTarget`, `MainActivity.glassSnapNext` | Left on the first app of a row goes to the last, and back. The glass highlight snaps instead of sliding across the row. |
| Wi-Fi pill | `MainActivity` | Network name beside the Wi-Fi icon (`wifi_label_width: 200`). |
| Now-playing | `NowPlaying`, `NowPlayingTray` | Top-bar note button toggles a 160 dp glass card under the Wi-Fi and Settings buttons: art or a note, title, artist, previous, play/pause, next. D-pad Down from a top-bar button enters it, Left and Right wrap, Up returns. On the lock screen the same song card uses Left, Right and OK for previous, next and play/pause; any other key unlocks. |
| Screensaver | `ClockDream` | Android dream with a clock that moves to a new spot every minute and shifts colour (no burn-in). Set with `settings put secure screensaver_components com.gathin.tvhome/.ClockDream`. |

## How now-playing reads the song

Android gives an app no normal way to read another app's media session on this class of device: notification
listeners are refused on `low_ram` builds, Spotify refuses media-browser clients, and accessibility notification
events only fire on the first post. What works: the `DUMP` permission (grantable by adb) lets the launcher run
`dumpsys media_session` every 4 seconds, only while the card or lock screen is showing, and parse the title, artist and
play state from the app's session. Controls are ordinary media keys. Limits: no album art; a title that contains ", "
is split at its first comma; Spotify only (`NowPlaying.PACKAGE`, with a `ponytail:` note on how to widen it).

Pause behaviour: a pause sent while Spotify is in the background is undone by Spotify itself about 31 seconds later
(measured). So the card's pause brings Spotify to the front for about 1.5 s, sends the pause there, and returns to
Home or to a new lock screen (`LockActivity` is `noHistory`). Resume sends a plain play.

## Configuration

The launcher reads `config.json` pushed to the device by `work/glow.ps1` (config only, restarts the launcher) or
`tools/theme.ps1` (config plus wallpapers). The current file is `work/theme/config.json`. Keys that matter:

| Key | Value used | Meaning |
|---|---|---|
| `dock_glass` | true | frosted dock |
| `cinematic_art`, `ambient_glow` | 0, false | the coloured glow wash is off (it was distracting) |
| `hidden_apps` | includes the TV app's package | its tile is hidden; inputs are reached from the Inputs pill |
| `live_tv_package` | the TV app's package | what the Live TV tile launches |
| `wallpaper_rotation_minutes` | 1440 | daily on Home (the lock changes it on every show) |
| `clock_wallpaper` | true | wallpaper behind the screensaver clock |
| `advanced_effects` | false | keeps Home light on a slow GPU |
| `wifi_label_width` | 200 | width of the network name |
| `home_idle_clock_minutes` | 2 | idle time before the lock screen |
| `columns` | 6 | app grid columns |

`work/glow.ps1 none|dim|default` switches the colour glow without a rebuild.

## Build, test, deploy

```
taskkill java first (one build at a time)
cd launcher-fork
set JAVA_HOME and ANDROID_HOME
cmd /c ".\gradlew.bat --no-daemon --offline --console=plain testReleaseUnitTest assembleRelease 2>&1"
adb -s <ip>:5555 install -r app\build\outputs\apk\release\app-release.apk
adb -s <ip>:5555 shell pm grant com.gathin.tvhome android.permission.DUMP
..\work\glow.ps1 none
```

About 75 seconds. Always `--offline` and never two builds at once (the machine and the Gradle cache do not like it).
The Gradle run goes through `cmd /c` because a harmless javac stderr note is otherwise fatal in PowerShell. Unit tests
(`LockKeysTest`, `GreetingsTest`, `NowPlayingParseTest`, `GridNavigation` tests) run on the plain JVM.

## Checks after a change

- `dumpsys window | grep mCurrentFocus` shows the launcher on Home.
- `settings get secure enabled_accessibility_services` contains `com.gathin.tvhome/.HomeKeyService`.
- `logcat -d -b crash | grep -c "Process:"` is 0.
- Press the real remote: Home, Back, Input, the Settings button, volume hold, and the lock screen keys. An injected key
  is not proof for anything the accessibility helper handles.
- `uiautomator dump` fails with the helper on ("null root node"); read the screen with a downscaled screenshot crop.

## Limits

- The App-grid and "T" buttons on the reference remote are dead by design (the stock launcher and its partner
  customizer are disabled; the firmware's key service hard-codes them).
- The lock screen shows Live TV for about 3 seconds at a cold boot; the firmware starts it before any app can.
- The reference firmware ignores `set-home-activity`, and a stock-launcher redirect made volume repeat fail, so the
  stock launcher is disabled instead.
