"""
crop_region.py — crop a fixed NES-pixel rectangle from every frame in a strip or folder.

Writes the result as a new strip (or individual files) so you can isolate one
sprite or tile before running colour analysis or diffing.

Usage:
    python image/crop_region.py \\
        --input PATH \\
        [--x X --y Y --w W --h H] \\
        [--scale N] \\
        --output PATH

    --input   a strip PNG  OR  a directory of cap_*.png files
    --x/y/w/h crop rectangle in NES pixels (default: full frame)
    --scale   NES-pixels-per-strip-pixel (default: 4)
    --output  output strip PNG (for strip input) or directory (for folder input)

Example:
    # Crop all images in ./captures to 102px wide starting at x=491,
    # retaining full height (use --scale 1 for raw pixel coordinates):
    python dev-toolbox/image/crop_region.py \\
        --input ./captures \\
        --x 491 --w 102 \\
        --scale 1 \\
        --output ./captures_cropped

Dependencies: Pillow  (pip install pillow)
"""

import argparse
import os
import sys

from PIL import Image


def main():
    parser = argparse.ArgumentParser(
        description='Crop a fixed NES-pixel region from each frame of a strip or folder.')
    parser.add_argument('--input', required=True, help='Strip PNG or directory of *.png.')
    parser.add_argument('--x', type=int, default=0, help='Left edge in NES pixels.')
    parser.add_argument('--y', type=int, default=0, help='Top edge in NES pixels.')
    parser.add_argument('--w', type=int, default=None, help='Width in NES pixels.')
    parser.add_argument('--h', type=int, default=None, help='Height in NES pixels.')
    parser.add_argument('--scale', type=int, default=4,
                        help='Strip pixels per NES pixel (default: 4).')
    parser.add_argument('--output', required=True,
                        help='Output strip PNG or directory.')
    args = parser.parse_args()

    scale = args.scale
    is_dir = os.path.isdir(args.input)
    is_file = os.path.isfile(args.input)

    if not is_dir and not is_file:
        sys.exit(f'ERROR: --input not found: {args.input}')

    if is_dir:
        frames = [
            Image.open(os.path.join(args.input, f))
            for f in sorted(os.listdir(args.input))
            if f.endswith('.png')
        ]
        if not frames:
            sys.exit(f'ERROR: no cap_*.png in {args.input}')
    else:
        # It's a strip — split into frames by assuming NES 256 width
        strip = Image.open(args.input)
        fw = 256 * scale
        fh = strip.height
        n = strip.width // fw
        frames = [strip.crop((i * fw, 0, (i + 1) * fw, fh)) for i in range(n)]

    sample = frames[0]
    x0_px = args.x * scale
    y0_px = args.y * scale
    w_px = (args.w * scale) if args.w is not None else (sample.width - x0_px)
    h_px = (args.h * scale) if args.h is not None else (sample.height - y0_px)
    box = (x0_px, y0_px, x0_px + w_px, y0_px + h_px)

    cropped = [f.crop(box) for f in frames]

    if args.output.endswith('.png'):
        # Stitch into a strip
        out_img = Image.new('RGB', (w_px * len(cropped), h_px))
        for i, c in enumerate(cropped):
            out_img.paste(c, (i * w_px, 0))
        os.makedirs(os.path.dirname(args.output) or '.', exist_ok=True)
        out_img.save(args.output)
        print(f'Wrote strip {args.output}  ({len(cropped)} frames, each {w_px}x{h_px}px)')
    else:
        os.makedirs(args.output, exist_ok=True)
        for i, c in enumerate(cropped):
            c.save(os.path.join(args.output, f'crop_{i:010d}.png'))
        print(f'Wrote {len(cropped)} crops to {args.output}')


if __name__ == '__main__':
    main()
