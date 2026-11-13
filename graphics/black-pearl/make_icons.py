"""Black Pearl: builds every launcher/store icon from graphics/black-pearl/icon-source.png (owner artwork).

Run from the repository root:  python3 graphics/black-pearl/make_icons.py   (needs Pillow + numpy)
Outputs: mipmap-*/ic_launcher_art.png (adaptive foreground), mipmap-*/ic_launcher_mono.png (themed icon),
mipmap-*/ic_launcher.png / ic_launcher_round.png (Android 7 and older), icon-512.png, fastlane icon.png.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SRC = 'graphics/black-pearl/icon-source.png'
RES = 'app/src/main/res'
# the dark rounded square inside the artwork (outside it is black)
L, T, R, B = 50, 51, 1197, 1198
# the pearl, in source pixels
PEARL_X, PEARL_Y, PEARL_R = 632, 627, 440
# keep in sync with drawable-v24/ic_launcher_background.xml and the insets of the foreground/monochrome drawables
BG_TOP, BG_BOTTOM = (16, 25, 41), (5, 9, 18)
INSET_DP = 22
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

src = Image.open(SRC).convert('RGB')
art = src.crop((L, T, R, B))
n = art.size[0]


def rounded_mask(size, radius_ratio=0.205, ss=4):
    m = Image.new('L', (size * ss, size * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size * ss - 1, size * ss - 1), radius=int(size * radius_ratio) * ss, fill=255)
    return m.resize((size, size), Image.LANCZOS)


square = rounded_mask(n)
# foreground: the artwork with its square edge faded, so it melts into the background layer under any mask shape
core = square.filter(ImageFilter.MinFilter(int(n * 0.06) // 2 * 2 + 1))
soft = core.filter(ImageFilter.GaussianBlur(n * 0.045))
fg = art.convert('RGBA')
fg.putalpha(Image.fromarray(np.minimum(np.asarray(square), np.asarray(soft)).astype(np.uint8)))

# monochrome: the pearl as a solid disc with the ship cut out, plus the waves and the notebook
a = np.asarray(art).astype(float)
lum = a @ [0.299, 0.587, 0.114]
yy, xx = np.mgrid[0:n, 0:n]
dist = np.hypot(xx - (PEARL_X - L), yy - (PEARL_Y - T))
disc = np.clip((PEARL_R - dist) / 3, 0, 1)
ship = np.clip((110 - lum) / 40, 0, 1)
bright = np.clip((lum - 150) / 40, 0, 1)
mono_alpha = np.maximum(disc * (1 - ship), bright * (dist > PEARL_R - 10)) * (np.asarray(square) / 255)
mono = Image.new('RGBA', (n, n), (255, 255, 255, 255))
mono.putalpha(Image.fromarray((mono_alpha * 255).astype(np.uint8)))


def adaptive(px):
    """The full 108 dp adaptive icon (background + foreground), px wide."""
    g = np.linspace(0, 1, px)[:, None, None]
    bg = ((1 - g) * np.array(BG_TOP) + g * np.array(BG_BOTTOM)).repeat(px, 1).astype(np.uint8)
    out = Image.fromarray(bg).convert('RGBA')
    size = round(px * (108 - 2 * INSET_DP) / 108)
    out.alpha_composite(fg.resize((size, size), Image.LANCZOS), (round(px * INSET_DP / 108),) * 2)
    return out


def legacy_square(px):
    """The artwork as it is: its own rounded square, transparent corners."""
    out = art.convert('RGBA').resize((px, px), Image.LANCZOS)
    out.putalpha(rounded_mask(px))
    return out


def legacy_round(px):
    """The visible 72 dp circle of the adaptive icon."""
    full = adaptive(round(px * 108 / 72))
    o = (full.size[0] - px) // 2
    out = full.crop((o, o, o + px, o + px))
    m = Image.new('L', (px * 4, px * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
    out.putalpha(m.resize((px, px), Image.LANCZOS))
    return out


for name, d in DENSITIES.items():
    layer = round((108 - 2 * INSET_DP) * d * 1.25)  # a bit sharper than 1:1
    fg.resize((layer, layer), Image.LANCZOS).save(f'{RES}/mipmap-{name}/ic_launcher_art.png', optimize=True)
    mono.resize((layer, layer), Image.LANCZOS).save(f'{RES}/mipmap-{name}/ic_launcher_mono.png', optimize=True)
    legacy_square(round(48 * d)).save(f'{RES}/mipmap-{name}/ic_launcher.png', optimize=True)
    legacy_round(round(48 * d)).save(f'{RES}/mipmap-{name}/ic_launcher_round.png', optimize=True)

legacy_square(512).save('graphics/black-pearl/icon-512.png', optimize=True)
legacy_square(512).save('fastlane/metadata/android/en-US/images/icon.png', optimize=True)
print('icons written')
