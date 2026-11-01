"""Turn icon art into transparent 256 px launcher icons.

    python tools/make-icons.py SRC_DIR OUT_DIR

Every PNG in SRC_DIR becomes OUT_DIR/<same name>.png. File names matter: the launcher maps
browser, camera, files, security, settings, telegram and termux (see MedallionIconProvider).

Why it exists: image generators often "draw" the transparency checkerboard into the picture as grey pixels, so
the PNG has no alpha channel at all. This removes that background: grey, low-colour pixels connected to the image
border are treated as background; the largest remaining shape is kept (dropping stray marks such as a corner
watermark); grey patches enclosed by the artwork are repainted deep sea blue; the edge is softened; the result
is cropped square and resized. Art that already has real transparency does not need this script; just resize it.

Needs: pillow, numpy, scipy.
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

FILL = (11, 42, 74)  # deep sea blue for grey left inside the art
SIZE = 256


def cut_out(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(int)
    mx, mn = a.max(2), a.min(2)
    grey = ((mx - mn) <= 9) & (mx >= 35) & (mx <= 125)  # neutral, dark to mid: the baked-in checkerboard

    lab, _ = ndimage.label(grey)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))

    lab2, n = ndimage.label(~bg)
    sizes = ndimage.sum(~bg, lab2, range(1, n + 1))
    keep = ndimage.binary_fill_holes(lab2 == 1 + int(np.argmax(sizes)))

    inner = grey & keep & ~bg
    il, k = ndimage.label(inner)
    if k:
        isz = ndimage.sum(inner, il, range(1, k + 1))
        big = np.isin(il, [i + 1 for i, z in enumerate(isz) if z > 400])
        big = ndimage.binary_dilation(big, iterations=2) & (grey | big)
        a[big] = FILL

    keep = ndimage.binary_opening(keep, iterations=1)
    alpha = np.clip((ndimage.gaussian_filter(keep.astype(float), 1.2) - 0.5) * 2.2 + 0.5, 0, 1)
    img = Image.fromarray(np.dstack([a.astype(np.uint8), (alpha * 255).astype(np.uint8)]), "RGBA")

    ys, xs = np.where(keep)
    img = img.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    side = max(img.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return square.resize((SIZE, SIZE), Image.LANCZOS)


def main(src, out):
    os.makedirs(out, exist_ok=True)
    for name in sorted(os.listdir(src)):
        if name.lower().endswith(".png"):
            cut_out(os.path.join(src, name)).save(os.path.join(out, name), optimize=True)
            print("ok", name)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
