"""
stitch_strip.py — stitch a folder of cap_XXXXXXXXXX.png frames into a horizontal strip.

Usage:
    python image/stitch_strip.py --input DIR [--output PATH] [--scale N] [--frames A B]

Options:
    --input   directory containing cap_XXXXXXXXXX.png files (required)
    --output  output strip PNG  (default: <project>/captures/strip.png)
    --scale   divide all pixel dimensions by N to get NES-native coords (default: 1)
    --frames  inclusive range of frame indices to include, e.g. --frames 209 286
              (indices are relative to the sorted file list in --input)

The strip is N_frames * frame_width wide and frame_height tall.
Frame 0 of the strip always corresponds to the first selected frame.

Dependencies: Pillow  (pip install pillow)
"""

import argparse
import os
import sys

from PIL import Image


def resolve_output(explicit):
    if explicit:
        return explicit
    toolbox_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(toolbox_dir))
    return os.path.join(project_root, 'captures', 'strip.png')


def main():
    parser = argparse.ArgumentParser(description='Stitch PNG frames into a horizontal strip.')
    parser.add_argument('--input', required=True, help='Directory of cap_XXXXXXXXXX.png files.')
    parser.add_argument('--output', help='Output strip PNG path.')
    parser.add_argument('--scale', type=int, default=1,
                        help='Pixel scale factor — strip will be divided by this to reach NES res.')
    parser.add_argument('--frames', type=int, nargs=2, metavar=('FIRST', 'LAST'),
                        help='Inclusive index range of frames to include.')
    args = parser.parse_args()

    if not os.path.isdir(args.input):
        sys.exit(f'ERROR: --input is not a directory: {args.input}')

    all_files = sorted(
        f for f in os.listdir(args.input) if f.endswith('.png')
    )
    if not all_files:
        sys.exit(f'ERROR: no captures found in {args.input}')

    if args.frames:
        first, last = args.frames
        selected = all_files[first:last + 1]
    else:
        selected = all_files

    if not selected:
        sys.exit('ERROR: frame range produced an empty selection.')

    sample = Image.open(os.path.join(args.input, selected[0]))
    fw, fh = sample.size
    n = len(selected)

    strip = Image.new('RGB', (fw * n, fh))
    for i, fname in enumerate(selected):
        frame = Image.open(os.path.join(args.input, fname))
        strip.paste(frame, (i * fw, 0))

    output_path = resolve_output(args.output)
    os.makedirs(os.path.dirname(output_path) or '.', exist_ok=True)
    strip.save(output_path)

    nes_w = fw // args.scale
    nes_h = fh // args.scale
    print(f'Stitched {n} frames ({fw}x{fh} px each, scale={args.scale} → NES {nes_w}x{nes_h})')
    print(f'Strip size: {fw * n} x {fh} px  →  {output_path}')


if __name__ == '__main__':
    main()
