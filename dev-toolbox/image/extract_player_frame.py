"""
extract_player_frame.py - render one player animation frame from the disassembly's CHR banks.

A frame is six 8x16 sprites (three across, two down) taken from one 1 KB CHR bank. This tool
draws them on a 24x32 canvas, facing left, with Mario's three colours, the way the player
sprites in src/main/resources/sprites/player are authored.

Usage:
    python image/extract_player_frame.py --bank 85 --patterns 01,03,05,07,09,0B --out out.png
    python image/extract_player_frame.py --bank 84 --patterns 01,03,F1,05,07,F1 --compare ref.png

    --bank       CHR bank number (decimal) holding the frame's tiles
    --patterns   six sprite patterns in hex, the upper row first; F1 leaves a cell empty
    --out        write the frame here
    --compare    report how many pixels differ from an existing frame

Dependencies: Pillow  (pip install pillow)
"""

import argparse
import os

from PIL import Image

CHR_DIRECTORY = os.path.expanduser('~/Projects/smb3dasm/CHR')
COLOURS = [(0, 0, 0, 0), (181, 49, 32, 255), (255, 204, 197, 255), (0, 0, 0, 255)]
EMPTY_PATTERN = 0xF1


def render(bank, patterns):
    tiles = Image.open(os.path.join(CHR_DIRECTORY, 'chr%03d.pcx' % bank))
    canvas = Image.new('RGBA', (24, 32), COLOURS[0])
    for cell, pattern in enumerate(patterns):
        if pattern == EMPTY_PATTERN:
            continue
        column = cell % 3
        row = cell // 3
        for half in range(2):
            tile = (pattern & 0xFE) + half
            source_x = (tile % 16) * 8
            source_y = (tile // 16) * 8
            for y in range(8):
                for x in range(8):
                    index = tiles.getpixel((source_x + x, source_y + y))
                    canvas.putpixel((column * 8 + x, row * 16 + half * 8 + y), COLOURS[index])
    return canvas


def main():
    parser = argparse.ArgumentParser(description='Render a player frame from CHR.')
    parser.add_argument('--bank', type=int, required=True)
    parser.add_argument('--patterns', required=True)
    parser.add_argument('--out')
    parser.add_argument('--compare')
    arguments = parser.parse_args()

    patterns = [int(value, 16) for value in arguments.patterns.split(',')]
    frame = render(arguments.bank, patterns)
    if arguments.out:
        frame.save(arguments.out)
    if arguments.compare:
        reference = Image.open(arguments.compare).convert('RGBA')
        differing = sum(1 for a, b in zip(frame.getdata(), reference.getdata()) if a != b)
        print('differing pixels:', differing, 'of', 24 * 32)


if __name__ == '__main__':
    main()
