#!/usr/bin/env bash
# Re-encode a video into a phone-friendly live-wallpaper loop: no audio, 30 fps, at most 720 px wide, H.264 main.
# The launcher plays one muted looping MediaPlayer; a big or high-fps source wastes battery and decoder time on
# a low-end SoC. Typical result: 2 to 5 MB for a 10 to 20 second loop.
#
#   tools/encode-wallpaper.sh input.mp4 output.mp4
#
# Needs ffmpeg. Raise -crf (lower quality, smaller file) up to about 34 if the result is too big.
set -eu
IN="${1:?usage: encode-wallpaper.sh input.mp4 output.mp4}"
OUT="${2:?usage: encode-wallpaper.sh input.mp4 output.mp4}"
ffmpeg -v error -y -i "$IN" -an -r 30 -vf "scale='min(720,iw)':-2" \
  -c:v libx264 -profile:v main -level 4.0 -preset slow -crf 31 -pix_fmt yuv420p -movflags +faststart "$OUT"
ls -la "$OUT"
