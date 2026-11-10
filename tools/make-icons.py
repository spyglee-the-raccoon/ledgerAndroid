"""Generates the Android launcher icons from the iOS app icon.

Usage (needs Pillow):  python tools/make-icons.py "<path to iOS repo>/ledger/Assets.xcassets/AppIcon.appiconset/Icon.png"

The iOS icon is a full-bleed 1024 px square: a mark on a dark background (since iOS 8a76b5d three
coloured bars forming an L on black). Android adaptive icons are 108 dp squares that the launcher
masks into its own shape; the middle 72 dp are visible and only the middle 66 dp circle always is.
So the iOS icon fills the visible 72 dp, or less if its mark would then reach outside the safe circle,
its background is extended into the bleed area, and a white silhouette of the mark is made for themed
(monochrome) icons on Android 13+. The Play listing icon is the iOS icon itself.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

CANVAS = 1080  # 108 dp at 10 px per dp; scaled down per density
VISIBLE = CANVAS * 72 // 108
INSET = (CANVAS - VISIBLE) // 2
SAFE_RADIUS = CANVAS * 33 / 108


def mark_alpha(image):
    """Opacity of the mark: anything clearly lighter than the icon's dark background."""
    return image.convert("L").point(lambda v: max(0, min(255, (v - 16) * 255 // 32)))


def mark_reach(image):
    """How far the mark reaches from the centre, as a share of the image's width."""
    pixels = mark_alpha(image).load()
    size = image.width
    return max(
        math.hypot(x - size / 2, y - size / 2)
        for y in range(size)
        for x in range(size)
        if pixels[x, y] > 128
    ) / size


def art_size(icon):
    """The visible 72 dp, or less so the mark stays inside the safe circle with a small margin."""
    return min(VISIBLE, int(SAFE_RADIUS * 0.97 / mark_reach(icon)))


def full_bleed(icon, size):
    """The iOS icon [size] px wide in the middle, its background continued to the 108 dp edges."""
    scaled = icon.resize((size, size), Image.LANCZOS)
    inset = (CANVAS - size) // 2
    canvas = Image.new("RGBA", (CANVAS, CANVAS))
    # A background gradient would be vertical, so each row takes the colour of the icon's left edge at that height.
    edge = [scaled.getpixel((0, min(size - 1, max(0, y - inset)))) for y in range(CANVAS)]
    for y, colour in enumerate(edge):
        canvas.paste(colour, (0, y, CANVAS, y + 1))
    canvas.paste(scaled, (inset, inset))
    return canvas


def check_safe_zone(full):
    """Fails if any part of the mark could be cut off by a launcher's mask."""
    alpha = mark_alpha(full)
    pixels = alpha.load()
    left, top, right, bottom = alpha.getbbox()
    furthest = max(
        math.hypot(x - CANVAS / 2, y - CANVAS / 2)
        for y in range(top, bottom)
        for x in range(left, right)
        if pixels[x, y] > 128
    )
    if furthest > SAFE_RADIUS:
        sys.exit(f"The mark reaches {furthest / 10:.1f} dp from the centre, outside the 33 dp safe zone")


def monochrome(full):
    """White silhouette of the mark on transparent, for themed icons."""
    silhouette = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    silhouette.putalpha(mark_alpha(full))
    return silhouette


def legacy(art, round_mask):
    """48 dp icon for Android 7 and older launchers: the art in a rounded square or circle."""
    size = art.width
    mask = Image.new("L", (size * 4, size * 4), 0)
    draw = ImageDraw.Draw(mask)
    if round_mask:
        draw.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 * 0.22, fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(art, (0, 0), mask)
    return out


def save(image, folder, name, size):
    path = RES / folder / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.LANCZOS).save(path, optimize=True)


def main():
    icon = Image.open(sys.argv[1]).convert("RGBA")
    full = full_bleed(icon, art_size(icon))
    check_safe_zone(full)
    mono = monochrome(full)
    # Legacy icons show the 72 dp middle of the 108 dp canvas, like a masked adaptive icon.
    legacy_art = full.crop((INSET, INSET, CANVAS - INSET, CANVAS - INSET))

    for density, factor in DENSITIES.items():
        folder = f"mipmap-{density}"
        save(full, folder, "ic_launcher_foreground", round(108 * factor))
        save(mono, folder, "ic_launcher_monochrome", round(108 * factor))
        size = round(48 * factor)
        save(legacy(legacy_art.resize((size, size), Image.LANCZOS), round_mask=False), folder, "ic_launcher", size)
        save(legacy(legacy_art.resize((size, size), Image.LANCZOS), round_mask=True), folder, "ic_launcher_round", size)

    # Google Play listing icon: 512 px, full square (Play applies its own mask), like the App Store's.
    icon.resize((512, 512), Image.LANCZOS).convert("RGB").save(RES.parent / "ic_launcher-playstore.png", optimize=True)
    print("Icons written to", RES)


if __name__ == "__main__":
    main()
