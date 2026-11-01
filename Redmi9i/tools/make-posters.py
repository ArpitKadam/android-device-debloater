"""Prepare wanted-poster art for the phone.

    python tools/make-posters.py theme-assets

Reads   <dir>/wanted-posters/black/<Name>.png        white art on near-black, any size (we used 1964 x 2752)
        <dir>/wanted-posters/transparent/<Name>.png  white art on transparent
Writes  <dir>/posters/<Name>.jpg   720 px wide JPEG (quality 88), the lock screen and the drawer panel
        <dir>/marks/<Name>.png     360 px wide PNG, drawn tinted gold as a faint watermark

<Name> must be one of the crew names in theme/Crew.kt (Luffy, Zoro, Nami, Usopp, Vinsmoke_Sanji, Tony_Tony_Chopper,
Nico_Robin, Franky, Brook, Jinbe), or whatever list you change that file to. Needs pillow.
"""
import glob
import os
import sys

from PIL import Image


def resize(img, width):
    return img.resize((width, round(width * img.height / img.width)), Image.LANCZOS)


def main(root):
    os.makedirs(os.path.join(root, "posters"), exist_ok=True)
    os.makedirs(os.path.join(root, "marks"), exist_ok=True)
    for f in sorted(glob.glob(os.path.join(root, "wanted-posters", "black", "*.png"))):
        name = os.path.splitext(os.path.basename(f))[0]
        resize(Image.open(f).convert("RGB"), 720).save(os.path.join(root, "posters", name + ".jpg"), quality=88)
        print("poster", name)
    for f in sorted(glob.glob(os.path.join(root, "wanted-posters", "transparent", "*.png"))):
        name = os.path.splitext(os.path.basename(f))[0]
        resize(Image.open(f).convert("RGBA"), 360).save(os.path.join(root, "marks", name + ".png"), optimize=True)
        print("mark", name)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
