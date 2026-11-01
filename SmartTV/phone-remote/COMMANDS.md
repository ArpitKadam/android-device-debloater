# What the phone remote understands

Generated from `commands.py`: run `python commands.py > COMMANDS.md` after changing the tables.
Case and punctuation do not matter. Words like *go to*, *take me*, *the*, *please*, *button* are ignored, so `go to home`, `take me home` and `home` are the same command.

## Open and close apps

Say `open <name>` (also *launch, start, go to, play, show, run, switch to, put on*), or just the name. Say `close <name>` (also *quit, exit, kill, shut down, stop*) to stop the app.

| App | Say |
|---|---|
| Netflix | netflix |
| Prime Video | prime video, prime, amazon |
| Hotstar | hotstar, disney |
| YouTube | youtube |
| SmartTube | smarttube |
| Spotify | spotify |
| Crunchyroll | crunchyroll |
| VLC | vlc |
| Weather | weather |
| Brightness | brightness |
| Storage | storage, capacity |
| Play Store | play store |

`close`, `close app`, `close this`, `quit` or `exit` on their own close whatever is on screen (the launcher and system apps are never closed; you are taken Home instead).

## Keys

Add a number or *N times* to repeat, for example `volume up 5` or `down three times` (at most 20; one to ten can be said as words).

| Action | Say |
|---|---|
| Volume up | volume up, louder, turn it up, increase volume |
| Volume down | volume down, quieter, turn it down, decrease volume, lower volume |
| Mute / unmute | mute, unmute, silence |
| Pause | pause, stop |
| Play | play, resume, continue |
| Next | next, skip |
| Previous | previous, last song |
| Fast forward | fast forward, forward |
| Rewind | rewind |
| Home | home |
| Back | back |
| Up | up |
| Down | down |
| Left | left |
| Right | right |
| OK | ok, okay, select, enter, confirm |
| Menu | menu |

## Volume level and typing

- `volume 30` or `set volume to 30`: sets the TV volume (0-100, digits).
- `type hello world`: types the text into the box that has focus on the TV (letters, digits, space and `. _ @ -` only).
