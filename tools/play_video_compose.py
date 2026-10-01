#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Builds the Play declaration video from a take (tools/play-video.sh process calls this).

    python3 tools/play_video_compose.py RAW.mp4 SCENES.tsv FINAL.mp4 FONT.ttf WORK_DIR "0.2.0 (2)"

SCENES.tsv: start, end (seconds of the take), x, y, w, h (the rectangle to keep sharp) and the caption, tab-separated.
The result follows the other apps' declaration videos (kuscher/googlebook-tech scripts/play/videos): a title card,
then each scene with a numbered caption band under it. Everything outside a scene's rectangle is blurred, and the
gaps between scenes are cut.
"""
import pathlib
import subprocess
import sys
import textwrap

from PIL import Image, ImageDraw, ImageFont

raw, scenes, final, fontfile, out, version = sys.argv[1:7]
out = pathlib.Path(out)
ACCENT, BG, INK = (63, 176, 168), (21, 23, 28), (244, 245, 247)
W, H = map(int, subprocess.check_output(
    ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", raw],
    text=True).strip().split(","))
BAND = 150
FW, FH = W, H + BAND


def font(size, weight=500):
    f = ImageFont.truetype(fontfile, size)
    f.set_variation_by_axes([min(144, max(6, size * 0.75)), weight, 0])
    return f


def title_card(path):
    im = Image.new("RGB", (FW, FH), BG)
    g = ImageDraw.Draw(im)
    for i in range(60, 0, -1):          # a soft accent glow, top left
        t = i / 60
        c = tuple(int(BG[k] + (ACCENT[k] - BG[k]) * 0.22 * (1 - t) ** 2) for k in range(3))
        g.ellipse((384 - 1200 * t, 270 - 700 * t, 384 + 1200 * t, 270 + 700 * t), fill=c)
    d = ImageDraw.Draw(im)
    x, y = 180, 300
    d.text((x, y), "HearOn Link", font=font(40, 650), fill=ACCENT)
    y += 84
    for line in textwrap.wrap("HearOn Link's connected-device foreground service", 34):
        d.text((x, y), line, font=font(84, 750), fill=INK)
        y += 96
    y += 30
    what = ("Foreground service type connectedDevice. Shown: the service running while AirPods are connected, with its "
            "notification; the listening mode changed in the app, then from the notification with the app's window "
            "closed; the Quick Settings tile; and the service stopping when the AirPods disconnect.")
    for line in textwrap.wrap(what, 78):
        d.text((x, y), line, font=font(38, 450), fill=(201, 204, 211))
        y += 54
    y += 44
    for fact in (f"Package io.github.kuscher.hearonlink · version {version}",
                 f"Recorded on a Googlebook (Android 17, API 37, {W} × {H}) with AirPods Pro · touches are shown as dots",
                 "Everything except HearOn Link and the system panels is blurred"):
        d.text((x, y), fact, font=font(28, 450), fill=(154, 160, 171))
        y += 46
    im.save(path)


def band(path, n, text):
    im = Image.new("RGB", (FW, BAND), BG)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, FW, 4), fill=ACCENT)
    d.ellipse((60, 45, 124, 109), fill=ACCENT)
    d.text((92, 77), str(n), font=font(32, 750), fill=(255, 255, 255), anchor="mm")
    lines = textwrap.wrap(text, 104)
    assert len(lines) <= 3, f"caption {n} is too long"
    y = 77 - len(lines) * 22
    for line in lines:
        d.text((154, y), line, font=font(34, 500), fill=INK)
        y += 44
    im.save(path)


rows = [line.rstrip("\n").split("\t") for line in open(scenes) if line.strip()]
enc = ["-r", "30", "-c:v", "libx264", "-crf", "20", "-preset", "medium", "-pix_fmt", "yuv420p", "-an"]
title = out / "title.png"
title_card(title)
parts = [out / "part-title.mp4"]
subprocess.run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-framerate", "30", "-t", "5", "-i", str(title),
                "-vf", "fade=t=out:st=4.6:d=0.4,format=yuv420p", *enc, str(parts[0])], check=True)
for i, (a, b, x, y, w, h, caption) in enumerate(rows):
    a, b = float(a), float(b)
    x, y, w, h = (int(float(v)) for v in (x, y, w, h))
    x, y = max(0, x), max(0, y)
    w, h = min(w, W - x) // 2 * 2, min(h, H - y) // 2 * 2
    cap = out / f"cap-{i}.png"
    band(cap, i + 1, caption)
    part = out / f"part-{i}.mp4"
    vf = (f"[0:v]fps=30,split[s][k];[s]boxblur=28:3[bg];[k]crop={w}:{h}:{x}:{y}[keep];[bg][keep]overlay={x}:{y}[v];"
          f"[v]pad={FW}:{FH}:0:0:color=0x15171c[p];[p][1:v]overlay=0:{H},setsar=1[o]")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{a:.2f}", "-to", f"{b:.2f}", "-i", raw, "-i", str(cap),
                    "-filter_complex", vf, "-map", "[o]", *enc, str(part)], check=True)
    parts.append(part)
lst = out / "parts.txt"
lst.write_text("".join(f"file '{p.name}'\n" for p in parts))
subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy",
                "-movflags", "+faststart", final], check=True)
for p in parts + list(out.glob("cap-*.png")) + [lst]:
    p.unlink()
d = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", final], text=True).strip()
print(f"{final}: title card + {len(rows)} scenes, {float(d):.1f} s, {FW}x{FH}, {pathlib.Path(final).stat().st_size // 1024} KB")
