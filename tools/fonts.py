#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Builds HearOn Link's font from Google Sans Flex (SIL Open Font License 1.1).

Keeps Latin text and punctuation, pins axes the app doesn't use (slant, grade, width), and renames
the family to "HearOn Sans": the OFL allows modified versions, and Google's trademark notes ask
that modified fonts not carry the Google Sans name. Credits and the OFL text
ship in the app (About › Licenses) and in the repo (app/src/main/assets/licenses/).
    python3 tools/fonts.py   (needs fonttools)
"""
import pathlib
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".cache/hearonlink"
BASE = "https://raw.githubusercontent.com/google/fonts/main/ofl/"
SRC = {
    "flex": ("googlesansflex/GoogleSansFlex%5BGRAD,ROND,opsz,slnt,wdth,wght%5D.ttf", "GoogleSansFlex.ttf"),
    "code": ("googlesanscode/GoogleSansCode%5Bwght%5D.ttf", "GoogleSansCode.ttf"),
    "ofl": ("googlesansflex/OFL.txt", "OFL.txt"),
}

# Basic Latin, Latin-1, Latin Extended-A (European names), general punctuation, currency,
# letterlike (℃ etc.), number forms (½), arrows, maths operators, superscripts, degree/prime marks.
RANGES = [(0x20, 0x7E), (0xA0, 0x17F), (0x2010, 0x2044), (0x20AC, 0x20AC), (0x2122, 0x2122), (0x2190, 0x2193), (0x2212, 0x2212)]


def fetch(key):
    remote, local = SRC[key]
    path = CACHE / local
    if not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(BASE + remote, path)
    return path


def rename(font, family):
    ps = family.replace(" ", "")
    for rec in font["name"].names:
        if rec.nameID in (1, 3, 4, 6, 16, 21):
            rec.string = ps if rec.nameID == 6 else (family if rec.nameID in (1, 16, 21) else family + " Variable")
        elif rec.nameID == 25 or rec.nameID > 255:
            # PostScript prefix and named-instance names ("GoogleSansFlex-Bold"): these end up in
            # PDFs, so they get the new name too. Copyright, trademark and licence records stay.
            text = rec.toUnicode()
            for old in ("GoogleSansFlex", "GoogleSansCode"):
                text = text.replace(old, ps)
            for old in ("Google Sans Flex", "Google Sans Code"):
                text = text.replace(old, family)
            rec.string = text


def build(src, out, family, pins):
    font = TTFont(src, lazy=False)
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.glyph_names = False
    opts.notdef_outline = True
    opts.name_IDs = ["*"]
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=[c for a, b in RANGES for c in range(a, b + 1)])
    sub.subset(font)
    if pins:
        font = instancer.instantiateVariableFont(font, pins)
    rename(font, family)
    out.parent.mkdir(parents=True, exist_ok=True)
    font.save(out)
    print(out.relative_to(ROOT), out.stat().st_size, "bytes")


def main():
    res = ROOT / "app/src/main/res/font"
    build(fetch("flex"), res / "hearon_sans.ttf", "HearOn Sans", {"slnt": 0, "GRAD": 0, "wdth": 100})
    lic = ROOT / "app/src/main/assets/licenses"
    lic.mkdir(parents=True, exist_ok=True)
    (lic / "OFL-GoogleSans.txt").write_text(fetch("ofl").read_text())


if __name__ == "__main__":
    main()
