#!/usr/bin/env python3
"""Builds the theme's four New Year pictures, wallpapers/ny-*.jpg, from photos on Wikimedia Commons.

The launcher shows files whose names start with "ny-" only in the Christmas skin's New Year days (1 January until
the skin ends) and leaves them out on every other day. Each original is fetched, checked, converted to sRGB if it carries
another colour profile, cropped, resized, shaded and saved exactly as prepare_wallpapers.py does for the Christmas photos; the credits are in CREDITS.md and in
wallpapers/captions.txt.

Usage: python3 prepare_newyear_wallpapers.py ORIGINALS   (needs Pillow; ORIGINALS is a folder for the downloads)
"""
import io
import os
import sys

from PIL import Image, ImageCms

from prepare_wallpapers import H, OUT, W, fetch, icc_profile, shade

# (output name, Commons path, SHA-256 of the original, crop box (left, top, right, bottom) on the original)
SOURCES = [
    ("ny-01-melbourne.jpg", "6/6c/Melbourne_New_Year_2014_%2811726618003%29.jpg",
     "39906752a37aeac24ed93f549a7788c1ef89a0da5110fe59937cd097a6bd92d7", (189, 0, 3892, 2083)),
    ("ny-02-marina-bay.jpg", "c/cc/Marina-Bay_Singapore_Firework-launching-CNY-2015-03.jpg",
     "316a37a59878df5a0a62c09faded886274c275a1c8c054bf6ded98ee4ad263fd", (0, 26, 5174, 2936)),
    ("ny-03-dubai.jpg", "1/14/Burj_Al_Arab_Fireworks_2021_New_Year.jpg",
     "24ea949af2d03e623f87474d133115e4ddfe8ee95d9a364eb68cc20d89269bca", (0, 304, 5836, 3587)),
    ("ny-04-como.jpg", "3/33/Capodanno_2020.jpg",
     "73732cf86d003dd4c9c52c76711617247170f6549e1bd37b925bdabe1fd31af0", (0, 325, 6240, 3835)),
]


def main(folder):
    os.makedirs(folder, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for out, path, sha, box in SOURCES:
        x0, y0, x1, y1 = box
        if abs((x1 - x0) * 9 - (y1 - y0) * 16) > 16:
            sys.exit(f"{out}: crop box is not 16:9")
        name = fetch(folder, path, sha)
        src = Image.open(name)
        src.load()
        icc = icc_profile(name)
        if icc:  # as prepare_wallpapers.py: the TV would read another profile's values as sRGB
            src = ImageCms.profileToProfile(src, ImageCms.ImageCmsProfile(io.BytesIO(icc)),
                                            ImageCms.createProfile("sRGB"), outputMode="RGB")
        img = src.convert("RGB").crop(box).resize((W, H), Image.LANCZOS)
        # a new image with only the pixels: no EXIF, ICC or XMP from the original is carried over
        clean = Image.new("RGB", (W, H))
        clean.paste(shade(img))
        for quality in range(86, 49, -2):  # the best quality that stays under 300 KB, as the theme guide asks
            dest = os.path.join(OUT, out)
            clean.save(dest, quality=quality, optimize=True, progressive=True)
            if os.path.getsize(dest) <= 300 * 1024:
                break
        else:
            sys.exit(f"{out}: still over 300 KB at quality 50")
        print("wrote", os.path.join("wallpapers", out), os.path.getsize(dest))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip().splitlines()[-1])
    main(sys.argv[1])
