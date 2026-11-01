#!/usr/bin/env python3
"""Phone remote for the TV: a page on the phone (hold to talk, buttons) and the adb calls behind it.

Runs in Termux on the phone (pkg install python android-tools) or on the laptop for testing (set ADB to the adb.exe
path). Listens on 127.0.0.1 only: the page is opened on the same device. The TV's address changes with every
network, so it is remembered and, when it stops answering, found again by scanning the local /24 for an open
adb port whose model reads UnionTV.
"""
import json
import os
import re
import socket
import subprocess
import sys
import threading
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import commands

ADB = os.environ.get("ADB", "adb")
PORT = int(os.environ.get("PORT", "8765"))
MODEL = os.environ.get("TV_MODEL", "UnionTV")
REMEMBER = os.path.expanduser("~/.tv-remote-host")
HERE = os.path.dirname(os.path.abspath(__file__))
LOCK = threading.Lock()
tv = {"host": None}


def adb(*args, host=None, timeout=8):
    cmd = [ADB] + (["-s", host + ":5555"] if host else []) + list(args)
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout + p.stderr).strip()
    except (subprocess.TimeoutExpired, FileNotFoundError) as e:
        return 1, str(e)


def is_tv(host):
    """True when host answers adb as the TV. 'unauthorized' means the TV still waits for its Allow prompt."""
    adb("connect", host + ":5555", timeout=6)
    rc, out = adb("shell", "getprop", "ro.product.model", host=host, timeout=6)
    if "unauthorized" in out:
        raise PermissionError("Accept the USB debugging prompt on the TV (tick Always allow).")
    return rc == 0 and out.strip() == MODEL


def local_prefix():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))   # no packet is sent; this only picks the Wi-Fi interface
        return s.getsockname()[0].rsplit(".", 1)[0]
    finally:
        s.close()


def port_open(host):
    try:
        with socket.create_connection((host, 5555), timeout=0.5):
            return True
    except OSError:
        return False


def find_tv():
    """The TV's address: the remembered one if it still answers, else a scan of this network."""
    saved = open(REMEMBER).read().strip() if os.path.exists(REMEMBER) else None
    if saved and is_tv(saved):
        return saved
    prefix = local_prefix()
    hosts = [f"{prefix}.{i}" for i in range(1, 255)]
    with ThreadPoolExecutor(64) as pool:
        candidates = [h for h, up in zip(hosts, pool.map(port_open, hosts)) if up]
    for host in candidates:
        if is_tv(host):
            with open(REMEMBER, "w") as f:
                f.write(host)
            return host
    return None


# Android key code -> (input node, Linux scancode). `input keyevent` starts a Java VM on the TV and takes 1.7 s;
# sendevent is a native binary and takes 0.4 s. The shell user may write /dev/input/event* (group input). Measured on
# this TV (measured with sendevent and the window-manager log): event0 is the IR remote's node, event4 a generic keyboard that also maps the media keys.
# ponytail: node numbers are fixed; if a reboot renumbers them keys do nothing, so re-measure or drop to `input keyevent`.
FAST_KEYS = {
    19: ("event0", 103), 20: ("event0", 108), 21: ("event0", 105), 22: ("event0", 106), 23: ("event0", 28),
    4: ("event0", 158), 3: ("event0", 102), 82: ("event0", 139),
    24: ("event0", 115), 25: ("event0", 114), 164: ("event0", 113),
    126: ("event0", 207), 127: ("event0", 201),
    87: ("event4", 163), 88: ("event4", 165), 90: ("event4", 208), 89: ("event4", 168),
}


# Never force-stopped by "close app": the launcher and the TV's own and system packages.
PROTECTED = ("com.gathin.tvhome", "android", "com.android.", "com.tcl.", "com.mediatek.", "com.google.android.tvlauncher")


def key_command(code, times):
    """Shell text that presses a key `times` times: native sendevent when the key is known, else input keyevent."""
    if code not in FAST_KEYS:
        return ["input", "keyevent", *([str(code)] * times)]
    node, scan = FAST_KEYS[code]
    press = f"sendevent /dev/input/{node} 1 {scan} 1; sendevent /dev/input/{node} 0 0 0; "             f"sendevent /dev/input/{node} 1 {scan} 0; sendevent /dev/input/{node} 0 0 0"
    return [";".join([press] * times)]


def tv_shell(*args, timeout=8):
    """Run a command on the TV. The remembered address is tried first; only when adb says the device is gone
    ("error: ..." or a timeout) is the TV looked for again, and the command retried once."""
    for _ in range(2):
        if not tv["host"]:
            tv["host"] = find_tv()
            if not tv["host"]:
                raise ConnectionError("TV not found: is it on and on the same Wi-Fi?")
        rc, out = adb("shell", *args, host=tv["host"], timeout=timeout)
        if not (out.startswith("error:") or "timed out" in out or "cannot connect" in out):
            return out
        tv["host"] = None
    raise ConnectionError("TV not answering: is it on and on the same Wi-Fi?")


def run(action):
    """Do one parsed action on the TV; returns a short sentence for the page."""
    with LOCK:
        kind = action[0]
        if kind == "key":
            tv_shell(*key_command(action[1], action[2]))
            return f"key {action[1]} x{action[2]}"
        if kind == "app":
            out = tv_shell("monkey", "-p", action[1], "-c", "android.intent.category.LEANBACK_LAUNCHER", "1", timeout=15)
            if "No activities found" in out:
                raise LookupError(f"{action[1]} is not installed or has no launcher entry")
            return f"opened {action[1]}"
        if kind == "close":
            tv_shell("am", "force-stop", action[1])
            return f"closed {action[1]}"
        if kind == "closefront":
            out = tv_shell("dumpsys window | grep mCurrentFocus")
            found = re.search(r"u0 ([A-Za-z0-9._]+)/", out)
            pkg = found.group(1) if found else ""
            if not pkg or pkg.startswith(PROTECTED):
                tv_shell(*key_command(3, 1))   # the launcher or the system is in front: only go Home
                return "nothing to close, went Home"
            tv_shell("am", "force-stop", pkg)
            return f"closed {pkg}"
        if kind == "volume":
            tv_shell("media", "volume", "--show", "--stream", "3", "--set", str(action[1]))
            return f"volume {action[1]}"
        if kind == "type":
            safe = re.sub(r"[^A-Za-z0-9 ._@-]", "", action[1]).replace(" ", "%s")
            tv_shell("input", "text", safe)
            return f"typed {action[1]}"
    raise ValueError(kind)


class Handler(BaseHTTPRequestHandler):
    allowed = {f"localhost:{PORT}", f"127.0.0.1:{PORT}"}

    def log_message(self, fmt, *args):
        pass

    def trusted(self):
        """The page only: right Host (no DNS rebinding), no foreign Origin, and a header another site cannot add."""
        origin = self.headers.get("Origin")
        return (self.headers.get("Host") in self.allowed
                and (origin is None or origin.replace("http://", "") in self.allowed)
                and self.headers.get("X-Remote") == "1")

    def reply(self, code, body, kind="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", "/index.html") and self.headers.get("Host") in self.allowed:
            with open(os.path.join(HERE, "index.html"), "rb") as f:
                return self.reply(200, f.read(), "text/html; charset=utf-8")
        if self.path == "/status" and self.trusted():
            return self.reply(200, {"tv": tv["host"] is not None})
        if self.path == "/commands" and self.trusted():
            return self.reply(200, {"suggestions": commands.suggestions()})
        self.reply(404, {"error": "not found"})

    def do_POST(self):
        if not self.trusted():
            return self.reply(403, {"error": "forbidden"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            if self.path == "/say":
                action = commands.parse(str(body.get("text", "")))
                if action is None:
                    return self.reply(200, {"ok": False, "msg": "I did not understand that"})
            elif self.path == "/key":
                action = ("key", int(body["code"]), max(1, min(int(body.get("times", 1)), commands.MAX_REPEAT)))
            elif self.path == "/app":
                if not re.fullmatch(r"[A-Za-z0-9._]+", str(body["pkg"])):
                    raise ValueError("bad package name")   # it reaches a device shell
                action = ("app", str(body["pkg"]))
            else:
                return self.reply(404, {"error": "not found"})
            self.reply(200, {"ok": True, "msg": run(action)})
        except (PermissionError, ConnectionError, LookupError) as e:
            self.reply(200, {"ok": False, "msg": str(e)})
        except (ValueError, KeyError, json.JSONDecodeError) as e:
            self.reply(400, {"error": str(e)})


if __name__ == "__main__":
    print(f"Phone remote on http://localhost:{PORT}  (adb: {ADB})", file=sys.stderr)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
