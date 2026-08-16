#!/usr/bin/env python3
"""Generate Better Trees' mod menu icon: an oak canopy with the stepped
edge the mod's leaf stairs give a tree, over an oak log trunk.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow,
the same script generated art approach as the rest of the suite. Deterministic:
re-running produces identical bytes. Source pixels are read straight out of the
vanilla Minecraft jar and scaled nearest neighbour, never smoothed.

Usage: python3 generate_icon.py [path/to/minecraft.jar]
"""

import glob
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/better-trees-justfatlard/icon.png")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the icon is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def scale(pixels, n):
    """Nearest neighbour only: these are pixel textures, never smooth them."""
    return [[px for px in row for _ in range(n)] for row in pixels for _ in range(n)]

def blank(width, height):
    return [[CLEAR] * width for _ in range(height)]

def tint(pixels, rgb):
    """Multiply by a colour, the way the game tints its greyscale foliage."""
    return [[(px[0] * rgb[0] // 255, px[1] * rgb[1] // 255,
              px[2] * rgb[2] // 255, px[3]) for px in row] for row in pixels]


# Plains foliage green: oak_leaves ships greyscale and the game tints it, so
# an untinted upscale would be a grey square.
FOLIAGE = (0x5C, 0xA8, 0x33)
OUTLINE = (0x1C, 0x38, 0x10, 0xFF)

# The canopy edge steps in two pixel increments, which is the mod's whole
# point: tree edges are leaf stairs rather than a flat cliff of leaves.
CANOPY = {1: (6, 9), 2: (5, 10), 3: (4, 11), 4: (3, 12), 5: (2, 13),
          6: (1, 14), 7: (1, 14), 8: (2, 13), 9: (3, 12), 10: (4, 11), 11: (6, 9)}


def build_icon():
    leaves = tint(vanilla("block/oak_leaves.png"), FOLIAGE)
    log = vanilla("block/oak_log.png")
    canvas = blank(16, 16)

    # oak_leaves is drawn with see-through gaps. At icon size the canopy has to
    # be a solid mass, so gaps fall back to the texture's own average colour.
    opaque = [px for row in leaves for px in row if px[3]]
    fill = (sum(px[0] for px in opaque) // len(opaque),
            sum(px[1] for px in opaque) // len(opaque),
            sum(px[2] for px in opaque) // len(opaque), 0xFF)

    for y in range(11, 16):
        for x in range(6, 10):
            canvas[y][x] = log[y][x]

    for y, (x0, x1) in CANOPY.items():
        for x in range(x0, x1 + 1):
            px = leaves[y][x]
            canvas[y][x] = px if px[3] else fill

    # dark rim so the silhouette holds when the menu draws this at 32px
    rimmed = [row[:] for row in canvas]
    for y in CANOPY:
        for x in range(16):
            if canvas[y][x][3] == 0:
                continue
            if any(not (0 <= y + dy < 16 and 0 <= x + dx < 16)
                   or canvas[y + dy][x + dx][3] == 0
                   for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                rimmed[y][x] = OUTLINE
    return scale(rimmed, 8)


if __name__ == "__main__":
    icon = build_icon()
    assert len(icon) == 128 and len(icon[0]) == 128, "mod menu icons are 128x128"
    write_png(OUT, icon)
