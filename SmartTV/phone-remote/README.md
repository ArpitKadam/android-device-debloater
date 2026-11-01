# Phone remote

A web page on the phone (hold to talk, buttons) and a small Python server that sends the commands to the TV with
adb. Everything runs in Termux on the phone; nothing is installed on the TV. What it understands: `COMMANDS.md`.

## Set up the phone (once)

1. Install **Termux** from F-Droid (not the Play Store version). For autostart also install **Termux:Boot** from
   F-Droid and open it once.
2. Join the TV's Wi-Fi. On the laptop: `cd phone-remote`, then `python -m http.server 8000 --bind <laptop ip>`.
3. In Termux: `curl -s http://<laptop ip>:8000/setup.sh | bash -s <laptop ip>:8000`, then `pkg upgrade -y` if
   `adb version` fails with a "cannot locate symbol" error. Stop the laptop's file server afterwards.
4. `~/tv-remote/start.sh`, then open `http://localhost:8765` in Chrome and add it to the home screen.
5. The first command makes the TV ask "Allow USB debugging?": tick Always allow.
6. Phone settings: battery saver for Termux and Termux:Boot set to No restrictions, and Autostart on (MIUI).

Update the phone after changing the code: repeat steps 2-3 and restart `start.sh`.

## Files

`server.py` (HTTP + adb), `commands.py` (what was said -> one action; tables for apps and keys), `index.html`
(the page), `setup.sh`, `COMMANDS.md` (generated: `python commands.py > COMMANDS.md`), `test_commands.py`
(`python -m unittest test_commands`).

The TV address is remembered in `~/.tv-remote-host` on the phone and found again by scanning the local network
when it changes. Keys go to `/dev/input/event0` and `event4` with `sendevent`; if a keys stop working after a
reboot, check those node numbers (see `FAST_KEYS` in `server.py`).
