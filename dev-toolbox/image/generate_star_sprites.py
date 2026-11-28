"""
generate_star_sprites.py - write the star item and the air-flip player frames from the CHR banks.

Usage (from dev-toolbox):
    python image/generate_star_sprites.py

Writes src/main/resources/sprites/reward/star/star.png and the four somersault_N.png frames of the
normal and raccoon suits. See kb/star.md, "Sprites".

Dependencies: Pillow  (pip install pillow)
"""

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import extract_player_frame as frames

RESOURCES = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'sprites')
SOMERSAULT_PATTERNS = [
    [0x01, 0x03, 0x05, 0x07, 0x09, 0x0B],
    [0x0D, 0x0F, 0x39, 0x3B, 0x3D, 0x3F],
    [0x19, 0x1B, 0x1D, 0x1F, 0x21, 0x23],
    [0x25, 0x27, 0x29, 0x2B, 0x2D, 0x2F],
]
SUIT_BANKS = {'normal': 85, 'raccoon': 1}
STARMAN_NORMAL = [(0, 0, 0, 0), (0, 0, 0, 255), (252, 252, 252, 255), (181, 49, 32, 255)]
STARMAN_RED = frames.COLOURS


def starman(palette, name):
    tiles = Image.open(os.path.join(frames.CHR_DIRECTORY, 'chr004.pcx'))
    half = Image.new('RGBA', (8, 16), palette[0])
    for row in range(2):
        tile = 0x14 + row
        for y in range(8):
            for x in range(8):
                index = tiles.getpixel(((tile % 16) * 8 + x, (tile // 16) * 8 + y))
                half.putpixel((x, row * 8 + y), palette[index])
    image = Image.new('RGBA', (16, 16), palette[0])
    image.paste(half, (0, 0))
    image.paste(half.transpose(Image.FLIP_LEFT_RIGHT), (8, 0))
    directory = os.path.join(RESOURCES, 'reward', 'starman')
    os.makedirs(directory, exist_ok=True)
    image.save(os.path.join(directory, name))

def somersaults():
    for suit, bank in SUIT_BANKS.items():
        directory = os.path.join(RESOURCES, 'player', 'mario', 'level', suit)
        for number, patterns in enumerate(SOMERSAULT_PATTERNS, start=1):
            frames.render(bank, patterns).save(os.path.join(directory, 'somersault_%d.png' % number))


if __name__ == '__main__':
    starman(STARMAN_NORMAL, 'star_normal.png')
    starman(STARMAN_RED, 'star_red.png')
    somersaults()
