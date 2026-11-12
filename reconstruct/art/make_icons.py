#!/usr/bin/env python3
"""Generate the Kestrel launcher/notification/splash icons with Pillow.

The Kestrel mark: an angular falcon head in profile (facing right) built from
graphite planes, with a single electric-cyan eye stroke like a terminal
cursor — the mono-graphite brand of the redesigned app.

Outputs (relative to the repo root, into the staged payload):
  reconstruct/out/stage/res/drawable-nodpi-v4/ic_launcher_logo.png   432x432
  reconstruct/out/stage/res/drawable-nodpi-v4/ic_notification.png    432x432 (white glyph)
  reconstruct/out/stage/res/drawable-nodpi-v4/splash_logo.png        540x540
  reconstruct/out/stage/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}-v4/ic_launcher.png + _round.png
  reconstruct/art/kestrel-preview.png  (visual check: light/dark/masked)
"""
import os
import sys
from PIL import Image, ImageDraw

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STAGE = os.path.join(REPO, "reconstruct", "out", "stage", "res")
ART = os.path.join(REPO, "reconstruct", "art")

SS = 4  # supersampling factor

# palette — mono graphite + electric cyan
C_CROWN = (78, 86, 99, 255)      # upper plane, lighter graphite
C_FACE = (47, 53, 62, 255)       # mid graphite
C_STRIPE = (24, 27, 33, 255)     # malar stripe, near-black
C_BEAK = (18, 21, 27, 255)       # beak, darkest
C_SEAM = (16, 19, 24, 255)       # seam between face and beak
C_EYE = (63, 217, 255, 255)      # electric cyan
C_WHITE = (255, 255, 255, 255)

# design space: 0..100, falcon head in profile facing right
SILHOUETTE = [(12, 32), (54, 16), (72, 26), (90, 44), (80, 52), (64, 48),
              (56, 68), (38, 76), (20, 80), (12, 62)]
CROWN = [(12, 32), (54, 16), (72, 26), (44, 40), (16, 44)]
FACE = [(16, 44), (44, 40), (72, 26), (80, 52), (64, 48), (56, 68),
        (38, 76), (20, 80), (12, 62)]
BEAK = [(72, 26), (90, 44), (80, 52), (63, 45)]
SEAM = [(70, 29), (74, 27), (70, 46), (66, 43)]
STRIPE = [(46, 44), (56, 42), (52, 66), (41, 62)]
EYE = [(50, 33), (63, 30), (63, 35), (50, 38)]

# content boxes measured from the original payload art
LOGO_BOX = (84, 94, 348, 337)      # 432 canvas
SPLASH_BOX = (67, 84, 471, 456)    # 540 canvas
MIPMAP_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def _map(pt, x0, y0, x1, y1):
    fx = (pt[0] - 12.0) / (88.0 - 12.0)
    fy = (pt[1] - 16.0) / (80.0 - 16.0)
    return (x0 + fx * (x1 - x0), y0 + fy * (y1 - y0))


def draw_mark(draw, x0, y0, x1, y1, colorize=None, cut=False):
    """Draw the kestrel head into the box.

    colorize: force every plane to one color (notification glyph).
    cut: when True, eye/stripe shapes are drawn as gaps (caller handles mask).
    """
    def poly(points, color):
        mapped = [_map(p, x0, y0, x1, y1) for p in points]
        draw.polygon(mapped, fill=color)

    head = colorize or C_FACE
    crown = colorize or C_CROWN
    beak = colorize or C_BEAK
    seam = colorize or C_SEAM
    stripe = colorize or C_STRIPE
    eye = colorize or C_EYE

    poly(SILHOUETTE, head)
    poly(CROWN, crown)
    poly(BEAK, beak)
    poly(SEAM, seam)
    if not cut:
        poly(STRIPE, stripe)
        poly(EYE, eye)


def render(size, box, mode="brand"):
    """Render the full canvas: mode brand (colored mark) or glyph (white)."""
    big = (size * SS, size * SS)
    img = Image.new("RGBA", big, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    bx = tuple(int(v * SS) for v in box)
    if mode == "glyph":
        # white silhouette with the eye + stripe cut out (notification mask)
        mask = Image.new("L", big, 0)
        mdraw = ImageDraw.Draw(mask)
        draw_mark(mdraw, *bx, colorize=255, cut=False)
        mdraw.polygon([_map(p, *bx) for p in STRIPE], fill=0)
        mdraw.polygon([_map(p, *bx) for p in EYE], fill=0)
        img.putalpha(mask)
        solid = Image.new("RGBA", big, C_WHITE)
        solid.putalpha(mask)
        img = solid
    else:
        draw_mark(draw, *bx)
    return img.resize((size, size), Image.LANCZOS)


def write(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print("wrote", os.path.relpath(path, REPO))


def main() -> int:
    # foreground bitmap consumed by drawable/ic_launcher_foreground.xml
    # (<inset 30%><bitmap src=@0x7f020001>) — geometry must match the original
    write(os.path.join(STAGE, "drawable-nodpi-v4", "ic_launcher_logo.png"),
          render(432, LOGO_BOX))
    # notification glyph (white on transparent)
    write(os.path.join(STAGE, "drawable-nodpi-v4", "ic_notification.png"),
          render(432, LOGO_BOX, mode="glyph"))
    # native splash logo
    write(os.path.join(STAGE, "drawable-nodpi-v4", "splash_logo.png"),
          render(540, SPLASH_BOX))
    # legacy launcher rasters (pre-26 launchers) — transparent background,
    # mark scaled with the 432 logo's content fractions
    fx0, fy0, fx1, fy1 = (LOGO_BOX[0] / 432.0, LOGO_BOX[1] / 432.0,
                          LOGO_BOX[2] / 432.0, LOGO_BOX[3] / 432.0)
    for density, size in MIPMAP_SIZES.items():
        box = (round(size * fx0), round(size * fy0), round(size * fx1), round(size * fy1))
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            write(os.path.join(STAGE, f"mipmap-{density}-v4", name), render(size, box))

    # preview sheet: on light, on dark, circle-masked (how launchers will crop)
    prev = Image.new("RGBA", (512, 170), (240, 242, 245, 255))
    icon = render(128, tuple(int(v * 128 / 432) for v in LOGO_BOX))
    dark_tile = Image.new("RGBA", (128, 128), (16, 18, 22, 255))
    dark_tile.alpha_composite(icon)
    prev.paste(icon, (10, 20), icon)
    prev.paste(dark_tile, (160, 20))
    masked = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    m = Image.new("L", (128, 128), 0)
    ImageDraw.Draw(m).ellipse((0, 0, 127, 127), fill=255)
    circle = Image.new("RGBA", (128, 128), (20, 23, 28, 255))
    circle.alpha_composite(icon)
    masked.paste(circle, (0, 0), m)
    prev.paste(masked, (310, 20), masked)
    prev.save(os.path.join(ART, "kestrel-preview.png"))
    print("wrote reconstruct/art/kestrel-preview.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
