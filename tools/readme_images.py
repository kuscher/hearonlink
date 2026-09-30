#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Frames HearOn Link's own renders (./hol render …, the real UI drawn on a private virtual display
with sample AirPods) for the README: rounded corners and a soft shadow on a light teal backdrop.
    python3 tools/readme_images.py RAW.png OUT.png [--width 1760] [--radius 22] [--crop x0,y0,x1,y1]
"""
import sys
from PIL import Image, ImageDraw, ImageFilter


def frame(raw, out, width=1760, radius=22, crop=None):
    im = Image.open(raw).convert("RGB")
    if crop:
        im = im.crop(crop)
    w, h = im.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=255)
    pad = max(40, w // 22)
    W, H = w + 2 * pad, h + 2 * pad
    bg = Image.new("RGB", (W, H))
    top, bot = (206, 238, 234), (226, 236, 250)
    d = ImageDraw.Draw(bg)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 14, pad + w, pad + h + 14), radius, fill=110)
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    bg.paste((20, 60, 60), (0, 0), shadow)
    bg.paste(im, (pad, pad), mask)
    if bg.width > width:
        bg = bg.resize((width, int(bg.height * width / bg.width)), Image.LANCZOS)
    bg.save(out, optimize=True)


if __name__ == "__main__":
    a = sys.argv
    kw = {}
    if "--width" in a: kw["width"] = int(a[a.index("--width") + 1])
    if "--radius" in a: kw["radius"] = int(a[a.index("--radius") + 1])
    if "--crop" in a: kw["crop"] = tuple(int(v) for v in a[a.index("--crop") + 1].split(","))
    frame(a[1], a[2], **kw)
