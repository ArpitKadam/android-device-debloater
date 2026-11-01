"""Turns what was said ("open netflix", "close youtube", "volume up 5", "go to home") into one TV action.

No I/O, so it is tested on its own. The tables below are the single source: the drop-down on the page and
COMMANDS.md are generated from them (python commands.py > COMMANDS.md); test_commands.py fails if the file is stale.
"""
import re
import sys

# (name shown, package, spoken names). Spoken names are matched with the spaces removed, so "you tube" and
# "hot star" still hit.
APPS = [
    ("Netflix", "com.netflix.ninja", ["netflix"]),
    ("Prime Video", "com.amazon.amazonvideo.livingroom", ["prime video", "prime", "amazon"]),
    ("Hotstar", "in.startv.hotstar", ["hotstar", "disney"]),
    ("YouTube", "com.google.android.youtube.tv", ["youtube"]),
    ("SmartTube", "org.smarttube.stable", ["smarttube"]),
    ("Spotify", "com.spotify.tv.android", ["spotify"]),
    ("Crunchyroll", "com.crunchyroll.crunchyroid", ["crunchyroll"]),
    ("VLC", "org.videolan.vlc", ["vlc"]),
    ("Weather", "io.github.sumyabazar1.weather", ["weather"]),
    ("Brightness", "io.github.maxhayim.brightness", ["brightness"]),
    ("Storage", "com.lionlev.tv.capacity", ["storage", "capacity"]),
    ("Play Store", "com.android.vending", ["play store"]),
]

# (name shown, Android key code, phrases). A phrase matches the whole sentence once filler words and counts are taken
# out, so "go to home", "take me home" and "home" are one command.
KEYS = [
    ("Volume up", 24, ["volume up", "louder", "turn it up", "increase volume"]),
    ("Volume down", 25, ["volume down", "quieter", "turn it down", "decrease volume", "lower volume"]),
    ("Mute / unmute", 164, ["mute", "unmute", "silence"]),
    ("Pause", 127, ["pause", "stop"]),
    ("Play", 126, ["play", "resume", "continue"]),
    ("Next", 87, ["next", "skip"]),
    ("Previous", 88, ["previous", "last song"]),
    ("Fast forward", 90, ["fast forward", "forward"]),
    ("Rewind", 89, ["rewind"]),
    ("Home", 3, ["home"]),
    ("Back", 4, ["back"]),
    ("Up", 19, ["up"]),
    ("Down", 20, ["down"]),
    ("Left", 21, ["left"]),
    ("Right", 22, ["right"]),
    ("OK", 23, ["ok", "okay", "select", "enter", "confirm"]),
    ("Menu", 82, ["menu"]),
]

OPEN_WORDS = ("open", "launch", "start", "go to", "play", "show", "run", "switch to", "put on")
CLOSE_WORDS = ("close", "quit", "exit", "kill", "shut down", "shut", "stop")
# "close" alone, or with only these after it, closes whatever is on screen ("stop" alone stays Pause).
FRONT_WORDS = {"app", "this", "it", "the", "current", "window", "please", "tv", "that"}
NUMBERS = {w: i for i, w in enumerate("zero one two three four five six seven eight nine ten".split())}
MAX_REPEAT = 20
# Dropped before comparing, so "press the back button" is "back". Counts go too: they set how many times.
FILLER = {"please", "times", "time", "a", "the", "it", "now", "once", "twice",
          "go", "to", "take", "me", "press", "hit", "tap", "click", "button", "key"}

ALIAS_PACKAGE = {a.replace(" ", ""): pkg for _, pkg, aliases in APPS for a in aliases}


def _clean(text):
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ]", " ", text.lower())).strip()


def _core(words):
    """The words that name the action: counts and filler dropped, so "turn it down three times" is "turn down"."""
    return " ".join(w for w in words if w not in FILLER and not w.isdigit() and w not in NUMBERS)


def _count(words):
    """The number said or typed in the phrase, or None."""
    for w in words:
        if w.isdigit():
            return int(w)
        if w in NUMBERS:
            return NUMBERS[w]
    return None


def _app_in(squashed):
    for alias in sorted(ALIAS_PACKAGE, key=len, reverse=True):
        if alias in squashed:
            return ALIAS_PACKAGE[alias]
    return None


def parse(text):
    """("app", pkg), ("close", pkg), ("closefront",), ("key", code, times), ("volume", 0-100), ("type", text);
    None when not understood."""
    raw = text.strip()
    t = _clean(raw)
    if not t:
        return None
    if t.startswith("type "):
        return ("type", raw[raw.lower().index("type") + 5:].strip())
    words = t.split()
    n = _count(words)
    squashed = t.replace(" ", "")

    if (t.startswith("volume ") or t.startswith("set volume")) and n is not None \
            and "up" not in words and "down" not in words:
        return ("volume", min(n, 100))

    for w in CLOSE_WORDS:
        if t.startswith(w + " "):
            pkg = _app_in(t[len(w):].replace(" ", ""))
            if pkg:
                return ("close", pkg)
            if w not in ("stop", "shut", "shut down") and set(t[len(w):].split()) <= FRONT_WORDS:
                return ("closefront",)
    if t in ("close", "quit", "exit"):
        return ("closefront",)

    pkg = _app_in(squashed)
    if pkg and (any(t.startswith(v + " ") for v in OPEN_WORDS) or squashed in ALIAS_PACKAGE):
        return ("app", pkg)

    core = _core(words)
    for _, code, phrases in KEYS:
        if core in {_core(p.split()) for p in phrases}:
            return ("key", code, min(n if n else 1, MAX_REPEAT))
    return None


def suggestions():
    """What the page's drop-down offers while typing: every stored command once, in a natural wording."""
    out = []
    for _, _, aliases in APPS:
        out += [f"open {aliases[0]}", f"close {aliases[0]}"]
    for _, _, phrases in KEYS:
        out += phrases
    return out + ["volume 30", "volume up 5", "close app", "type hello"]


def markdown():
    lines = ["# What the phone remote understands", "",
             "Generated from `commands.py`: run `python commands.py > COMMANDS.md` after changing the tables.",
             "Case and punctuation do not matter. Words like *go to*, *take me*, *the*, *please*, *button* are "
             "ignored, so `go to home`, `take me home` and `home` are the same command.", "",
             "## Open and close apps", "",
             "Say `open <name>` (also *launch, start, go to, play, show, run, switch to, put on*), or just the name. "
             "Say `close <name>` (also *quit, exit, kill, shut down, stop*) to stop the app.", "",
             "| App | Say |", "|---|---|"]
    lines += [f"| {name} | {', '.join(aliases)} |" for name, _, aliases in APPS]
    lines += ["", "`close`, `close app`, `close this`, `quit` or `exit` on their own close whatever is on screen "
              "(the launcher and system apps are never closed; you are taken Home instead).", "",
              "## Keys", "",
              "Add a number or *N times* to repeat, for example `volume up 5` or `down three times` (at most "
              f"{MAX_REPEAT}; one to ten can be said as words).", "",
              "| Action | Say |", "|---|---|"]
    lines += [f"| {name} | {', '.join(phrases)} |" for name, _, phrases in KEYS]
    lines += ["", "## Volume level and typing", "",
              "- `volume 30` or `set volume to 30`: sets the TV volume (0-100, digits).",
              "- `type hello world`: types the text into the box that has focus on the TV "
              "(letters, digits, space and `. _ @ -` only).", ""]
    return "\n".join(lines)


if __name__ == "__main__":
    sys.stdout.write(markdown())
