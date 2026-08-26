"""
dump_colors.py — print all distinct RGBA colours in a PNG, sorted by frequency.

This is the essential first step when you don't know the exact RGB values of a
target sprite. Run it on a strip or a cropped region before passing a --color
argument to measure_trajectory.py.

Usage:
    python misc/dump_colors.py --input PATH [--x X --y Y --w W --h H] [--top N] [--scale S]

    --x/y/w/h  optional crop rectangle in NES pixels (requires --scale)
    --top N    show only the N most frequent colours (default: 30)
    --scale    NES pixels per strip pixel (default: 1 — raw pixel coords)

Dependencies: Pillow, numpy  (pip install pillow numpy)
"""

import argparse
import os
import sys

import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser(
        description='List distinct colours in a PNG sorted by frequency.')
    parser.add_argument('--input', required=True, help='Path to PNG file.')
    parser.add_argument('--x', type=int, default=None, help='Crop left edge (NES px).')
    parser.add_argument('--y', type=int, default=None, help='Crop top edge (NES px).')
    parser.add_argument('--w', type=int, default=None, help='Crop width (NES px).')
    parser.add_argument('--h', type=int, default=None, help='Crop height (NES px).')
    parser.add_argument('--scale', type=int, default=1,
                        help='Strip pixels per NES pixel (default: 1).')
    parser.add_argument('--top', type=int, default=30,
                        help='Number of top colours to show (default: 30).')
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        sys.exit(f'ERROR: file not found: {args.input}')

    img = Image.open(args.input).convert('RGB')

    if args.x is not None or args.y is not None:
        scale = args.scale
        x0 = (args.x or 0) * scale
        y0 = (args.y or 0) * scale
        w = (args.w * scale) if args.w is not None else (img.width - x0)
        h = (args.h * scale) if args.h is not None else (img.height - y0)
        img = img.crop((x0, y0, x0 + w, y0 + h))

    arr = np.array(img).reshape(-1, 3)
    unique, counts = np.unique(arr, axis=0, return_counts=True)
    order = np.argsort(-counts)

    top = args.top
    print(f'{"Rank":>5}  {"R":>3} {"G":>3} {"B":>3}  {"Count":>8}  Hex')
    print('-' * 45)
    for rank, idx in enumerate(order[:top], start=1):
        r, g, b = unique[idx]
        count = counts[idx]
        print(f'{rank:>5}  {r:>3} {g:>3} {b:>3}  {count:>8}  #{r:02x}{g:02x}{b:02x}')

    total = int(counts.sum())
    shown = int(counts[order[:top]].sum())
    print(f'\nTop {min(top, len(order))} colours account for {shown}/{total} pixels '
          f'({100.0 * shown / total:.1f}%)')


if __name__ == '__main__':
    main()
