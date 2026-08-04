# Dev-Toolbox — Knowledge Base

**Before writing a new script for any analysis or automation task, read this file
and check `dev-toolbox/` first.** A ready-made tool may already exist. If it does
not, build it in that directory so it is reusable by the next agent.

---

## Directory layout

```
dev-toolbox/
├── video/          # AVI / video file utilities
├── image/          # PNG / strip / frame-sequence utilities
├── animation/      # Sprite trajectory & offset extraction
└── misc/           # Everything else
```

All tools are self-contained Python scripts. Each one accepts `--help` and documents
its own options. They have no shared dependencies beyond the standard library and
`Pillow` (plus `opencv-python` where noted). Install once:

```
pip install pillow opencv-python
```

---

## Running tools — MANDATORY conventions on Windows

**Always use the PowerShell trampoline pattern.** The `execute_cmd` tool is
unreliable when Python is invoked directly with quoted paths that contain
hyphens. The safe pattern is:

1. Write a short `.ps1` trampoline to the `dev-toolbox/` directory:

```powershell
Set-Location "C:/Users/pkordis/Projects/super-mario-bros-3/dev-toolbox"
python video/extract_frames.py --start 00:10 --end 00:20
```

2. Run it with forward slashes and `-File`:

```
powershell -File C:/Users/pkordis/Projects/super-mario-bros-3/dev-toolbox/run_tool.ps1
```

3. Use a new trampoline filename for each invocation — reusing a previously-run
   filename can cause the tool execution to be silently skipped.

---

## Tool catalogue

### video/extract_frames.py

Splits any AVI (or other OpenCV-supported video) into sequentially numbered PNG
frames. The primary input for all animation capture work.

```
python video/extract_frames.py [--input PATH] [--output DIR] [--start MM:SS] [--end MM:SS]
```

| Option | Default | Notes |
|--------|---------|-------|
| `--input` | only `.avi` in `captures/input/` | Aborts if 0 or >1 found |
| `--output` | `captures/output/<stem>/` | Numbered `cap_0000000000.png`, `cap_0000000001.png`, … |
| `--start` | beginning of file | Time-code window start |
| `--end` | end of file | Time-code window end |

Output counter always starts at 0 regardless of `--start`. Requires `opencv-python`.

Typical workflow:

1. Copy the emulator recording to `captures/input/<name>.avi`.
2. Run with `--start`/`--end` to isolate the relevant action.
3. Rename the output folder to something descriptive, e.g.
   `captures/output/single_coin_reward_q_block_bounce/`.
4. Use the frames with the image tool or feed them to an analysis script.

---

### image/stitch_strip.py

Takes a folder of per-frame PNG captures and stitches them side-by-side into a
single horizontal strip PNG. The strip is the canonical input for trajectory
and colour analysis scripts.

```
python image/stitch_strip.py --input DIR [--output PATH] [--scale N] [--frames A B]
```

| Option | Default | Notes |
|--------|---------|-------|
| `--input` | required | Directory of `cap_XXXXXXXXXX.png` files |
| `--output` | `../captures/our_impl.png` | Output strip path |
| `--scale` | 1 | Downsample factor (e.g. `4` → NES native resolution) |
| `--frames` | all | Inclusive range, e.g. `--frames 209 286` |

The strip is `N_frames × frame_width` pixels wide and `frame_height` pixels tall.
Frame 0 of the strip = first selected frame. Frame index in the strip is therefore
relative to the selection, not to the original video.

---

### animation/measure_trajectory.py

Reads a strip PNG and, for each strip frame, finds the topmost pixel of a target
colour within an optional X window. Reports per-frame Y coordinates and offsets
relative to a reference row. Produces Java array literals ready to paste into code.

```
python animation/measure_trajectory.py \
    --strip PATH \
    --color R G B [--tolerance N] \
    --reference-row Y \
    [--x-center X --x-radius R] \
    [--scale N] \
    [--output-java]
```

| Option | Default | Notes |
|--------|---------|-------|
| `--strip` | required | Strip PNG path |
| `--color` | required | Target RGB (e.g. `234 158 34` for coin orange) |
| `--tolerance` | 15 | Per-channel tolerance for colour matching |
| `--reference-row` | required | NES Y of the reference object edge (block top etc.) |
| `--x-center` | full width | NES X of the sprite's expected horizontal centre |
| `--x-radius` | full width | Search window half-width in NES pixels |
| `--scale` | 4 | Pixel scale factor (strip pixels per NES pixel) |
| `--output-java` | off | Print result as a Java `int[]` literal |

Output columns: `frame | top_y_nes | offset_from_reference`.
Positive offset = sprite is above the reference row.

**Key caveat — occlusion by other sprites**: if a foreground sprite (bouncing
block, score popup) shares the same colour or overlaps the target, the measured
Y will be wrong for those frames. Always cross-check suspicious values visually
against the individual cap frames in `captures/output/` before trusting the table.
Occlusion is the most common cause of anomalous dips in a trajectory table.

---

### animation/measure_multi_object.py

Like `measure_trajectory.py` but tracks several independently-coloured objects
across the same strip simultaneously. Reports a separate column per object. Useful
when a bouncing block, a coin, and a score popup all appear in the same capture.

```
python animation/measure_multi_object.py \
    --strip PATH \
    --objects "coin:234,158,34" "score:255,254,255" \
    --reference-row Y \
    [--scale N] \
    [--output-java]
```

Each `--objects` entry is `label:R,G,B`. Separate X windows can be appended:
`"coin:234,158,34:xcenter=128:xradius=20"`.

---

### image/compare_frames.py

Diffs two PNG frames or two full strip files pixel-by-pixel and highlights
changed regions. Useful for confirming that a code change only affects the
intended pixels.

```
python image/compare_frames.py --a PATH --b PATH [--output PATH]
```

Writes a diff image where unchanged pixels are dimmed and changed pixels are
highlighted in red. Prints a summary of changed pixel count and bounding box.

---

### image/crop_region.py

Crops a fixed rectangular region out of every frame in a strip or a folder of
PNGs. Use this to isolate a single sprite or tile before colour analysis.

```
python image/crop_region.py \
    --input PATH \
    [--x X --y Y --w W --h H] \
    [--scale N] \
    --output PATH
```

Coordinates are in NES pixels; `--scale` converts to strip pixels.

---

### misc/dump_colors.py

Prints all distinct RGBA colours in a PNG (or in a region of it), sorted by
frequency. Essential first step when you don't know the exact RGB values of a
target sprite.

```
python misc/dump_colors.py --input PATH [--x X --y Y --w W --h H] [--top N]
```

---

## How to add a new tool

1. Place the script in the appropriate subdirectory (`video/`, `image/`,
   `animation/`, or `misc/`). Create a new subdirectory if needed.
2. Add `--help` support (use `argparse`).
3. Follow the path conventions below.
4. Add a section to this file under the **Tool catalogue** heading.

### Path conventions for scripts

All scripts resolve project paths relative to their own location. The
`dev-toolbox/` directory is one level below the project root:

```python
import os

TOOLBOX_DIR  = os.path.dirname(os.path.abspath(__file__))      # dev-toolbox/<sub>
TOOLBOX_ROOT = os.path.dirname(TOOLBOX_DIR)                    # dev-toolbox/
PROJECT_ROOT = os.path.dirname(TOOLBOX_ROOT)                   # super-mario-bros-3/
CAPTURES_DIR = os.path.join(PROJECT_ROOT, 'captures')
RESOURCES_DIR = os.path.join(PROJECT_ROOT, 'src', 'main', 'resources')
```

Never hardcode absolute paths. Never write output files into the dasm project
(`~/Projects/smb3dasm`); that project is read-only reference material.

---

## Worked example — coin pop trajectory

This is exactly the kind of task these tools exist for. The full workflow:

```
# 1. Extract frames from the AVI around the block-hit event
python video/extract_frames.py --start 00:03 --end 00:06

# 2. Rename the output folder
# captures/output/single_coin_reward_q_block_bounce/

# 3. Stitch only the relevant frames into a strip (scale 4 → NES pixels)
python image/stitch_strip.py \
    --input captures/output/single_coin_reward_q_block_bounce \
    --output captures/strip.png \
    --scale 4 \
    --frames 209 286

# 4. Find the exact coin colour first
python misc/dump_colors.py --input captures/strip.png --top 20

# 5. Measure the coin's Y trajectory, reference = block top row
python animation/measure_trajectory.py \
    --strip captures/strip.png \
    --color 234 158 34 \
    --tolerance 15 \
    --reference-row 112 \
    --x-center 128 --x-radius 20 \
    --scale 4 \
    --output-java
```

The `--output-java` flag prints the offsets ready to paste into `Y_OFFSETS`.

**When offset values look wrong for a cluster of frames** (sudden dip, then
recovery — like frames 2–5 in the coin pop), the cause is almost always
occlusion: another sprite (the bouncing block top, a score popup) is in front
of the coin and the detected topmost pixel belongs to that sprite instead.
Verify by opening the corresponding individual cap frames from
`captures/output/<name>/` and inspecting visually. Use `image/crop_region.py`
to isolate the exact pixel region if needed.

---

## Relationship to other KB documents

- `kb/sprites_extraction.md` — CHR data extraction, palette mapping, tile
  pairing rules. Read this when you need to extract sprite artwork rather than
  measure animation data.
- `AGENTS.md` — overall project conventions, dasm index, code style rules.
  Always read before making structural changes.
