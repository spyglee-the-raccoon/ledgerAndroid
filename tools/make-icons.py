"""Generates the Android launcher icons from the iOS app icon.

Usage (needs Pillow):  python tools/make-icons.py "<path to iOS repo>/budget tracker/Assets.xcassets/AppIcon.appiconset/Ladger Logo.png"

The iOS icon is a 1024 px rounded square with the artwork on a dark background. Android adaptive
icons are 108 dp squares that the launcher masks into its own shape; only the middle 66 dp circle
is always visible. So the artwork is scaled to fit that circle and blended into a full-bleed
background, and a white silhouette is made for themed (monochrome) icons on Android 13+.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
BACKGROUND = (11, 13, 17, 255)  # the iOS icon's background, sampled

CANVAS = 1080  # 108 dp at 10 px per dp; scaled down per density
SAFE_DIAMETER = CANVAS * 66 / 108


def content_box(icon):
    """Bounding box of the artwork: pixels clearly brighter than the dark background."""
    lum = icon.convert("L").point(lambda v: 255 if v > 70 else 0)
    alpha = icon.getchannel("A").point(lambda v: 255 if v > 200 else 0)
    return ImageChops.multiply(lum, alpha).getbbox()


def full_bleed(icon):
    """The artwork centered on a 108 dp canvas, fitting the safe circle, faded into the background."""
    left, top, right, bottom = content_box(icon)
    diagonal = math.hypot(right - left, bottom - top)
    scale = SAFE_DIAMETER * 0.96 / diagonal
    scaled = icon.resize((round(icon.width * scale), round(icon.height * scale)), Image.LANCZOS)
    cx, cy = (left + right) / 2 * scale, (top + bottom) / 2 * scale

    # Fade the scaled icon out before its rounded corners, so no edge shows on the background.
    inner, outer = diagonal * scale / 2 * 1.02, min(scaled.width, scaled.height) / 2 * 0.98
    mask = Image.new("L", scaled.size, 0)
    for r in range(int(outer), int(inner) - 1, -1):
        level = 255 if r <= inner else round(255 * (outer - r) / (outer - inner))
        ImageDraw.Draw(mask).ellipse((cx - r, cy - r, cx + r, cy + r), fill=level)
    mask = ImageChops.multiply(mask, scaled.getchannel("A"))

    canvas = Image.new("RGBA", (CANVAS, CANVAS), BACKGROUND)
    offset = (round(CANVAS / 2 - cx), round(CANVAS / 2 - cy))
    canvas.paste(scaled, offset, mask)
    return canvas, scale, offset, mask


def monochrome(icon, scale, offset, fade):
    """White silhouette of the artwork on transparent, for themed icons."""
    scaled = icon.resize((round(icon.width * scale), round(icon.height * scale)), Image.LANCZOS)
    # Distance from the background colour rather than brightness, so the dark green of the mark counts too.
    background = Image.new("RGB", scaled.size, BACKGROUND[:3])
    distance = ImageChops.difference(scaled.convert("RGB"), background).split()
    strongest = ImageChops.lighter(ImageChops.lighter(distance[0], distance[1]), distance[2])
    shape = strongest.point(lambda v: max(0, min(255, (v - 22) * 255 // 14)))
    alpha = Image.new("L", (CANVAS, CANVAS), 0)
    # The same fade as the colour layer, so the iOS icon's rounded border never shows.
    alpha.paste(ImageChops.multiply(shape, fade), offset)
    silhouette = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    silhouette.putalpha(alpha)
    return silhouette


def legacy(full, round_mask):
    """48 dp icon for Android 7 and older launchers: the full-bleed art in a rounded square or circle."""
    size = full.width
    mask = Image.new("L", (size * 4, size * 4), 0)
    draw = ImageDraw.Draw(mask)
    if round_mask:
        draw.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 * 0.22, fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(full, (0, 0), mask)
    return out


def save(image, folder, name, size):
    path = RES / folder / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.LANCZOS).save(path, optimize=True)


def main():
    icon = Image.open(sys.argv[1]).convert("RGBA")
    full, scale, offset, fade = full_bleed(icon)
    mono = monochrome(icon, scale, offset, fade)
    # Legacy icons show the 72 dp middle of the 108 dp canvas, like a masked adaptive icon.
    crop = (CANVAS - CANVAS * 72 // 108) // 2
    legacy_art = full.crop((crop, crop, CANVAS - crop, CANVAS - crop))

    for density, factor in DENSITIES.items():
        folder = f"mipmap-{density}"
        save(full, folder, "ic_launcher_foreground", round(108 * factor))
        save(mono, folder, "ic_launcher_monochrome", round(108 * factor))
        size = round(48 * factor)
        save(legacy(legacy_art.resize((size, size), Image.LANCZOS), round_mask=False), folder, "ic_launcher", size)
        save(legacy(legacy_art.resize((size, size), Image.LANCZOS), round_mask=True), folder, "ic_launcher_round", size)

    # Google Play listing icon: 512 px, full square (Play applies its own mask).
    legacy_art.resize((512, 512), Image.LANCZOS).convert("RGB").save(RES.parent / "ic_launcher-playstore.png", optimize=True)
    print("Icons written to", RES)


if __name__ == "__main__":
    main()
