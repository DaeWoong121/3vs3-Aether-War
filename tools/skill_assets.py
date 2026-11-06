"""
Skill tree art for the Aether War resource pack: 32px skill icons, class icons and
chest-GUI backgrounds drawn through a font glyph (the title trick).
Imported by build_resourcepack.py.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

CLASSES = ["warrior", "miner", "alchemist", "builder"]
# branch colors, same as PlayerClass.java
BRANCH_COLORS = {
    "warrior": ["ffaa3c", "c9ced6", "8bd45a"],
    "miner": ["ffaa3c", "5adc8c", "5abeff"],
    "alchemist": ["8ff0ff", "ff8fb3", "9c6bff"],
    "builder": ["b0b6c0", "ff9d4d", "6be07a"],
}
CLASS_COLORS = {"warrior": "ff6b5b", "miner": "ffc94d", "alchemist": "c08cff", "builder": "5ad1c8"}
# one symbol per branch
SYMBOLS = {
    "warrior": ["chevrons", "shield", "crosshair"],
    "miner": ["pickaxe", "bag", "eye"],
    "alchemist": ["gem", "heart", "swirl"],
    "builder": ["bricks", "gear", "flag"],
}
CLASS_SYMBOL = {"warrior": "sword", "miner": "pickaxe", "alchemist": "flask", "builder": "bricks"}


def rgb(h, a=255):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mul(c, f, a=None):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3] if a is None else a)


S = 32  # icon size
K = 4   # supersampling for clean shapes, then downscaled with NEAREST-ish look


def badge(color, frame="normal"):
    """Rounded gem badge: dark inner well with a colored rim. frame: normal | ult | cross | locked | aug."""
    big = Image.new("RGBA", (S * K, S * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    rim = {"ult": rgb("ffd34d"), "cross": rgb("ff78ff"), "locked": rgb("55505f"), "aug": rgb("ff8fe0")}.get(frame, color)
    if frame == "ult":
        # pointed star-ish frame for ultimates
        cx = cy = S * K / 2
        pts = []
        for i in range(16):
            r = (S * K / 2 - 2) if i % 2 == 0 else (S * K / 2 - 14)
            a = math.pi * 2 * i / 16 - math.pi / 2
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        d.polygon(pts, fill=mul(rim, 0.55))
        d.ellipse([18, 18, S * K - 18, S * K - 18], fill=mul(rim, 1.0))
        d.ellipse([26, 26, S * K - 26, S * K - 26], fill=(26, 18, 40, 255))
    elif frame in ("cross", "aug"):
        cx = cy = S * K / 2
        r = S * K / 2 - 4
        d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=mul(rim, 0.6))
        r2 = r - 9
        d.polygon([(cx, cy - r2), (cx + r2, cy), (cx, cy + r2), (cx - r2, cy)], fill=rim)
        r3 = r2 - 8
        d.polygon([(cx, cy - r3), (cx + r3, cy), (cx, cy + r3), (cx - r3, cy)], fill=(26, 18, 40, 255))
    else:
        d.rounded_rectangle([6, 6, S * K - 6, S * K - 6], radius=30, fill=mul(rim, 0.5))
        d.rounded_rectangle([12, 12, S * K - 12, S * K - 12], radius=26, fill=rim)
        d.rounded_rectangle([20, 20, S * K - 20, S * K - 20], radius=20, fill=(26, 18, 40, 255))
        # top sheen
        d.rounded_rectangle([24, 22, S * K - 24, 44], radius=10, fill=mul(rim, 0.35, 120))
    return big


def symbol(d, name, c, scale=1.0):
    """Draws a symbol in the middle of a S*K canvas."""
    z = S * K
    m = z / 2
    u = z / 32 * scale  # one icon pixel in canvas units
    hi = mul(c, 1.25)
    lo = mul(c, 0.7)
    def P(x, y):  # icon-space (-16..16) to canvas
        return (m + x * u, m + y * u)
    if name == "chevrons":
        for dx in (-4, 2):
            d.line([P(dx - 3, -7), P(dx + 4, 0), P(dx - 3, 7)], fill=c, width=int(3 * u), joint="curve")
    elif name == "shield":
        d.polygon([P(-7, -8), P(7, -8), P(7, 0), P(0, 9), P(-7, 0)], fill=c)
        d.polygon([P(0, -8), P(7, -8), P(7, 0), P(0, 9)], fill=lo)
        d.line([P(0, -5), P(0, 5)], fill=hi, width=int(1.5 * u))
    elif name == "crosshair":
        d.ellipse([P(-7, -7), P(7, 7)], outline=c, width=int(2 * u))
        for a, b in (((0, -10), (0, -4)), ((0, 4), (0, 10)), ((-10, 0), (-4, 0)), ((4, 0), (10, 0))):
            d.line([P(*a), P(*b)], fill=c, width=int(2 * u))
        d.ellipse([P(-1.5, -1.5), P(1.5, 1.5)], fill=hi)
    elif name == "pickaxe":
        d.line([P(-8, 9), P(4, -3)], fill=rgb("a86b2d"), width=int(2.5 * u))
        # crescent head across the top of the handle
        d.polygon([P(-6, -6), P(-1, -9), P(4, -9), P(9, -6), P(10, 0), P(6, -4), P(2, -6), P(-2, -6.5)], fill=c)
        d.polygon([P(-1, -9), P(4, -9), P(9, -6), P(4, -7.5)], fill=hi)
    elif name == "bag":
        d.rounded_rectangle([P(-7, -3), P(7, 9)], radius=3 * u, fill=c)
        d.rectangle([P(-3, -7), P(3, -3)], fill=lo)
        d.line([P(-7, 2), P(7, 2)], fill=lo, width=int(1.5 * u))
        d.polygon([P(0, 0), P(2.5, 3), P(0, 6), P(-2.5, 3)], fill=rgb("b79bff"))
    elif name == "eye":
        d.chord([P(-10, -7), P(10, 9)], 180, 360, fill=c)
        d.chord([P(-10, -9), P(10, 7)], 0, 180, fill=c)
        d.ellipse([P(-4, -4), P(4, 4)], fill=(26, 18, 40, 255))
        d.ellipse([P(-2, -2), P(2, 2)], fill=hi)
    elif name == "gem":
        d.polygon([P(-8, -3), P(-4, -8), P(4, -8), P(8, -3), P(0, 9)], fill=c)
        d.polygon([P(-8, -3), P(8, -3), P(0, 9)], fill=lo)
        d.polygon([P(-4, -8), P(0, -3), P(4, -8)], fill=hi)
    elif name == "heart":
        d.ellipse([P(-8, -7), P(0, 1)], fill=c)
        d.ellipse([P(0, -7), P(8, 1)], fill=c)
        d.polygon([P(-7.6, -1.5), P(7.6, -1.5), P(0, 8)], fill=c)
        d.ellipse([P(-6, -5), P(-3, -2)], fill=hi)
    elif name == "swirl":
        pts = [P(math.cos(t) * t * 1.05, math.sin(t) * t * 1.05) for t in [i / 8 for i in range(4, 72)]]
        d.line(pts, fill=c, width=int(2.4 * u), joint="curve")
    elif name == "bricks":
        for row in range(3):
            y0 = -7 + row * 5
            off = 0 if row % 2 == 0 else -3
            for col in range(-1, 3):
                x0 = -9 + col * 6 + off
                x1 = x0 + 5
                x0, x1 = max(x0, -8), min(x1, 8)
                if x1 - x0 > 1:
                    d.rectangle([P(x0, y0), P(x1, y0 + 4)], fill=c if (row + col) % 2 else lo)
    elif name == "gear":
        for i in range(8):
            a = math.pi * 2 * i / 8
            d.line([P(math.cos(a) * 4, math.sin(a) * 4), P(math.cos(a) * 9, math.sin(a) * 9)], fill=c, width=int(3.2 * u))
        d.ellipse([P(-6.5, -6.5), P(6.5, 6.5)], fill=c)
        d.ellipse([P(-2.5, -2.5), P(2.5, 2.5)], fill=(26, 18, 40, 255))
    elif name == "flag":
        d.line([P(-6, -9), P(-6, 9)], fill=rgb("e8e8f0"), width=int(2 * u))
        d.polygon([P(-5, -9), P(8, -5), P(-5, -1)], fill=c)
        d.ellipse([P(-9, 7), P(-3, 10)], fill=lo)
    elif name == "sword":
        d.line([P(-7, 7), P(7, -7)], fill=c, width=int(3 * u))
        d.line([P(-6, 2), P(-2, 6)], fill=rgb("ffd34d"), width=int(2.5 * u))
        d.line([P(-9, 9), P(-6, 6)], fill=rgb("a86b2d"), width=int(2.5 * u))
    elif name == "flask":
        d.rectangle([P(-2, -9), P(2, -3)], fill=rgb("d8d0f0"))
        d.ellipse([P(-8, -5), P(8, 10)], fill=c)
        d.ellipse([P(-5, -2), P(-1, 2)], fill=hi)
    elif name == "lock":
        d.arc([P(-5, -10), P(5, 2)], 180, 360, fill=c, width=int(2.5 * u))
        d.rounded_rectangle([P(-7, -3), P(7, 8)], radius=2 * u, fill=c)
        d.ellipse([P(-1.5, 0), P(1.5, 3)], fill=(26, 18, 40, 255))
    elif name == "spark":
        for i in range(4):
            a = math.pi / 2 * i
            d.polygon([P(0, 0), P(math.cos(a - 0.35) * 3, math.sin(a - 0.35) * 3), P(math.cos(a) * 10, math.sin(a) * 10),
                       P(math.cos(a + 0.35) * 3, math.sin(a + 0.35) * 3)], fill=c)
        d.ellipse([P(-2.5, -2.5), P(2.5, 2.5)], fill=hi)
    elif name == "book":
        d.polygon([P(-9, -6), P(0, -4), P(0, 8), P(-9, 6)], fill=rgb("f4efff"))
        d.polygon([P(9, -6), P(0, -4), P(0, 8), P(9, 6)], fill=rgb("d8ccff"))
        d.line([P(0, -4), P(0, 8)], fill=lo, width=int(1.2 * u))
        d.polygon([P(0, -12), P(2.5, -8.5), P(0, -5), P(-2.5, -8.5)], fill=c)
    elif name == "cycle":
        d.arc([P(-8, -8), P(8, 8)], 30, 300, fill=c, width=int(2.6 * u))
        d.polygon([P(7, -9), P(9, -1), P(2, -3)], fill=c)
    elif name == "question":
        d.arc([P(-5, -9), P(5, 1)], 180, 90, fill=c, width=int(2.6 * u))
        d.line([P(0, 1), P(0, 4)], fill=c, width=int(2.6 * u))
        d.ellipse([P(-1.5, 6), P(1.5, 9)], fill=c)


def finish(big):
    small = big.resize((S, S), Image.LANCZOS)
    # harden alpha so it reads as pixel art
    px = small.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 0 if a < 90 else 255)
    return small


def icon(frame_color, frame, sym, sym_color):
    big = badge(frame_color, frame)
    symbol(ImageDraw.Draw(big), sym, sym_color, 0.78 if frame in ("cross", "aug") else 0.9)
    return finish(big)


def all_icons():
    """Returns {model_name: Image}."""
    out = {}
    for cls in CLASSES:
        for b in range(3):
            col = rgb(BRANCH_COLORS[cls][b])
            out[f"skill_{cls}_{b}"] = icon(col, "normal", SYMBOLS[cls][b], mul(col, 1.15))
            out[f"skill_{cls}_{b}_ult"] = icon(col, "ult", SYMBOLS[cls][b], mul(col, 1.2))
        out[f"skill_{cls}_cross"] = icon(rgb("ff78ff"), "cross", "spark", rgb(CLASS_COLORS[cls]))
        cc = rgb(CLASS_COLORS[cls])
        out[f"class_{cls}"] = icon(cc, "normal", CLASS_SYMBOL[cls], mul(cc, 1.2))
    out["skill_augment"] = icon(rgb("ff8fe0"), "aug", "spark", rgb("ffe0f6"))
    out["skill_locked"] = icon(rgb("55505f"), "locked", "lock", rgb("8a8496"))
    out["skill_empty"] = icon(rgb("3a3448"), "locked", "spark", rgb("4e475e"))
    out["skill_book"] = icon(rgb("b79bff"), "normal", "book", rgb("9c6bff"))
    out["skill_respec"] = icon(rgb("ffc94d"), "normal", "cycle", rgb("ffd34d"))
    out["skill_help"] = icon(rgb("5abeff"), "normal", "question", rgb("8ff0ff"))
    return out


# ------------------------------------------------------------------ chest GUI backgrounds

BG = (24, 17, 38, 255)
PANEL = (38, 27, 60, 255)
EDGE = (92, 68, 140, 255)
EDGE_HI = (150, 120, 210, 255)


def panel(w, h):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=6, fill=EDGE)
    d.rounded_rectangle([2, 2, w - 3, h - 3], radius=5, fill=PANEL)
    # soft vertical gradient
    for y in range(3, h - 3):
        t = y / h
        d.line([(3, y), (w - 4, y)], fill=(int(40 - 10 * t), int(28 - 8 * t), int(64 - 14 * t), 255))
    d.line([(6, 1), (w - 7, 1)], fill=EDGE_HI)
    return img


def well(d, x, y, ring=None, glow=None):
    """An 18x18 slot well whose 16x16 item area starts at x+1, y+1."""
    if glow:
        d.rounded_rectangle([x - 1, y - 1, x + 18, y + 18], radius=3, fill=glow)
    d.rectangle([x, y, x + 17, y + 17], fill=(16, 11, 26, 255))
    d.rectangle([x + 1, y + 1, x + 16, y + 16], fill=(30, 22, 46, 255))
    d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=(70, 54, 100, 255))
    d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=(70, 54, 100, 255))
    if ring:
        d.rectangle([x, y, x + 17, y + 17], outline=ring)


def player_inventory(d, top):
    for r in range(3):
        for c in range(9):
            well(d, 7 + 18 * c, top + 18 * r)
    for c in range(9):
        well(d, 7 + 18 * c, top + 58)


def tree_bg():
    w, h = 176, 222
    img = panel(w, h)
    d = ImageDraw.Draw(img)
    # branch lanes: soft colored columns behind branch A (0-1), B (3-4), C (6-7)
    lanes = [(0, (255, 170, 60, 34)), (3, (120, 220, 255, 34)), (6, (140, 230, 110, 34))]
    lane_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lane_layer)
    for col, color in lanes:
        x = 7 + 18 * col
        ld.rounded_rectangle([x - 2, 15, x + 37, 17 + 18 * 5 + 1], radius=4, fill=color)
    img.alpha_composite(lane_layer)
    d = ImageDraw.Draw(img)
    # tier connectors down each lane
    for col, _ in lanes:
        for c2 in (col, col + 1):
            x = 7 + 18 * c2 + 9
            d.line([(x, 17 + 18), (x, 17 + 18 * 4)], fill=(80, 62, 120, 255))
    # tree wells, rows 0-4; ultimate row gets a gold ring
    for r in range(5):
        for col, _ in lanes:
            for c2 in (col, col + 1):
                well(d, 7 + 18 * c2, 17 + 18 * r, ring=(255, 211, 77, 255) if r == 4 else None)
    # cross skills sit between lanes on row 2, linked with magenta lines
    pink = (255, 120, 255, 255)
    for cx in (2, 5):
        x = 7 + 18 * cx
        d.line([(x - 1, 17 + 36 + 9), (x + 18, 17 + 36 + 9)], fill=pink)
        well(d, x, 17 + 36, ring=pink, glow=(255, 120, 255, 70))
    # column 8: info, respec, cross A+C, help
    well(d, 7 + 18 * 8, 17, ring=(183, 155, 255, 255))
    well(d, 7 + 18 * 8, 17 + 18, ring=(255, 201, 77, 255))
    well(d, 7 + 18 * 8, 17 + 36, ring=pink, glow=(255, 120, 255, 70))
    well(d, 7 + 18 * 8, 17 + 54, ring=(90, 190, 255, 255))
    # bottom row: 2 active slots (cyan) + augments (pink)
    y = 17 + 18 * 5
    d.line([(7, y - 2), (w - 8, y - 2)], fill=(80, 62, 120, 255))
    for c in (0, 1):
        well(d, 7 + 18 * c, y, ring=(90, 230, 255, 255), glow=(90, 230, 255, 60))
    for c in range(3, 9):
        well(d, 7 + 18 * c, y, ring=(255, 143, 224, 200))
    player_inventory(d, 139)
    return img


def small_bg(slots, extra=()):
    w, h = 176, 168
    img = panel(w, h)
    d = ImageDraw.Draw(img)
    for s in slots:
        r, c = divmod(s, 9)
        x, y = 7 + 18 * c, 17 + 18 * r
        d.ellipse([x - 5, y - 5, x + 22, y + 22], fill=(120, 80, 200, 60))
        well(d, x, y, ring=(183, 155, 255, 255), glow=(183, 155, 255, 80))
    for s in extra:
        r, c = divmod(s, 9)
        well(d, 7 + 18 * c, 17 + 18 * r)
    player_inventory(d, 85)
    return img


def gui_backgrounds():
    """Returns [(char, image)] for the aetherwar:gui font."""
    return [
        ("", tree_bg()),
        ("", small_bg([10, 12, 14, 16])),
        ("", small_bg([11, 13, 15], extra=[22])),
    ]
