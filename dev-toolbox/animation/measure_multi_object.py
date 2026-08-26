"""
measure_multi_object.py — track several coloured objects simultaneously across a strip.

Like measure_trajectory.py but handles multiple objects in one pass, with a
separate column per object. Useful when a bouncing block, a coin, and a score
popup all appear in the same capture and you need their Y positions together.

Usage:
    python animation/measure_multi_object.py \\
        --strip PATH \\
        --objects "label:R,G,B" ["label2:R,G,B:xcenter=N:xradius=N" ...] \\
        --reference-row Y \\
        [--tolerance N] \\
        [--scale N] \\
        [--output-java]

Object spec format:
    label:R,G,B
    label:R,G,B:xcenter=X:xradius=R

Example:
    --objects "coin:234,158,34:xcenter=128:xradius=20" "score:255,254,255"

Dependencies: Pillow, numpy  (pip install pillow numpy)
"""

import argparse
import os
import re
import sys

import numpy as np
from PIL import Image


def parse_object_spec(spec):
    """Parse 'label:R,G,B[:xcenter=N:xradius=N]' into a dict."""
    parts = spec.split(':')
    if len(parts) < 2:
        sys.exit(f'ERROR: invalid --objects spec: {spec!r}')
    label = parts[0]
    rgb = tuple(int(v) for v in parts[1].split(','))
    if len(rgb) != 3:
        sys.exit(f'ERROR: expected R,G,B in spec: {spec!r}')
    result = {'label': label, 'color': rgb, 'xcenter': None, 'xradius': None}
    for extra in parts[2:]:
        m = re.fullmatch(r'(xcenter|xradius)=(\d+)', extra)
        if m:
            result[m.group(1)] = int(m.group(2))
    return result


def main():
    parser = argparse.ArgumentParser(
        description='Track multiple coloured objects simultaneously across a strip PNG.')
    parser.add_argument('--strip', required=True, help='Path to the horizontal strip PNG.')
    parser.add_argument('--objects', required=True, nargs='+',
                        help='Object specs: "label:R,G,B[:xcenter=X:xradius=R]"')
    parser.add_argument('--reference-row', type=int, required=True,
                        help='NES Y of the reference anchor row.')
    parser.add_argument('--tolerance', type=int, default=15,
                        help='Per-channel colour tolerance (default: 15).')
    parser.add_argument('--scale', type=int, default=4,
                        help='Strip pixels per NES pixel (default: 4).')
    parser.add_argument('--output-java', action='store_true',
                        help='Print a Java int[] per object at the end.')
    args = parser.parse_args()

    if not os.path.isfile(args.strip):
        sys.exit(f'ERROR: strip not found: {args.strip}')

    objects = [parse_object_spec(s) for s in args.objects]

    img = Image.open(args.strip).convert('RGB')
    arr = np.array(img)
    strip_w = img.size[0]
    scale = args.scale
    nes_frame_w = 256
    frame_w_px = nes_frame_w * scale
    n_frames = strip_w // frame_w_px
    if n_frames == 0:
        sys.exit(f'ERROR: strip too narrow for one frame at scale {scale}.')

    tol = args.tolerance

    # Header
    col_labels = ' | '.join(f'{obj["label"]:>12}' for obj in objects)
    print(f'{"Frame":>6} | {col_labels}')
    print('-' * (10 + 15 * len(objects)))

    results = {obj['label']: [] for obj in objects}

    for fi in range(n_frames):
        row_parts = [f'{fi:>6}']
        for obj in objects:
            label = obj['label']
            r_t, g_t, b_t = obj['color']

            if obj['xcenter'] is not None and obj['xradius'] is not None:
                x0 = fi * frame_w_px + max(0, (obj['xcenter'] - obj['xradius']) * scale)
                x1 = fi * frame_w_px + min(frame_w_px, (obj['xcenter'] + obj['xradius']) * scale)
            else:
                x0 = fi * frame_w_px
                x1 = fi * frame_w_px + frame_w_px

            region = arr[:, x0:x1]
            matches = (
                (np.abs(region[:, :, 0].astype(int) - r_t) <= tol) &
                (np.abs(region[:, :, 1].astype(int) - g_t) <= tol) &
                (np.abs(region[:, :, 2].astype(int) - b_t) <= tol)
            )
            rows = np.where(np.any(matches, axis=1))[0]

            if rows.size > 0:
                top_nes = int(rows[0]) // scale
                offset = args.reference_row - top_nes
                results[label].append(offset)
                row_parts.append(f'{offset:>12}')
            else:
                results[label].append(None)
                row_parts.append(f'{"(none)":>12}')

        print(' | '.join(row_parts))

    if args.output_java:
        print()
        for obj in objects:
            label = obj['label']
            values = [str(v) if v is not None else '/* not found */' for v in results[label]]
            print(f'// {label} Y offsets')
            print(f'private static final int[] {label.upper()}_Y_OFFSETS = {{')
            for i in range(0, len(values), 10):
                chunk = ', '.join(values[i:i + 10])
                print(f'    {chunk},')
            print('};')
            print()


if __name__ == '__main__':
    main()
