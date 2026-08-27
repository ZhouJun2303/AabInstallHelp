"""Generate the AAB Install Helper app icon (geometric install mark)."""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BLUE = (37, 99, 235, 255)
WHITE = (255, 255, 255, 255)
MASTER = 1024
SS = 4
CANVAS = MASTER * SS


def make_icon():
    img = Image.new("RGBA", (CANVAS, CANVAS), BLUE)
    d = ImageDraw.Draw(img)
    cx, s = CANVAS / 2, SS

    shaft_w = 132 * s
    shaft_top = 190 * s
    shaft_bot = 500 * s
    d.rounded_rectangle(
        (cx - shaft_w / 2, shaft_top, cx + shaft_w / 2, shaft_bot),
        radius=36 * s,
        fill=WHITE,
    )

    head_top = 450 * s
    head_bot = 710 * s
    head_w = 430 * s
    d.polygon(
        [
            (cx, head_bot),
            (cx - head_w / 2, head_top),
            (cx + head_w / 2, head_top),
        ],
        fill=WHITE,
    )

    tray_t = 70 * s
    tray_w = 560 * s
    tray_h = 150 * s
    tray_y = 770 * s
    rad = 35 * s
    d.rounded_rectangle(
        (cx - tray_w / 2, tray_y, cx - tray_w / 2 + tray_t, tray_y + tray_h),
        radius=rad,
        fill=WHITE,
    )
    d.rounded_rectangle(
        (cx + tray_w / 2 - tray_t, tray_y, cx + tray_w / 2, tray_y + tray_h),
        radius=rad,
        fill=WHITE,
    )
    d.rounded_rectangle(
        (cx - tray_w / 2, tray_y + tray_h - tray_t, cx + tray_w / 2, tray_y + tray_h),
        radius=rad,
        fill=WHITE,
    )
    return img.resize((MASTER, MASTER), Image.LANCZOS)


def export(master):
    res = ROOT / "resources"
    res.mkdir(exist_ok=True)
    png512 = master.resize((512, 512), Image.LANCZOS)
    png512.save(res / "icon.png", "PNG", optimize=True)

    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    master.save(res / "icon.ico", format="ICO", sizes=ico_sizes)

    android_res = ROOT / "android" / "app" / "src" / "main" / "res"
    png512.save(android_res / "drawable" / "ic_launcher.png", "PNG", optimize=True)
    master.resize((72, 72), Image.LANCZOS).save(
        android_res / "drawable-hdpi" / "ic_launcher.png", "PNG", optimize=True
    )
    mipmaps = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in mipmaps.items():
        dest = android_res / folder
        dest.mkdir(exist_ok=True)
        master.resize((size, size), Image.LANCZOS).save(dest / "ic_launcher.png", "PNG", optimize=True)

    preview = ROOT / "install_temp"
    preview.mkdir(exist_ok=True)
    master.save(preview / "icon-final.png")
    master.resize((16, 16), Image.LANCZOS).resize((128, 128), Image.NEAREST).save(
        preview / "icon-final-16.png"
    )
    print("exported", (res / "icon.png").stat().st_size, (res / "icon.ico").stat().st_size)


if __name__ == "__main__":
    export(make_icon())
