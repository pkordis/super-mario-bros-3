"""
compare_frames.py — pixel-level diff of two PNG frames or strips.

Highlights changed regions in red; dims unchanged pixels. Prints a summary
of changed pixel count and bounding box. Useful for confirming that a code
change only affects the intended pixels.

Usage:
    python image/compare_frames.py --a PATH --b PATH [--output PATH]

If --output is omitted, the diff image is saved alongside --a as <name>_diff.png.

Dependencies: Pillow, numpy  (pip install pillow numpy)
"""

import argparse
import os
import sys

import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description='Pixel-level diff of two PNG images.')
    parser.add_argument('--a', required=True, help='First image (reference).')
    parser.add_argument('--b', required=True, help='Second image (comparison).')
    parser.add_argument('--output', help='Output diff image path.')
    args = parser.parse_args()

    for path in (args.a, args.b):
        if not os.path.isfile(path):
            sys.exit(f'ERROR: file not found: {path}')

    img_a = Image.open(args.a).convert('RGB')
    img_b = Image.open(args.b).convert('RGB')

    if img_a.size != img_b.size:
        print(f'WARNING: sizes differ — a={img_a.size}, b={img_b.size}. Cropping to overlap.')
        w = min(img_a.width, img_b.width)
        h = min(img_a.height, img_b.height)
        img_a = img_a.crop((0, 0, w, h))
        img_b = img_b.crop((0, 0, w, h))

    arr_a = np.array(img_a, dtype=np.int16)
    arr_b = np.array(img_b, dtype=np.int16)

    diff = np.abs(arr_a - arr_b).sum(axis=2)  # sum of channel differences per pixel
    changed = diff > 0

    n_changed = int(changed.sum())
    total = changed.size

    # Build output image: dim unchanged pixels, highlight changed in red
    out = arr_a.astype(np.uint8).copy()
    out[~changed] = (out[~changed] // 3)           # dim unchanged
    out[changed] = np.array([220, 40, 40], dtype=np.uint8)  # red highlight

    if n_changed > 0:
        ys, xs = np.where(changed)
        bbox = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))
    else:
        bbox = None

    output_path = args.output
    if not output_path:
        base, ext = os.path.splitext(args.a)
        output_path = base + '_diff' + (ext or '.png')

    Image.fromarray(out).save(output_path)

    print(f'Changed pixels : {n_changed} / {total}  ({100.0 * n_changed / total:.2f}%)')
    if bbox:
        print(f'Bounding box   : x={bbox[0]}..{bbox[2]}, y={bbox[1]}..{bbox[3]}')
    else:
        print('Images are identical.')
    print(f'Diff saved to  : {output_path}')


if __name__ == '__main__':
    main()
