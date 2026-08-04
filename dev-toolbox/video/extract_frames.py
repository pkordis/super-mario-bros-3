"""
extract_frames.py — split an AVI (or any OpenCV-supported video) into numbered PNG frames.

Usage:
    python video/extract_frames.py [--input PATH] [--output DIR] [--start MM:SS] [--end MM:SS]

Defaults:
    --input   the single .avi found in <project>/captures/input/  (aborts if 0 or >1)
    --output  <project>/captures/output/<video_stem>/
    --start   beginning of file
    --end     end of file

Output frames are named cap_0000000000.png, cap_0000000001.png, ...
The counter always starts at 0 regardless of --start.

Dependencies: opencv-python  (pip install opencv-python)
"""

import argparse
import glob
import os
import sys

import cv2


def parse_timecode(tc):
    """Parse MM:SS or MM:SS.mmm into total seconds (float)."""
    parts = tc.split(':')
    if len(parts) == 2:
        minutes, seconds = parts
        return int(minutes) * 60 + float(seconds)
    raise ValueError(f'Invalid timecode: {tc!r}  (expected MM:SS)')


def resolve_input(explicit):
    if explicit:
        return explicit
    toolbox_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(toolbox_dir))
    pattern = os.path.join(project_root, 'captures', 'input', '*.avi')
    matches = glob.glob(pattern)
    if len(matches) == 0:
        sys.exit(f'ERROR: no .avi found in {os.path.dirname(pattern)}')
    if len(matches) > 1:
        sys.exit(f'ERROR: multiple .avi files found — specify --input explicitly:\n  ' +
                 '\n  '.join(matches))
    return matches[0]


def resolve_output(explicit, input_path):
    if explicit:
        return explicit
    toolbox_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(toolbox_dir))
    stem = os.path.splitext(os.path.basename(input_path))[0]
    return os.path.join(project_root, 'captures', 'output', stem)


def main():
    parser = argparse.ArgumentParser(description='Split a video into numbered PNG frames.')
    parser.add_argument('--input', help='Path to input video file.')
    parser.add_argument('--output', help='Output directory for frames.')
    parser.add_argument('--start', help='Start time MM:SS (default: beginning).')
    parser.add_argument('--end', help='End time MM:SS (default: end).')
    args = parser.parse_args()

    input_path = resolve_input(args.input)
    output_dir = resolve_output(args.output, input_path)
    os.makedirs(output_dir, exist_ok=True)

    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        sys.exit(f'ERROR: could not open {input_path}')

    fps = cap.get(cv2.CAP_PROP_FPS) or 60.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    start_sec = parse_timecode(args.start) if args.start else 0.0
    end_sec = parse_timecode(args.end) if args.end else total_frames / fps

    start_frame = int(start_sec * fps)
    end_frame = min(int(end_sec * fps), total_frames - 1)

    print(f'Input : {input_path}')
    print(f'Output: {output_dir}')
    print(f'Frames: {start_frame} – {end_frame}  ({end_frame - start_frame + 1} frames at {fps:.2f} fps)')

    cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
    counter = 0
    for frame_no in range(start_frame, end_frame + 1):
        ok, frame = cap.read()
        if not ok:
            print(f'WARNING: could not read frame {frame_no}, stopping.')
            break
        name = f'cap_{counter:010d}.png'
        cv2.imwrite(os.path.join(output_dir, name), frame)
        counter += 1

    cap.release()
    print(f'Done. {counter} frames written to {output_dir}')


if __name__ == '__main__':
    main()
