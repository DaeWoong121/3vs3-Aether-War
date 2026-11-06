#!/usr/bin/env python3
"""
Builds the Aether War resource pack (Minecraft 1.21.4, pack_format 46) from code:
pixel-art textures, HUD font icons, boss bar skins and synthesized sound effects.

    python3 tools/build_resourcepack.py

Writes resourcepack/AetherWar-pack.zip (and the unpacked tree under build/resourcepack).
Needs Pillow and numpy; ffmpeg (libvorbis) for the sounds.
"""
import json, math, os, shutil, subprocess, sys, zipfile, hashlib, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skill_assets

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build", "resourcepack")
ZIP = os.path.join(ROOT, "resourcepack", "AetherWar-pack.zip")
NS = "aetherwar"


def path(*p):
    full = os.path.join(OUT, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def write_json(obj, *p):
    with open(path(*p), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# ------------------------------------------------------------------ pixel art helpers

def sprite(rows, palette):
    """rows: list of equal-length strings; '.' is transparent; other chars index the palette."""
    h, w = len(rows), len(rows[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img


CRYSTAL_ROWS = [
    "................",
    ".......aa.......",
    "......abba......",
    ".....abcbba.....",
    ".....abccbba....",
    "....abcwcbbda...",
    "....abccwcbda...",
    "...abccccwbdda..",
    "...abcccccbdda..",
    "...abbcccbbdda..",
    "....abbcbbddaa..",
    "....aabbbddaa...",
    ".....aabddaa....",
    "......aadaa.....",
    ".......aa.......",
    "................",
]
ETHER = {"a": hexc("2a1b5c"), "b": hexc("7b4dff"), "c": hexc("b79bff"), "d": hexc("4d2fb8"), "w": hexc("f4efff")}
REFINED = {"a": hexc("0d3b4f"), "b": hexc("27b3d9"), "c": hexc("8ff0ff"), "d": hexc("147a99"), "w": hexc("ffffff")}


def glow_icon(base, size, glow_rgb):
    """Upscales a 16px sprite and adds a soft glow halo (for HUD/GUI icons)."""
    big = base.resize((size, size), Image.NEAREST)
    halo = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    alpha = big.split()[3].filter(ImageFilter.GaussianBlur(size / 10))
    halo.paste(Image.new("RGBA", (size, size), glow_rgb + (170,)), (0, 0), alpha)
    halo.alpha_composite(big)
    return halo


def boss_bar(color_a, color_b, background=False):
    w, h = 182, 5
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        t = x / (w - 1)
        r = int(color_a[0] + (color_b[0] - color_a[0]) * t)
        g = int(color_a[1] + (color_b[1] - color_a[1]) * t)
        b = int(color_a[2] + (color_b[2] - color_a[2]) * t)
        for y in range(h):
            if background:
                c = (22, 18, 34, 230) if 0 < y < h - 1 else (60, 48, 90, 255)
                if x in (0, w - 1):
                    c = (60, 48, 90, 255)
            else:
                shade = [1.35, 1.15, 1.0, 0.85, 0.7][y]
                c = (min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)), 255)
                if (x % 18) == 17:  # segment ticks
                    c = (int(c[0] * 0.75), int(c[1] * 0.75), int(c[2] * 0.75), 255)
            img.putpixel((x, y), c)
    return img


def small_icon(rows, palette):
    return sprite(rows, palette)


CLOCK = [
    "..aaaaa..",
    ".abbbbba.",
    "abbbcbbba",
    "abbbcbbba",
    "abbbccbba",
    "abbbbbbba",
    "abbbbbbba",
    ".abbbbba.",
    "..aaaaa..",
]
SWORD = [
    ".......aa",
    "......aba",
    ".....aba.",
    "....aba..",
    "c..aba...",
    ".cabа....".replace("а", "a"),
    "..cc.....",
    ".c.c.....",
    "c........",
]
STAR = [
    "....a....",
    "....a....",
    "...aba...",
    "aaabbbaaa",
    ".abbbbba.",
    "..abbba..",
    "..ab.ba..",
    ".ab...ba.",
    ".a.....a.",
]
SHIELD = [
    "aaaaaaaaa",
    "abbbbbbba",
    "abbbcbbba",
    "abbcccbba",
    "abbbcbbba",
    ".abbbbba.",
    ".abbbbba.",
    "..abbba..",
    "...aaa...",
]


def build_textures():
    crystal = sprite(CRYSTAL_ROWS, ETHER)
    refined = sprite(CRYSTAL_ROWS, REFINED)
    crystal.save(path("assets", NS, "textures", "item", "ether_crystal.png"))
    refined.save(path("assets", NS, "textures", "item", "refined_crystal.png"))

    # HUD font icons (bitmap font, private use area)
    icons = {
        "": (glow_icon(crystal, 16, (150, 110, 255)), 8, 7),
        "": (small_icon(CLOCK, {"a": hexc("3b2a63"), "b": hexc("f1e9ff"), "c": hexc("7b4dff")}), 8, 7),
        "": (small_icon(SWORD, {"a": hexc("4b4b5a"), "b": hexc("e8e8f0"), "c": hexc("a86b2d")}), 8, 7),
        "": (small_icon(STAR, {"a": hexc("9a6a00"), "b": hexc("ffd34d")}), 8, 7),
        "": (small_icon(SHIELD, {"a": hexc("1c5a7a"), "b": hexc("5fd3ff"), "c": hexc("ffffff")}), 8, 7),
        "": (team_dot(hexc("e5484d")), 8, 7),
        "": (team_dot(hexc("3e7bfa")), 8, 7),
    }
    providers = [{"type": "space", "advances": {" ": 4}}]
    for ch, (img, height, ascent) in icons.items():
        name = "icon_%04x" % ord(ch)
        img.save(path("assets", NS, "textures", "font", name + ".png"))
        providers.append({"type": "bitmap", "file": f"{NS}:font/{name}.png", "height": height, "ascent": ascent, "chars": [ch]})
    # keep vanilla glyphs, add ours on top of the default font
    providers.insert(0, {"type": "reference", "id": "minecraft:include/default"})
    write_json({"providers": providers}, "assets", "minecraft", "font", "default.json")

    # boss bars: red = 홍팀 (ember), blue = 청팀 (sky)
    sprites = ("assets", "minecraft", "textures", "gui", "sprites", "boss_bar")
    boss_bar((255, 92, 92), (255, 170, 90)).save(path(*sprites, "red_progress.png"))
    boss_bar((0, 0, 0), (0, 0, 0), background=True).save(path(*sprites, "red_background.png"))
    boss_bar((70, 130, 255), (110, 230, 255)).save(path(*sprites, "blue_progress.png"))
    boss_bar((0, 0, 0), (0, 0, 0), background=True).save(path(*sprites, "blue_background.png"))
    boss_bar((170, 110, 255), (255, 140, 230)).save(path(*sprites, "purple_progress.png"))
    boss_bar((0, 0, 0), (0, 0, 0), background=True).save(path(*sprites, "purple_background.png"))

    # pack icon: big glowing crystal on a night-violet gradient
    icon = Image.new("RGBA", (128, 128))
    d = ImageDraw.Draw(icon)
    for y in range(128):
        t = y / 127
        d.line([(0, y), (127, y)], fill=(int(30 + 40 * t), int(16 + 10 * t), int(70 + 40 * t), 255))
    icon.alpha_composite(glow_icon(crystal, 112, (170, 120, 255)), (8, 8))
    icon.save(path("pack.png"))


def team_dot(color):
    img = Image.new("RGBA", (7, 7), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, 6, 6], fill=color, outline=(255, 255, 255, 200))
    return img


def build_item_models():
    for name in ("ether_crystal", "refined_crystal"):
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}},
                   "assets", NS, "models", "item", name + ".json")
    # vanilla amethyst shard switches to our models when the plugin sets custom_model_data strings
    write_json({
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [
                {"when": "ether_crystal", "model": {"type": "minecraft:model", "model": f"{NS}:item/ether_crystal"}},
                {"when": "refined_crystal", "model": {"type": "minecraft:model", "model": f"{NS}:item/refined_crystal"}},
            ],
            "fallback": VANILLA["amethyst_shard"],
        }
    }, "assets", "minecraft", "items", "amethyst_shard.json")


def item_select(fallback, names):
    return {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": n, "model": {"type": "minecraft:model", "model": f"{NS}:item/{n}"}} for n in names],
            "fallback": VANILLA[fallback],
        }
    }


def build_skill_assets():
    icons = skill_assets.all_icons()
    for name, img in icons.items():
        img.save(path("assets", NS, "textures", "item", name + ".png"))
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}},
                   "assets", NS, "models", "item", name + ".json")
    skill_names = [n for n in icons if not n.startswith("class_")]
    # skill tree GUI icons are paper; equipped actives are nether star / heart of the sea; menu is the recovery compass
    write_json(item_select("paper", list(icons)), "assets", "minecraft", "items", "paper.json")
    write_json(item_select("nether_star", skill_names), "assets", "minecraft", "items", "nether_star.json")
    write_json(item_select("heart_of_the_sea", skill_names), "assets", "minecraft", "items", "heart_of_the_sea.json")
    # class icons on their own materials (keep vanilla look otherwise)
    for cls, mat in (("warrior", "iron_sword"), ("miner", "iron_pickaxe"), ("alchemist", "brewing_stand"), ("builder", "bricks")):
        write_json(item_select(mat, [f"class_{cls}"]), "assets", "minecraft", "items", mat + ".json")
    # recovery compass keeps its animated vanilla item definition unless it is the skill book
    write_json({
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": "skill_book", "model": {"type": "minecraft:model", "model": f"{NS}:item/skill_book"}}],
            "fallback": VANILLA["recovery_compass"],
        }
    }, "assets", "minecraft", "items", "recovery_compass.json")

    # chest GUI backgrounds via the title font
    providers = [{"type": "space", "advances": {"\uf808": -8, "\uf809": -169}}]
    for ch, img in skill_assets.gui_backgrounds():
        name = "gui_%04x" % ord(ch)
        img.save(path("assets", NS, "textures", "font", name + ".png"))
        providers.append({"type": "bitmap", "file": f"{NS}:font/{name}.png", "height": img.height, "ascent": 13, "chars": [ch]})
    write_json({"providers": providers}, "assets", NS, "font", "gui.json")


# vanilla 1.21.4 item definitions (copied from the client jar), used as fallbacks
VANILLA = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla_items.json"), encoding="utf-8"))


# ------------------------------------------------------------------ sound synthesis

RATE = 44100


def tone(freq, dur, amp=0.5, decay=4.0, harmonics=((1, 1.0), (2, 0.35), (3, 0.15), (4.2, 0.08))):
    t = np.arange(int(RATE * dur)) / RATE
    env = np.exp(-decay * t) * np.minimum(1, t * 200)
    sig = sum(a * np.sin(2 * np.pi * freq * m * t) for m, a in harmonics)
    return amp * env * sig


def mix(parts, total):
    out = np.zeros(int(RATE * total))
    for start, sig in parts:
        i = int(RATE * start)
        n = min(len(sig), len(out) - i)
        out[i:i + n] += sig[:n]
    peak = np.max(np.abs(out)) or 1
    return out / peak * 0.85


def noise_swell(dur, lo=60, amp=0.6, rise=True):
    n = int(RATE * dur)
    rnd = np.random.default_rng(3).standard_normal(n)
    # crude low-pass via cumulative moving average
    k = int(RATE / (lo * 4))
    kern = np.ones(k) / k
    low = np.convolve(rnd, kern, mode="same")
    t = np.arange(n) / n
    env = (t if rise else (1 - t)) ** 1.5 * np.minimum(1, (1 - t) * 8)
    return amp * low / (np.max(np.abs(low)) or 1) * env


def note(n):  # semitones from A4
    return 440 * 2 ** (n / 12)


def build_sounds():
    sounds = {
        # rising sparkly arpeggio when crystals reach the core
        "deliver": mix([(0, tone(note(3), 0.6, 0.6)), (0.07, tone(note(7), 0.6, 0.5)), (0.14, tone(note(10), 0.6, 0.5)),
                        (0.21, tone(note(15), 0.9, 0.5, 3))], 1.2),
        # crystalline chip when a node is harvested
        "harvest": mix([(0, tone(note(19), 0.35, 0.6, 9, ((1, 1), (2.76, 0.4), (5.4, 0.2)))),
                        (0.04, tone(note(26), 0.4, 0.4, 8, ((1, 1), (2.76, 0.3))))], 0.5),
        # deep gong + shimmer at each phase
        "phase": mix([(0, tone(note(-26), 3.0, 0.9, 1.0, ((1, 1), (2.01, 0.5), (2.98, 0.35), (4.1, 0.2)))),
                      (0.0, noise_swell(1.2, 300, 0.2, rise=False)),
                      (0.3, tone(note(14), 2.0, 0.25, 1.5)), (0.45, tone(note(19), 2.0, 0.2, 1.5))], 3.2),
        # ominous rumble for a rift surge
        "surge": mix([(0, noise_swell(2.2, 40, 0.9)), (0.2, tone(note(-34), 2.0, 0.7, 1.2)),
                      (1.4, tone(note(-22), 1.4, 0.5, 2.0, ((1, 1), (1.5, 0.6), (2, 0.3))))], 2.8),
        # two-note confirmation when a node is captured
        "capture": mix([(0, tone(note(7), 0.5, 0.6, 5)), (0.12, tone(note(14), 0.8, 0.6, 4))], 1.0),
        # victory fanfare
        "victory": mix([(0, tone(note(3), 0.5, 0.6, 3)), (0.15, tone(note(7), 0.5, 0.6, 3)), (0.3, tone(note(10), 0.5, 0.6, 3)),
                        (0.45, tone(note(15), 1.6, 0.7, 1.4)), (0.45, tone(note(10), 1.6, 0.4, 1.4)), (0.45, tone(note(3), 1.6, 0.4, 1.4))], 2.3),
        # the great rift opens
        "rift_open": mix([(0, noise_swell(1.5, 80, 0.7)), (1.2, tone(note(-14), 2.5, 0.8, 1.0, ((1, 1), (1.5, 0.5), (2.01, 0.4), (3, 0.2)))),
                          (1.3, tone(note(10), 2.0, 0.3, 1.2)), (1.45, tone(note(17), 2.0, 0.25, 1.2))], 3.6),
        # alarm when your core is hit
        "core_hit": mix([(0, tone(note(-5), 0.25, 0.7, 10, ((1, 1), (3, 0.5)))), (0.12, tone(note(-8), 0.3, 0.7, 10, ((1, 1), (3, 0.5))))], 0.5),
    }
    defs = {}
    tmp = os.path.join(ROOT, "build", "wav")
    os.makedirs(tmp, exist_ok=True)
    for name, sig in sounds.items():
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((sig * 32767).astype(np.int16).tobytes())
        ogg = path("assets", NS, "sounds", name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)
        defs[name] = {"sounds": [{"name": f"{NS}:{name}"}], "subtitle": f"subtitles.{NS}.{name}"}
    write_json(defs, "assets", NS, "sounds.json")


def build_lang():
    ko = {
        f"subtitles.{NS}.deliver": "결정 납품", f"subtitles.{NS}.harvest": "결정 채굴", f"subtitles.{NS}.phase": "페이즈 전환",
        f"subtitles.{NS}.surge": "균열 폭주", f"subtitles.{NS}.capture": "노드 점령", f"subtitles.{NS}.victory": "승리",
        f"subtitles.{NS}.rift_open": "대균열 개방", f"subtitles.{NS}.core_hit": "코어 피격",
    }
    write_json(ko, "assets", NS, "lang", "ko_kr.json")
    write_json(ko, "assets", NS, "lang", "en_us.json")


def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    write_json({"pack": {"pack_format": 46, "description": "§d§lAether War §r§7- 에테르 결정 쟁탈전"}}, "pack.mcmeta")
    build_textures()
    build_item_models()
    build_skill_assets()
    build_sounds()
    build_lang()
    os.makedirs(os.path.dirname(ZIP), exist_ok=True)
    with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(OUT):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                info = zipfile.ZipInfo(os.path.relpath(full, OUT).replace(os.sep, "/"), date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(full, "rb") as fh:
                    z.writestr(info, fh.read())
    sha1 = hashlib.sha1(open(ZIP, "rb").read()).hexdigest()
    print(f"wrote {ZIP} ({os.path.getsize(ZIP)} bytes) sha1={sha1}")


if __name__ == "__main__":
    main()
