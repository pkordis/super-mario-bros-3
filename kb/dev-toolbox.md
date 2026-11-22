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
├── audio/          # Waveform analysis (lead-in, tail, clicks), lossless Ogg trimming
├── db/             # One-off database clean-ups (pymongo)
└── misc/           # Everything else
```

All tools are self-contained Python scripts. Each one accepts `--help` and documents
its own options. They have no shared dependencies beyond the standard library and
`Pillow` (plus `opencv-python` where noted, and `pymongo` for `db/`). Install once:

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

### audio/analyze_waveform.py

Analyses a 16-bit PCM WAV. Needs `numpy`. For an `.ogg`, first decode it to WAV
with jME's own decoder (load it with an `AudioKey` that is not a stream, then
write `AudioBuffer.getData()` behind a 44-byte WAV header). That way the
analysis sees exactly what the game plays.

```
python audio/analyze_waveform.py --wav PATH [--ogg PATH] [--envelope-ms N] [--range START_MS END_MS]
python audio/analyze_waveform.py --wav PATH --segments [--gap-ms N] [--silence-db DB]
```

| Option | Default | Notes |
|--------|---------|-------|
| `--wav` | required | 16-bit PCM WAV |
| `--ogg` | none | The source Ogg. Compares the decoded length with the Ogg's own sample count |
| `--envelope-ms` | 2 | Window of the printed peak envelopes |
| `--range` | none | Also print the envelope and every left-channel sample between two times |
| `--segments` | off | One line per file: each sounding stretch with its start, end, peak and attack |
| `--gap-ms` | 20 | Silence that separates two segments |
| `--silence-db` | -70 | Level below which a sample counts as silent |

The default report covers:
- where the signal first and last crosses -70 to -30 dBFS (the lead-in and the tail)
- the DC offset
- the first and last frames
- the largest sample-to-sample jump near the end, which is where a click would show
- peak envelopes of the start and of the end

`--segments` is how the recorded pop after the jump was found: it shows up as a
second, 13 ms segment.

---

### audio/trim_ogg_end.py

Shortens an Ogg Vorbis file at a given time **without re-encoding** (no ffmpeg or
Vorbis encoder is needed, only the standard library). The packets past the cut are
dropped, and the last page is marked as the end of the stream with its granule
position (the total sample count) set to the cut. The audio that stays is bit for
bit what it was. Used in SMB3-73 to cut the silence after the level completion
jingles.

```
python audio/trim_ogg_end.py --ogg PATH --report
python audio/trim_ogg_end.py --ogg IN.ogg --out OUT.ogg --end-ms N
```

| Option | Default | Notes |
|--------|---------|-------|
| `--ogg` | required | Ogg Vorbis file to read |
| `--out` | none | Where to write the shortened file. Without it, only the report is printed |
| `--end-ms` | none | Where the shortened file ends |
| `--report` | off | List the pages and their granules |

- Each packet's sample count comes from the two block sizes and the packet's mode
  bit, assuming libvorbis's usual two modes (0 short, 1 long). The tool checks this
  against every page's own granule and refuses to write when they disagree.
- jME's non-streamed load stops exactly at the cut. Its streaming load plays every
  packet kept, so it may run up to one block (about 23 ms) past the cut.
- Workflow: find the end of the sound with `analyze_waveform.py` (the "-70 dBFS
  last at" line), cut a little after it, decode the result with jME again, and
  check that the kept samples equal the original's and the dropped part is silent.

### db/clear_npc_enemy_tiles.py

Backs up the stored level scenes, then clears the enemy part tiles from their NPC layer: every NPC cell holding a
tile of type `ENEMY_PART` becomes 0, the empty cell; other NPC tiles are kept. Since SMB3-84 an enemy is a placement
in the NPC layer's `enemyPlacements` list, and part tiles no longer spawn anything, so run this once per database on
the SMB3-84 code, then place the enemies again in the editor.

```
pip install pymongo
python db/clear_npc_enemy_tiles.py                          # lists what it would clear, per scene
python db/clear_npc_enemy_tiles.py --title W01-L02-S01      # only that scene (repeatable)
python db/clear_npc_enemy_tiles.py --apply                  # writes the backup, then clears
python db/clear_npc_enemy_tiles.py --restore <backup.json>  # puts the backed-up scenes back
```

- The connection comes from the same properties files as the other `db/` tools (`collection.level-scenes` and
  `collection.tiles` for the collection names).
- `--apply` writes the whole documents of the scenes it will change to `~/smb3-db-backups/levelScenes-<time>.json`
  (MongoDB extended JSON, every field kept; `--backup-dir` changes the folder) before it changes anything, and prints
  the restore command.
- It only rewrites `layers.NON_PLAYABLE_CHARACTERS.tileIds`; the rest of each scene is untouched.
- Nitrite is not covered, as for the other `db/` tools.
### db/delete_virtual_tile_records.py

Deletes the stored Rendering Starter and Spawn Point tile records (type `RENDERING_STARTER` or `SPAWN_POINT`,
ids 1 and 2) from the Mongo `tiles` collection. SMB3-75 turned both into virtual objects and removed the tile
types, and a stored tile whose type no longer exists cannot be read back, so the editor does not start until
they are gone. Run it once per database, before starting the editor on the SMB3-75 code.

```
pip install pymongo
python db/delete_virtual_tile_records.py            # lists what it would delete
python db/delete_virtual_tile_records.py --delete   # deletes it
```

- The connection comes from `src/main/resources/application.properties`, overridden by the git-ignored
  `application-local.properties` (host, port, database, username, password, `collection.tiles`). The user is
  authenticated against the database itself, as `MongoDBConfig` does.
- It matches by type, not by id, and prints every record it finds first.
- Nitrite is not covered: there is no local `smb3.db` in use. A Nitrite store would need the same two records
  removed by hand.

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
