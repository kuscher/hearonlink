#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Records the demo video Google Play asks for in the foreground-service declaration
# (store-submission/forms/foreground-service.md), on a Googlebook over adb.
#
#   tools/play-video.sh record     take the screen for about a minute and record the walkthrough
#   tools/play-video.sh process    (re)build the final video from the last take: captions + privacy blur
#   tools/play-video.sh            both
#
# Before recording: wear your AirPods (HearOn Link must say Connected) and don't touch the
# Googlebook until your windows come back. About 35 seconds in,
# when the small Quick Settings panel has closed, put both AirPods in the case and close the lid:
# the last scene shows the service stopping on its own. (Skip that and the video just ends earlier.)
#
# What it does on the screen, with real taps (adb input) on the real UI:
#   1. shows the desktop (your windows are minimised, and restored at the end),
#   2. opens the notification panel: HearOn Link's ongoing notification is there because the AirPods are connected,
#   3. opens HearOn Link and switches the listening mode,
#   4. closes the window, opens the panel again and switches the mode from the notification,
#   5. opens the Quick Settings tile's small panel,
#   6. waits for the AirPods to go back in the case and shows that the notification is gone.
# The final video follows the other apps' declaration videos (kuscher/googlebook-tech scripts/play/videos):
# a title card, then the take with a numbered caption band under it, touches shown as dots. Everything
# outside HearOn Link's window and the system panels is blurred (wallpaper, widgets, other apps' icons,
# the status bar's chips). The raw take stays in store-submission/video/out/, which git ignores: don't publish it.
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."
PKG=io.github.kuscher.hearonlink
OUT=store-submission/video/out
RAW=$OUT/raw.mp4
SCENES=$OUT/scenes.tsv            # start end x y w h caption   (seconds from the start of the take; px)
FINAL=$OUT/hearonlink-foreground-service.mp4
FONT=app/src/main/res/font/hearon_sans.ttf
DEV=/sdcard/Download/hearonlink-fgs-take.mp4
ENVF="$HOME/.config/vscodebook/android.env"
[ -f "$ENVF" ] && . "$ENVF"
export ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET:-localfilesystem:$HOME/.local/state/vscodebook/adb.sock}
SER=$(cat "$HOME/.config/vscodebook/adb-serial")
A() { adb -s "$SER" "$@"; }
sh() { A shell "$@" </dev/null; }
now() { date +%s.%N; }
T0=0
t() { echo "$(now) $T0" | awk '{printf "%.2f", $1 - $2}'; }

# The bounds "x y w h" of a window whose title contains $1 (from dumpsys), or nothing.
frame() {
  sh "dumpsys window windows" | python3 -c '
import re, sys
hit = False
for line in sys.stdin:
    if line.startswith("  Window #"): hit = sys.argv[1] in line
    m = hit and re.search(r"mBounds=Rect\((\d+), (\d+) - (\d+), (\d+)\)", line)
    if m:
        x1, y1, x2, y2 = map(int, m.groups()); print(x1, y1, x2 - x1, y2 - y1); break
' "$1"
}

# The centre "x y" and bounds of the first node whose text or description contains $1 in the focused window.
node() {
  sh "uiautomator dump /sdcard/Download/hol-ui.xml >/dev/null 2>&1; cat /sdcard/Download/hol-ui.xml; rm -f /sdcard/Download/hol-ui.xml" |
    python3 -c '
import re, sys
want = sys.argv[1].lower()
xml = sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", xml):
    n = m.group(0)
    label = " ".join(re.findall(r"(?:text|content-desc)=\"([^\"]*)\"", n)).replace("&#10;", " ").lower()
    b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
    if want in label and b:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2, x1, y1, x2 - x1, y2 - y1); break
' "$1"
}
tap() { local p; p=$(node "$1"); [ -n "$p" ] || return 1; set -- $p; sh "input touchscreen tap $1 $2"; }

# The system panel (notifications or Quick Settings) is a popup below the status bar on the right.
panel_rect() {
  sh "uiautomator dump /sdcard/Download/hol-ui.xml >/dev/null 2>&1; cat /sdcard/Download/hol-ui.xml; rm -f /sdcard/Download/hol-ui.xml" |
    python3 -c '
import re, sys
xml = sys.stdin.read()
best = None
for b in re.finditer(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", xml):
    x1, y1, x2, y2 = map(int, b.groups())
    w, h = x2 - x1, y2 - y1
    if 300 <= w <= 900 and h >= 200 and x1 >= 900 and (best is None or w * h > best[2] * best[3]): best = (x1, y1, w, h)
x, y, w, h = best or (1440, 41, 470, 760)
print(max(0, x - 12), max(0, y - 6), w + 24, h + 22)      # below the status bar: its chips stay blurred'
}

scene() { printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" $3 "$4" >> "$SCENES"; }   # start end "x y w h" caption

record() {
  mkdir -p "$OUT"; : > "$SCENES"
  ./hol debug state | grep -q "connected=true" || { echo "HearOn Link isn't connected: put your AirPods in (and check the app says Connected), then run this again."; exit 1; }
  local size; size=$(sh "wm size" | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1); set -- $size; local W=$1 H=$2
  local front; front=$(sh "dumpsys window | grep mCurrentFocus" | sed -n 's/.* \([^ ]*\)\/\([^ }]*\)}.*/\1\/\2/p')
  echo "recording on $SER (${W}x$H); in front now: ${front:-?}"
  local touches; touches=$(sh "settings get system show_touches" | tr -d '\r')
  sh "settings put system show_touches 1"
  sh "am start --user current -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null"; sleep 1.5
  sh "rm -f $DEV"
  A shell "screenrecord --bit-rate 12M --time-limit 150 $DEV" </dev/null >/dev/null 2>&1 &
  local rec=$!
  sleep 1.2; T0=$(now)
  local a b r

  # 1. The notification is there because the AirPods are connected.
  sleep 1; a=$(t)
  sh "cmd statusbar expand-notifications"; sleep 1.2; r=$(panel_rect); sleep 3.5
  b=$(t); scene "$a" "$b" "$r" "AirPods connected: HearOn Link starts its connected-device service and shows this notification (battery, listening mode)."
  sh "cmd statusbar collapse"; sleep 0.8

  # 2. The app, and a mode switch from it.
  a=$(t)
  sh "am start --user current -n $PKG/.ui.MainActivity >/dev/null"; sleep 3
  r=$(frame "$PKG/$PKG.ui.MainActivity"); [ -n "$r" ] || r="0 0 $W $H"
  sleep 1.5
  tap "Noise Cancellation" || ./hol debug mode noise >/dev/null; sleep 3
  b=$(t); scene "$a" "$b" "$r" "The app: battery for each AirPod and the case, and the listening mode. Switching to Noise Cancellation."

  # 3. Window closed: the service keeps the AirPods' control channel open, the notification stays.
  sh "am start --user current -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null"; sleep 1.5
  a=$(t)
  sh "cmd statusbar expand-notifications"; sleep 1.2; r=$(panel_rect); sleep 2
  tap "Transparency" || ./hol debug mode transparency >/dev/null; sleep 3.5
  b=$(t); scene "$a" "$b" "$r" "HearOn Link's window is closed. The service keeps running, so the notification still works: switching to Transparency from it."
  sh "cmd statusbar collapse"; sleep 0.8

  # 4. The Quick Settings tile and its small panel.
  a=$(t)
  sh "cmd statusbar expand-settings"; sleep 1.2; r=$(panel_rect); sleep 2.5
  b=$(t); scene "$a" "$b" "$r" "The Quick Settings tile shows the listening mode and battery, kept current by the service."
  a=$(t)
  sh "cmd statusbar click-tile $PKG/.system.ModeTile"; sleep 2.5
  r=$(frame "$PKG/$PKG.ui.PanelActivity"); [ -n "$r" ] || r="0 0 $W $H"
  sleep 3
  b=$(t); scene "$a" "$b" "$r" "A tap on the tile opens HearOn Link's small panel."
  sh "cmd statusbar collapse; am start --user current -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null"; sleep 1

  # 5. AirPods back in the case: the service stops on its own and the notification goes away.
  echo "now put both AirPods in the case and close the lid (waiting up to 60 s)…"
  local n=0
  while [ $n -lt 30 ] && ./hol debug state | grep -q "connected=true"; do sleep 2; n=$((n + 1)); done
  if [ $n -lt 30 ]; then
    sleep 14   # the service waits 12 s after the link drops before it stops
    a=$(t)
    sh "cmd statusbar expand-notifications"; sleep 1.2; r=$(panel_rect); sleep 4
    b=$(t); scene "$a" "$b" "$r" "AirPods back in their case: the service has stopped by itself and its notification is gone."
    sh "cmd statusbar collapse"; sleep 0.8
  else
    echo "still connected: leaving that scene out"
  fi

  sh "pkill -INT screenrecord" || true
  wait $rec 2>/dev/null || true
  case "$touches" in 0|1) sh "settings put system show_touches $touches" ;; *) sh "settings delete system show_touches" ;; esac
  sleep 1.5
  # Bring the window that was in front back (like clicking its taskbar icon); the desk's other windows return with it.
  [ -n "$front" ] && sh "am start --user current -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n $front >/dev/null 2>&1" || true
  A exec-out cat "$DEV" > "$RAW"; sh "rm -f $DEV"
  echo "took $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$RAW") s -> $RAW"; cat "$SCENES"
}

# Title card, caption bands, blur and cuts: tools/play_video_compose.py.
process() {
  [ -s "$RAW" ] && [ -s "$SCENES" ] || { echo "no take yet: run tools/play-video.sh record"; exit 1; }
  local version code
  version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
  code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
  python3 tools/play_video_compose.py "$RAW" "$SCENES" "$FINAL" "$FONT" "$OUT" "$version ($code)"
}

case "${1:-all}" in
  record) record ;;
  process) process ;;
  all) record; process ;;
  *) sed -n '3,25p' "$0" ;;
esac
