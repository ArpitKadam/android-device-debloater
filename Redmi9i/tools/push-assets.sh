#!/usr/bin/env bash
# Copy the theme assets onto the phone. The launcher and the Files app read them from /sdcard/GrandLine/.
#
#   tools/push-assets.sh [theme-assets-dir]      (default: ./theme-assets)
#
# Expected layout is described in theme-assets/README.md. Missing folders are skipped; the apps draw their
# own fallbacks for anything absent.
set -u
ADB="${ADB:-adb}"
SRC="${1:-theme-assets}"
export MSYS_NO_PATHCONV=1
DEST=/sdcard/GrandLine

push_dir() { # local-folder device-subfolder
  [ -d "$SRC/$1" ] || { echo "skip $1 (not found)"; return; }
  "$ADB" shell mkdir -p "$DEST/$2"
  "$ADB" push "$SRC/$1/." "$DEST/$2/" | tail -1
}

push_dir posters posters
push_dir marks marks
push_dir icons icons
push_dir wallpapers/live wallpapers
for f in "$SRC"/wallpapers/*.jpg "$SRC"/wallpapers/*.png; do
  [ -f "$f" ] && { "$ADB" shell mkdir -p "$DEST/wallpapers"; "$ADB" push "$f" "$DEST/wallpapers/" | tail -1; }
done
"$ADB" shell ls -R "$DEST" | head -60
