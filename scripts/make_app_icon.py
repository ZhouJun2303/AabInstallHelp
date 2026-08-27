"""Generate the AAB Install Helper app icon (geometric install mark)."""
import io
import struct
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


def _and_row_size(width):
    return ((width + 31) // 32) * 4


def image_to_dib(img):
    img = img.convert("RGBA")
    width, height = img.size
    pixels = img.load()
    xor = bytearray()
    for y in range(height - 1, -1, -1):
        for x in range(width):
            red, green, blue, alpha = pixels[x, y]
            xor += struct.pack("BBBB", blue, green, red, alpha)
    and_row = _and_row_size(width)
    mask = bytearray()
    for y in range(height - 1, -1, -1):
        row = bytearray(and_row)
        for x in range(width):
            if pixels[x, y][3] < 128:
                row[x // 8] |= 1 << (7 - (x % 8))
        mask += row
    header = struct.pack(
        "<IiiHHIIiiII",
        40,
        width,
        height * 2,
        1,
        32,
        0,
        len(xor) + len(mask),
        0,
        0,
        0,
        0,
    )
    return header + xor + mask


def write_ico(path, images):
    blobs = []
    for img in images:
        width, height = img.size
        if width >= 256 and height >= 256:
            buf = io.BytesIO()
            img.convert("RGBA").save(buf, format="PNG")
            blobs.append(buf.getvalue())
        else:
            blobs.append(image_to_dib(img))
    offset = 6 + 16 * len(images)
    out = bytearray(struct.pack("<HHH", 0, 1, len(images)))
    for img, blob in zip(images, blobs):
        width, height = img.size
        out += struct.pack(
            "<BBBBHHII",
            0 if width >= 256 else width,
            0 if height >= 256 else height,
            0,
            0,
            1,
            32,
            len(blob),
            offset,
        )
        offset += len(blob)
    for blob in blobs:
        out += blob
    path.write_bytes(out)


def export(master):
    res = ROOT / "resources"
    res.mkdir(exist_ok=True)
    png512 = master.resize((512, 512), Image.LANCZOS)
    png512.save(res / "icon.png", "PNG", optimize=True)

    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    write_ico(res / "icon.ico", [master.resize((n, n), Image.LANCZOS) for n in ico_sizes])

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

    print("exported", (res / "icon.png").stat().st_size, (res / "icon.ico").stat().st_size)


if __name__ == "__main__":
    export(make_icon())
