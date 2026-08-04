# Sprite Extraction — Knowledge Base

This document records everything an agent needs to extract sprites from the SMB3
disassembly CHR data and from gameplay captures, without repeating prior research.

---

## 1. Video capture → PNG frames

### Tool
`captures/extract_frames.py` — extracts every frame of an AVI to numbered PNGs.

### Usage
```
# Full video
python captures/extract_frames.py

# Time-windowed
python captures/extract_frames.py --start MM:SS --end MM:SS
```

### Conventions
- **Input**: exactly one `.avi` file in `captures/input/`. The script aborts if zero
  or more than one is present.
- **Output**: `captures/output/<capture_name>/cap_0000000000.png`, `cap_0000000001.png`, …
  The counter always starts at 0 regardless of `--start`.
- **Dependency**: `pip install opencv-python`
- Output frames are NES resolution (256×240) scaled up by the emulator capture.
  Each NES frame is ~16.67 ms at 60 fps; the AVI is captured at the same rate.

### Workflow for sprite research
1. Record gameplay with the emulator, save as `captures/input/<name>.avi`.
2. Run `extract_frames.py --start`/`--end` to isolate the relevant action.
3. Move the output folder from `captures/output/` to
   `captures/output/<descriptive_name>/` so it is easy to find later.
4. Load representative frames into the agent image tool to visually confirm
   which animation frames exist and in what order.

---

## 2. CHR data — where sprites live

### Location
All CHR banks are PCX files in `~/Projects/smb3dasm/CHR/`:
`chr000.pcx` … `chr127.pcx`. Each PCX is **1 KB = 64 tiles** laid out in a
128×32 pixel grid (16 columns × 4 rows of 8×8 tiles).

### Reading a tile from a PCX
```python
from PIL import Image

def get_tile(img, local_index):
    col = local_index % 16
    row = local_index // 16
    x0, y0 = col * 8, row * 8
    return [[img.getpixel((x0 + x, y0 + y)) for x in range(8)]
            for y in range(8)]

img = Image.open('chr004.pcx')   # palette-mode image
pixels = get_tile(img, 9)        # 8×8 list-of-lists of palette indices 0-3
```

Palette indices: `0` = transparent, `1`/`2`/`3` = colour shades (white/grey/dark
in-game depending on the active palette). For extraction purposes colour is
irrelevant — only index `0` (transparent) vs non-zero (opaque) matters for
shape fidelity.

---

## 3. MMC3 CHR bank mapping during normal gameplay

The NES PPU has two 4 KB pattern tables. SMB3 uses **8×16 sprites** throughout
gameplay (PPU_CTL1 bit 5 set). The PPU reads sprites from pattern table 2
(PPU $1000–$1FFF), which is split into four 1 KB windows:

| BankSel index | PPU range     | Pattern IDs | Set by                              |
|---------------|---------------|-------------|-------------------------------------|
| [2]           | $1000–$13FF   | $00–$3F     | `Player_Draw` (prg029.asm) per frame — varies by player suit/frame |
| [3]           | $1400–$17FF   | $40–$7F     | `Player_DoGameplay` (prg008.asm) per frame — **chr004** for normal levels, chr060 for Giant World |
| [4]           | $1800–$1BFF   | $80–$BF     | Object-specific overrides only (e.g. laser = chr018) |
| [5]           | $1C00–$1FFF   | $C0–$FF     | Suit-specific override (hammer suit = chr079); otherwise rarely changed |

**Key rule**: pattern ID `P` lives in the bank at `BankSel[2 + P/64]`, at local
tile index `P mod 64` (i.e. `P & 0x3F`).

For the **$40–$7F range** (most gameplay object sprites): `local = patternID - 0x40`,
source = **chr004** in normal levels.

### 8×16 tile pairing
In 8×16 mode the PPU ignores bit 0 of the pattern ID. Pattern `$49` renders
tiles `$48` (top) and `$49` (bottom). However, **coin and score objects render
in 8×8 mode** (single sprite entry, one tile) — the draw code writes only one
sprite RAM entry. The tile used is the exact pattern ID as written
(odd IDs are fine in 8×8 mode).

---

## 4. Confirmed sprite extractions

All sprites extracted from **chr004.pcx**, BankSel[3] = 4.
All output saved to `src/main/resources/sprites/object/`.

### 4a. Spinning coin (`object/coin/`)

Source: `CoinPUp_Patterns` table, `prg007.asm` line 2767.
Draw routine: `CoinPUp_UpdateAndDraw` (prg007.asm). **8×16 sprite (two 8×8 tiles
stacked: even pattern = top tile, odd pattern = bottom tile).**

| File               | Pattern | Top local | Bot local | Shape                          | Attribute          |
|--------------------|---------|-----------|-----------|--------------------------------|--------------------|
| `frame_0.png`      | $49     | 8 ($48)   | 9 ($49)   | Full front-facing coin         | SPR_PAL3           |
| `frame_1.png`      | $4F     | 14 ($4E)  | 15 ($4F)  | Coin angling away              | SPR_PAL3           |
| `frame_2.png`      | $4D     | 12 ($4C)  | 13 ($4D)  | Thin vertical sliver (edge-on) | SPR_PAL3           |
| `frame_3_hflip.png`| $4F     | 14 ($4E)  | 15 ($4F)  | Coin returning (frame_1 H-flipped) | SPR_PAL3 + H-FLIP |

Animation cycle: `0 → 1 → 2 → 3 → repeat`.
Frame 3 reuses the same two tiles as frame 1 with `SPR_HFLIP` set.
`frame_3_hflip.png` is a pre-flipped convenience export.

**NES palette used (SPR_PAL3, plains level):**
- Index 0 → transparent
- Index 1 → black `(0, 0, 0)` — outline
- Index 2 → orange/gold `(252, 160, 68)` — NES colour $27
- Index 3 → white `(252, 252, 252)` — NES colour $30 — highlight

### 4b. Score popup font (`object/score/`)

Source: `Score_PatternLeft` / `Score_PatternRight` tables, `prg007.asm` lines
2121–2122. Draw routine: `Score_GiveAndDraw` (prg007.asm).

**Not a standard digit font.** Each tile encodes a compressed multi-digit glyph.
Each popup renders at most 2 tiles side-by-side (left + right), giving 8×8 or
16×8 total size.

#### Individual tiles (8×8 each)

| File          | Pattern | Local | Glyph role                              |
|---------------|---------|-------|-----------------------------------------|
| `tile_5B.png` | $5B     | 27    | "1" — left digit for 100/1000; lone "10" right tile |
| `tile_59.png` | $59     | 25    | "000" — right half for 1000/2000/4000/8000 |
| `tile_61.png` | $61     | 33    | 1-UP left half                          |
| `tile_63.png` | $63     | 35    | "2" — left for 200/2000; lone "20"      |
| `tile_69.png` | $69     | 41    | "00" — right half for 100/200/400/800   |
| `tile_6B.png` | $6B     | 43    | "4" — left for 400/4000; lone "40"      |
| `tile_6D.png` | $6D     | 45    | "8" — left for 800/8000; lone "80"      |
| `tile_6F.png` | $6F     | 47    | 1-UP right half                         |

**NES palette used (PAL1, score text):**
- Index 0 → transparent
- Index 1 → black `(0, 0, 0)`
- Index 2 → mid-grey `(80, 80, 80)`
- Index 3 → white `(252, 252, 252)`

#### Composite score sprites (16×8, left+right pre-merged)

| File              | Left tile | Right tile | Value  |
|-------------------|-----------|------------|--------|
| `score_010.png`   | (blank)   | $5B        | 10 pt  |
| `score_020.png`   | (blank)   | $63        | 20 pt  |
| `score_040.png`   | (blank)   | $6B        | 40 pt  |
| `score_080.png`   | (blank)   | $6D        | 80 pt  |
| `score_100.png`   | $5B       | $69        | 100 pt |
| `score_200.png`   | $63       | $69        | 200 pt |
| `score_400.png`   | $6B       | $69        | 400 pt |
| `score_800.png`   | $6D       | $69        | 800 pt |
| `score_1000.png`  | $5B       | $59        | 1000 pt|
| `score_2000.png`  | $63       | $59        | 2000 pt|
| `score_4000.png`  | $6B       | $59        | 4000 pt|
| `score_8000.png`  | $6D       | $59        | 8000 pt|
| `score_1up.png`   | $61       | $6F        | 1-UP   |

The score popup for a coin from a ? block is **100 pt** → `score_100.png`.
The score value stored is `$85` (prg007.asm line ~2832); `$85 & $7F = $05` =
index 5 in the score table = 100 pts.

---

## 5. Score draw mechanics (prg007.asm)

- Up to 5 simultaneous score popups (`Scores_Value[0..4]`).
- Each popup lives for `$30` (48) ticks, rising upward at a rate controlled by
  `Score_RiseCounterMask`.
- The score is added to `Score_Earned` at tick `$2A` (midpoint), not immediately.
- Counter clears to 0 when done; slot is reused.
- If no sprite RAM slot is free for one half of the 2-tile display, that half
  is dropped for that frame (alternates each tick via `Counter_1`).

---

## 6. Other already-extracted sprites (index)

| Sprite              | Resource path                                         | Source CHR / notes                    |
|---------------------|-------------------------------------------------------|---------------------------------------|
| Question block      | `sprites/object/block/question/frame_0..3.png`        | 4-frame animation                     |
| Empty block         | `sprites/object/block/empty/frame_0.png`              | Single frame                          |
| Plain brick         | `sprites/object/brick/plain/frame_0..3.png`           | 4 frames + fragment                   |
| Brick poof          | `sprites/object/brick/poof_1..4.png`                  | 4-frame destruction anim              |
| Small Mario (level) | `sprites/player/mario/level/shrunk/`                  | Multiple frames                       |

---

## 7. Extraction rules and pitfalls

1. **Always use chr004 for patterns $40–$7F during normal gameplay.** Agents
   previously (incorrectly) tried chr078/chr079 — those are used in other
   contexts (Hammer suit, Laser object, N-Spade game) but not for standard
   object sprites during a normal level.

2. **Do not use the world-map banks (chr032–chr035)** for gameplay sprites.
   BankSel[2..5] = $20..$23 only during world map; they are overridden every
   frame once gameplay starts.

3. **chr004 local tile index = patternID - 0x40.** Valid range: local 0–63
   for patterns $40–$7F.

4. **The PCX palette is indexed (mode P).** `img.getpixel(x, y)` returns an
   integer 0–3, not an RGB tuple. The PCX stores only 4 greyscale shades:
   index 0 = black, 1 = dark grey, 2 = light grey, 3 = white. These are NOT
   the correct colours — they are just placeholder shades in the CHR dump.

5. **Always apply a real NES palette when extracting.** The PCX has no colour.
   You must map the 4 indices to actual RGBA values from the NES palette that
   the game would use for this sprite at runtime. "Colour is not important"
   means the exact shade/hue doesn't have to be pixel-perfect, but the output
   must be full-colour RGBA — **not monochrome greyscale**. A greyscale or
   all-white extract is wrong. Index 0 must always be transparent (alpha 0).
   Use the dasm or prg007/prg029 to determine which palette slot (PAL0–3) the
   sprite uses, then pick sensible RGB values for that context.

6. **SMB3 runs in 8×16 sprite mode throughout gameplay** (PPU_CTL1 bit 5 set).
   Even-pattern IDs address the top 8×8 tile; the hardware automatically fetches
   the next (odd) tile as the bottom half. A sprite described by pattern $49 is
   always 8×16: top = tile $48 (local 8), bottom = tile $49 (local 9). Never
   extract just the bottom tile — that gives half the sprite. Always pair
   `(patternID & 0xFE)` as top and `(patternID | 0x01)` as bottom.
   Exception: the game sometimes writes 8×8 sprites explicitly by manipulating
   OAM differently, but this is rare — check the draw code first.

7. **Never save working/scratch files to the dasm project** (`~/Projects/smb3dasm`).
   All final sprites belong in `src/main/resources/sprites/` in this project.
   The dasm project is read-only reference material.

10. **Path resolution from dasm scripts: always go up two levels, not one.**
    Extraction scripts that live in `~/Projects/smb3dasm/CHR/` must resolve the
    project root as:
    ```python
    CHR_DIR      = os.path.dirname(os.path.abspath(__file__))  # .../smb3dasm/CHR
    PROJECTS_DIR = os.path.dirname(os.path.dirname(CHR_DIR))   # .../Projects
    PROJECT_ROOT = os.path.join(PROJECTS_DIR, 'super-mario-bros-3')
    ```
    Using `os.path.join(CHR_DIR, '..', 'super-mario-bros-3')` is wrong — it
    resolves to `smb3dasm/super-mario-bros-3` which does not exist. The symptom
    is that `os.makedirs` silently creates the wrong directory and the files
    appear to save but the project resources are never updated.

11. **Running Python scripts from dasm on Windows: use a .ps1 trampoline.**
    The `execute_cmd` tool is unreliable with quoted paths containing hyphens or
    spaces when invoking Python directly. The only reliably working pattern is:
    - Write a `.ps1` file that does `Set-Location <dir>` then `python <script>.py`
    - Invoke it as `powershell -File C:/path/to/script.ps1` using **forward slashes**
    - Store the `.ps1` in a path that does NOT contain `super-mario-bros-3` in the
      filename itself — put it under `~/Projects/smb3dasm/` instead
    - Each new invocation needs a **new filename** — reusing a previously-run
      ps1 filename sometimes causes the tool to reject it

8. **Verify against captures before committing.** Load a few frames from
   `captures/output/<capture_name>/` as images and confirm the extracted tile
   matches the on-screen pixel shape.

9. **Score popup index is 1-based** in the `Score_PatternLeft/Right` tables
   (index 0 is unused/empty). A stored value of `$05` → index 5 → 100 pts.
   A stored value with bit 7 set (`$85`) is the same score with the bit used
   as a flag elsewhere; mask with `$7F` before indexing.
