# Reference device: Redmi 9i

This is the phone the project was built and tested on. Nothing here is required to use the project on another
phone, but it shows the kind of constraints to look for (CPU ABI, RAM, OEM skin, locked bootloader) and how they
shaped the decisions. Read over adb on 2026-11-01. Rows in the specs table come from the phone,
except screen size (6.53"), battery capacity (5000 mAh), dual SIM and Wi-Fi
b/g/n, which are the published Redmi 9i specs and were not read from the
device. Everything under "Feasibility" and "Use cases" is engineering judgment
(performance numbers are estimates, not benchmarks) until tested on-device.

## Connection

- adb 1.0.41, Windows 11 host.
- Transport **USB**. Decided to stay on USB; Wi-Fi adb is not needed.
- Not rooted (`su` missing). Bootloader locked (`ro.boot.flash.locked=1`,
  verified boot `green`). adb runs as shell uid 2000.
- Already installed by user: Termux 0.119.0-beta.3 (targetSdk 28), Termux:Boot
  0.8.1, F-Droid, Indus Appstore. Only 4 third-party packages total.

## Measured specs

| Item | Value |
| --- | --- |
| Model | Redmi 9i, `M2006C3LII`, codename `dandelion` (India build `dandelion_in`) |
| OS | Android 10 (API 29), MIUI 12 (`V12.0.26.0.QCDINXM`), India region |
| Security patch | 2023-02-01 (build date 2023-02-09). Effectively end-of-life |
| SoC | MediaTek Helio G25, `MT6762G` (platform `mt6765`) |
| CPU | 8x Cortex-A53 (`CPU part 0xd03`), ARMv8. Cores 0-3 up to 2.0 GHz, cores 4-7 up to 1.5 GHz |
| CPU features | NEON, VFPv4, AES, SHA1/2, CRC32. **No** dotprod, fp16 arith, i8mm, SVE |
| **Userspace ABI** | **32-bit only**: `armeabi-v7a, armeabi`. No `/system/lib64`, `getconf LONG_BIT` = 32 |
| Kernel | Linux 4.9.190-perf, reports `armv8l` (64-bit CPU running 32-bit userland) |
| GPU | PowerVR Rogue GE8320, OpenGL ES 3.2. No usable Vulkan/OpenCL compute for ML |
| RAM | 3.88 GB total (4 GB variant). ~2.17 GB in use at idle with MIUI running |
| Swap | 2 GB zram (~370 MB in use when sampled) |
| Storage | 52 GB `/data` (eMMC `mmcblk0`), 28 GB used, 23 GB free. No SD card present |
| Display | 6.53" 720x1600, 320 dpi, 60 Hz, FLAG_SECURE on screen |
| Battery | Li-poly, 5000 mAh class. 36% at sample, 34.3 C, health good, USB 500 mA |
| Modem | MediaTek `MOLY.LR12A.R3.MP.V107.5.P133`, GSM + IMS (VoLTE), dual SIM |
| Radios | Wi-Fi (b/g/n, Wi-Fi Direct), Bluetooth LE, USB host (OTG). **No NFC** |
| Sensors seen | Accelerometer. No gyroscope or compass reported |
| Misc | Rear flash present. No fingerprint feature flag reported. Treble on, dynamic partitions on, A-only (no A/B) |
| Packages | 266 total, 262 system, 4 user-installed, 2 already disabled |
| Timezone/locale | Asia/Kolkata, en-GB |

## What the specs mean (read this before planning anything)

1. **32-bit userspace is the main constraint.** Termux on this phone is an
   `arm` (32-bit) install. Many prebuilt AI binaries only ship `aarch64` and
   will not run. Anything that builds from source (llama.cpp, whisper.cpp,
   rclone) is fine; pip wheels with native code often are not available for
   `armv7l`.
2. **Cortex-A53 is slow for inference.** In-order cores, no dotprod, no fp16.
   Memory bandwidth on LPDDR3/4 is low. Token speed is bandwidth-bound.
3. **Usable RAM for a model is about 1.5-2 GB**, not 4 GB. Debloating can raise
   it a little. A model plus context must fit with room for Android to avoid
   the low-memory killer killing Termux.
4. **Android 10 is a good target for Termux:** no phantom-process killer (that
   starts in Android 12), and Termux (targetSdk 28) can still exec downloaded
   binaries. The remaining killer is MIUI battery management.
5. **No root, locked bootloader.** Debloat and everything below stays inside
   what adb shell and a normal app can do. Bootloader unlock needs a Mi account
   plus Xiaomi's mandatory waiting period and wipes the phone.

## Feasibility

| Goal | Verdict | Notes |
| --- | --- | --- |
| Debloat | **Yes, easy** | `adb shell pm uninstall -k --user 0 <pkg>` removes for the current user without root, reversible with `adb shell cmd package install-existing <pkg>`. Keep: dialer, SMS, Settings, SystemUI, `com.miui.home` until a replacement launcher is set, Google Play services if any Google app is used, WebView. 262 system packages to triage. Gains: RAM, battery, no ads. Does not free storage (`-k` keeps the APK in `/system`) |
| Replace launcher | **Yes** | Install a launcher (Olauncher, Lawnchair, Niagara, etc), set as default, then optionally disable `com.miui.home` only after the replacement works. Never disable it first: no launcher = no home screen. MIUI may hide the default-home chooser; `adb shell cmd package set-home-activity <pkg/activity>` works |
| Termux | **Yes, already installed** | Grant "No restrictions" battery, MIUI Autostart, lock in recents. Use `termux-wake-lock` for long jobs. Termux:API app is needed for SMS/TTS/notifications/sensors |
| rclone to Google Drive | **Yes, easy** | `pkg install rclone` (Go, ships for arm). Create your own Google Cloud OAuth client id/secret (shared rclone id is rate-limited). Headless auth: run `rclone authorize "drive"` on this PC, paste the token into the phone's `rclone config`. No FUSE without root, so use `sync`/`copy`/`bisync`/`serve webdav`, not `mount`. Schedule with cron (`pkg install cronie termux-services`) or Termux:Boot |
| Telegram | **Yes, trivial** | Bot API over HTTPS long-polling needs no open port and works behind mobile NAT. Use `python-telegram-bot`, `aiogram`, or plain `curl`. User-account API (Telethon/TDLib) also works; needs api_id from my.telegram.org. CPU cost is negligible |
| Laya (small decision model) | **Probably, unproven** | Convai's ~421M non-autoregressive ONNX decision model; published figures claim ~1 GB RAM and ~33 ms/decision on unstated hardware. RAM fits the ~1.5-2 GB budget but leaves little headroom. The risk is the runtime: no official `onnxruntime` wheel for 32-bit ARM Termux, so it needs a source build or an Android app with `onnxruntime-android`. Latency on A53 will be much worse than the claim. Needs an on-device spike |
| Small LLM (general chat) | **Deferred**; feasible, small only | Build llama.cpp from source in Termux (or `pkg install llama-cpp` if the repo has an arm build). Realistic: 0.5B-1.5B at Q4 (~0.4-1.1 GB). Estimated 3-10 tok/s for 0.5B, 1-4 tok/s for 1-1.5B on this CPU. 3B+ does not fit comfortably: expect swapping and kills. Candidates: Qwen2.5-0.5B/1.5B-Instruct, SmolLM2 360M/1.7B, Gemma-3-1B-it, Llama-3.2-1B-Instruct. For "decisions", use GBNF grammar or JSON schema constrained output so a tiny model emits a valid label. "Laya" in the request was read as a small decision-style LLM; confirm which model you meant |
| STT | **Yes** | Best fit: Vosk small models (~40-50 MB, streaming, fast on weak CPUs) or sherpa-onnx. whisper.cpp `tiny`/`base` (quantized) works for voice notes and batch, roughly near real-time for `tiny` on A53, slower for `base`; no streaming-grade latency. Build from source |
| TTS | **Yes, with caveats** | Easiest: `termux-tts-speak` (uses the phone's built-in Google/Mi TTS engine, no model cost) or `espeak-ng` (robotic). Better quality: Piper voices (~20-60 MB, ONNX). Piper's prebuilt Linux binaries are glibc and 64-bit-heavy, so run them in a proot Debian armhf, or via sherpa-onnx. Expect several seconds per sentence |
| Linux-like OS | **Yes, as userland. No, as a replacement OS** | `proot-distro` gives Debian/Alpine/Ubuntu (armhf) inside Termux, no root. 10-30% slower than native Termux, no systemd, no KVM, no GPU. Replacing Android with a real Linux (postmarketOS, Droidian) is not realistic: locked bootloader, 4.9 kernel, no known mainline port for MT6765 on this model. A custom Android ROM (community builds for the Redmi 9A family may exist) is possible only after a bootloader unlock; check XDA before committing. Not recommended: the phone loses banking-app friendliness and warranty for little gain |

## Operational limits to respect

- **Heat/throttling:** sustained 8-core load on a 12nm A53 phone throttles. Run
  inference on 4 threads (`-t 4`) and pin to the fast cluster if possible.
- **MIUI kills background apps:** set Termux and Termux:Boot to "No
  restrictions", enable Autostart, lock the app in the recents screen.
- **Always-plugged use:** if the phone becomes a server, cap charge at ~80% or
  remove the battery risk; a swollen cell is a real failure mode.
- **Storage:** 23 GB free. A 1B Q4 model is ~0.8 GB, whisper-base ~60 MB,
  a Debian proot ~1-2 GB. Fine.
- **Security:** Android 10 with a 2023-02 patch. Do not expose services to the
  internet directly. Use Telegram long-polling or a tunnel with auth.

## Where small local models earn their keep on this phone

Ranked by how well they fit the hardware (small, latency-tolerant, private,
always-on, no GPU needed).

1. **Intent router / decision model.** Classify an incoming Telegram message,
   SMS, or voice command into a fixed action set (`reply`, `ignore`, `remind`,
   `run_script`, `escalate`) with grammar-constrained JSON. A 0.5B-1.5B model is
   enough for a closed label set. This is the best match for the hardware.
2. **Voice-note pipeline via Telegram.** User sends a voice note to a bot,
   whisper.cpp transcribes it on the phone, a small LLM summarises or extracts
   tasks, the bot replies with text (or Piper audio). Latency is minutes, not
   seconds, and that is fine for async use.
3. **SMS / notification triage.** Spam/OTP/promo classification and a daily
   digest of what mattered, generated on-device so message content never leaves
   the phone. Run on a schedule, not per message.
4. **Hands-free voice commands.** Vosk streaming STT, then tiny LLM to map the
   phrase to a `termux-api` action (flashlight, call, SMS, timer, media). Works
   offline, useful while driving or cooking.
5. **Private document search over Google Drive.** rclone pulls selected folders,
   a small embedding model (MiniLM-class, ONNX) indexes them, a 1B LLM answers
   questions with retrieved snippets. Slow to index, fine to query.
6. **Always-on home/IoT brain.** The phone replaces a Raspberry Pi: MQTT or
   HTTP to smart devices, small LLM turns "make it cosy" into device commands,
   screen off, battery as built-in UPS.
7. **Offline accessibility and language help.** Dictation (Vosk/whisper),
   read-aloud of saved articles (TTS), short translation or rephrasing with a
   1B model when there is no signal.
8. **Scheduled digests.** Cron job at 8am: pull RSS/Drive notes, summarise with
   the small model, send via Telegram.

Poor fits (do not plan around them): open-ended chat quality, long-context
summarisation, code generation, image generation or vision models, 7B+ models,
real-time streaming TTS, anything needing a GPU.

