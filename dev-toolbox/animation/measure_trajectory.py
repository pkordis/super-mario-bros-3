"""
measure_trajectory.py — per-frame Y tracking of a colour target in a strip PNG.

For each frame in the strip, finds the topmost pixel matching --color (within
--tolerance) inside an optional X window. Reports Y coordinates and offsets
relative to a reference row. Optionally prints a Java int[] literal.

Usage:
    python animation/measure_trajectory.py \\
        --strip PATH \\
        --color R G B [--tolerance N] \\
        --reference-row Y \\
        [--x-center X --x-radius R] \\
        [--scale N] \\
        [--output-java]

Coordinate system
-----------------
All coordinates are in NES pixels (strip pixels / scale).
--reference-row is the NES Y row of the anchor object (e.g. block top edge).
offset = reference_row - top_y_nes   (positive = sprite is ABOVE the reference)

Occlusion warning
-----------------
When another sprite with the same colour appears above the target, the measured
top Y will be wrong for those frames. Always verify anomalous values visually
using the individual cap frames in captures/output/<name>/.

Dependencies: Pillow  (pip install pillow)
"""

import argparse
import os
import sys

import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser(
        description='Measure per-frame Y trajectory of a coloured sprite in a strip PNG.')
    parser.add_argument('--strip', required=True, help='Path to the horizontal strip PNG.')
    parser.add_argument('--color', type=int, nargs=3, required=True,
                        metavar=('R', 'G', 'B'), help='Target colour.')
    parser.add_argument('--tolerance', type=int, default=15,
                        help='Per-channel tolerance (default: 15).')
    parser.add_argument('--reference-row', type=int, required=True,
                        help='NES Y of the reference anchor row (e.g. block top).')
    parser.add_argument('--x-center', type=int, default=None,
                        help='NES X centre of the expected sprite column.')
    parser.add_argument('--x-radius', type=int, default=None,
                        help='NES half-width of the X search window.')
    parser.add_argument('--scale', type=int, default=4,
                        help='Strip pixels per NES pixel (default: 4).')
    parser.add_argument('--output-java', action='store_true',
                        help='Print result as a Java int[] literal.')
    args = parser.parse_args()

    if not os.path.isfile(args.strip):
        sys.exit(f'ERROR: strip not found: {args.strip}')

    img = Image.open(args.strip).convert('RGB')
    arr = np.array(img)
    strip_w, strip_h = img.size
    scale = args.scale
    frame_w_px = strip_h  # Assume square-ish NES frames: frame width = detect from strip height
    # Actually compute number of frames from strip width / frame height ratio:
    # Each frame is the same height as the strip, width = (strip_w / n_frames).
    # We can't know n_frames without more info, but the frame width must be an integer.
    # Best heuristic: treat each 'column section' as frame_w_px wide.
    # For NES 256x240 at scale 4: frame is 1024x960. We detect frame_w from the strip dims.
    # The user knows how many frames were stitched; we infer by assuming aspect ratio.
    # Simple approach: frame width (in strip pixels) = strip height (since NES 256 is wider
    # than 240 so actually frame_w_px would be > strip_h — just make frame_w a param or
    # detect a reasonable default: NES 256x240 → at scale 4 = 1024x960).
    nes_frame_w = 256  # standard NES width
    frame_w_px = nes_frame_w * scale
    n_frames = strip_w // frame_w_px
    if n_frames == 0:
        sys.exit(f'ERROR: strip width {strip_w} is narrower than one frame ({frame_w_px}px).')

    r_target, g_target, b_target = args.color
    tol = args.tolerance

    # X window in strip pixels
    if args.x_center is not None and args.x_radius is not None:
        x_min_nes = args.x_center - args.x_radius
        x_max_nes = args.x_center + args.x_radius
        x_min_px = max(0, x_min_nes * scale)
        x_max_px = min(frame_w_px, x_max_nes * scale)
    else:
        x_min_px = 0
        x_max_px = frame_w_px

    print(f'Strip       : {args.strip}')
    print(f'Frames      : {n_frames}  (frame width {frame_w_px}px = NES {nes_frame_w}px)')
    print(f'Target color: rgb({r_target}, {g_target}, {b_target}) ±{tol}')
    print(f'Reference Y : {args.reference_row} NES px')
    if args.x_center is not None:
        print(f'X window    : NES {x_min_nes}–{x_max_nes}')
    print()
    print(f'{"Frame":>6} | {"Top Y (NES)":>12} | {"Offset":>8}')
    print('-' * 34)

    offsets = []
    for fi in range(n_frames):
        x0 = fi * frame_w_px + x_min_px
        x1 = fi * frame_w_px + x_max_px
        region = arr[:, x0:x1]

        matches = (
            (np.abs(region[:, :, 0].astype(int) - r_target) <= tol) &
            (np.abs(region[:, :, 1].astype(int) - g_target) <= tol) &
            (np.abs(region[:, :, 2].astype(int) - b_target) <= tol)
        )
        rows = np.where(np.any(matches, axis=1))[0]

        if rows.size > 0:
            top_nes = int(rows[0]) // scale
            offset = args.reference_row - top_nes
            offsets.append(offset)
            print(f'{fi:>6} | {top_nes:>12} | {offset:>8}')
        else:
            print(f'{fi:>6} | {"(not found)":>12} | {"":>8}')

    if args.output_java and offsets:
        print()
        values = ', '.join(str(v) for v in offsets)
        print(f'private static final int[] Y_OFFSETS = {{')
        # Wrap at 10 values per line
        for i in range(0, len(offsets), 10):
            chunk = ', '.join(str(v) for v in offsets[i:i + 10])
            print(f'    {chunk},')
        print('};')


if __name__ == '__main__':
    main()
