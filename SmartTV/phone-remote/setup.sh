#!/data/data/com.termux/files/usr/bin/bash
# Run once in Termux:  curl -s http://<laptop>:8000/setup.sh | bash -s <laptop>:8000
# Installs python and adb, fetches the remote's three files and writes start.sh.
set -e
src="${1:?usage: setup.sh <laptop-ip>:8000}"
yes | pkg install -y python android-tools
mkdir -p ~/tv-remote && cd ~/tv-remote
for f in server.py commands.py index.html; do curl -sfO "http://$src/$f"; done
cat > start.sh <<'EOS'
#!/data/data/com.termux/files/usr/bin/bash
# Keeps Termux alive and runs the remote; open http://localhost:8765 in Chrome.
cd ~/tv-remote
termux-wake-lock
adb start-server >/dev/null 2>&1
exec python server.py
EOS
chmod +x start.sh
# Autostart at phone boot: only runs if the Termux:Boot app (F-Droid) is installed and opened once.
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/start-remote.sh <<'EOB'
#!/data/data/com.termux/files/usr/bin/bash
~/tv-remote/start.sh
EOB
chmod +x ~/.termux/boot/start-remote.sh
echo "Done. Start it with:  ~/tv-remote/start.sh"
