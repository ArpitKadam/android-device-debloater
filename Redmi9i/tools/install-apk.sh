#!/usr/bin/env bash
# Install an APK over USB and, on phones that ask "Install this app via USB?", tap the confirm button for you.
#
#   tools/install-apk.sh path/to/app.apk
#
# Needs: adb on PATH (or set ADB=/path/to/adb), USB debugging on, the screen awake and unlocked.
# Some vendor skins show a confirmation dialog on the phone for a few seconds and fail the install with
# INSTALL_FAILED_USER_RESTRICTED when nobody taps it. This script finds the dialog's negative/positive
# button with uiautomator and taps it. If your phone words the dialog differently, tap it by hand.
set -u
ADB="${ADB:-adb}"
APK="${1:?usage: install-apk.sh path/to/app.apk}"
export MSYS_NO_PATHCONV=1 # Git Bash on Windows must not rewrite device paths

log="$(mktemp)"
( "$ADB" install -r "$APK" > "$log" 2>&1 & )

for _ in $(seq 1 10); do
  sleep 1.2
  # resource-id android:id/button2 is the confirm button on the install-via-USB dialog we have seen.
  bounds=$("$ADB" shell "uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; grep -o 'resource-id=\"android:id/button2\"[^>]*bounds=\"[^\"]*\"' /sdcard/ui.xml" 2>/dev/null \
    | grep -o 'bounds="[^"]*"' | head -1)
  if [ -n "$bounds" ]; then
    set -- $(echo "$bounds" | grep -o '[0-9]*') # left top right bottom
    "$ADB" shell input tap $((($1 + $3) / 2)) $((($2 + $4) / 2))
    break
  fi
  [ -s "$log" ] && break # adb already finished (no dialog on this phone)
done

sleep 5
cat "$log"
grep -q "^Success" "$log"
