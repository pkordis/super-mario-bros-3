# Sprite Extraction — Knowledge Base

This document records everything an agent needs to extract sprites from the SMB3
disassembly CHR data and from gameplay captures, without repeating prior research.

---

## 1. Video capture → PNG frames

### Tool
`dev-toolbox/extract_frames.py` — extracts every frame of an AVI to numbered PNGs.

### Usage
```
# Full video
python dev-toolbox/extract_frames.py

# Time-windowed
python dev-toolbox/extract_frames.py --start MM:SS --end MM:SS
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
| `frame_0.png`      | $4F     | 14 ($4E)  | 15 ($4F)  | Coin angling away              | SPR_PAL3           |
| `frame_2.png`      | $4D     | 12 ($4C)  | 13 ($4D)  | Thin vertical sliver (edge-on) | SPR_PAL3           |
| `frame_3.png`| $4F     | 14 ($4E)  | 15 ($4F)  | Coin returning (frame_1 H-flipped) | SPR_PAL3 + H-FLIP |

Animation cycle: `0 → 1 → 2 → 3 → repeat`.
Frame 3 reuses the same two tiles as frame 1 with `SPR_HFLIP` set.
`frame_3.png` is a pre-flipped convenience export.

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
- The sprite is 8x16 and the digits are its lower 8 lines (the pattern bytes are
  odd, bottom-half tiles), so they show at `Scores_Y + 9` on screen. See
  `kb/level-completion.md`, SMB3-66, for the port (`ScoreRisePopMotion`).
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

### Loading frames: the path pattern

An `AnimationImageResource` field is injected with `@Value("classpath:/...")`, and `AnimationImageResourceConverter`
turns the path into frames. Both parts of the pattern are optional:

- `{n,m}` is frame numbers `n` to `m`, both included: `poof/frame_{0,3}.png` is 4 frames.
- `<a,b,c>` lists alternatives, usually folders, expanded first and in order: `spin_away/<mushroom,flower,star>/
  frame_{0,2}.png` is `mushroom/frame_0` to `frame_2`, then the Flower's, then the Star's, 9 frames in all. Several
  lists combine with the first varying slowest; an empty alternative is an error.

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

---

## 8. Animated **background** tiles (PPU $0800) — the shimmer cycle

Section 3 covers the *sprite* pattern table. Animated background tiles (coins, question
blocks, bricks, the P-Switch) work differently and are worth knowing before extracting
any tile that appears to shimmer.

### The mechanism

`PatTable_BankSel` is 6 bytes; each sets the VROM at one PPU window
(`smb3.asm:2021`). Index `[1]` is PPU **$0800** — commented "second half BG, typ
animated". One global cycle drives every animated BG tile
(`prg030.asm PRG030_8E4F`, "REGULAR LEVEL ANIMATIONS"):

```
LDA <Counter_1
AND #$18
LSR A / LSR A / LSR A     ; X = (Counter_1 & $18) >> 3  -> 0..3, changes every 8 ticks
TAX
LDA PT2_Anim,X
STA PatTable_BankSel+1
```

with `PT2_Anim: .byte $60, $62, $64, $66` (`prg030.asm:423`).

So: **4 phases × 8 ticks = a 32-tick loop**, shared and phase-locked across all animated
BG tiles. This is why the question block, brick and coin animators in this project all use
4 frames at 8 ticks per frame.

Overrides to be aware of:
- `Level_Tileset == 5` (pipe/plant infestation) uses `PlantInfest_PatTablePerACnt` instead.
- `Level_Tileset == 7` (Toad House) and `== 10` (Airship) have their own paths.
- While `Level_PSwitchCnt != 0` the bank is forced to `$3E` (`PRG030_8E24`) — all BG tile
  animation **freezes** for the P-Switch's duration.

### Resolving a BG pattern number to a chr file

The $0800 window is **2 KB = two 1 KB banks = 128 patterns ($80–$FF)**:

| Pattern range | chr file            | Local tile index |
|---------------|---------------------|------------------|
| `$80`–`$BF`   | `chr<page>`         | `pattern - $80`  |
| `$C0`–`$FF`   | `chr<page + 1>`     | `pattern - $C0`  |

where `page` is the `PT2_Anim` entry for the phase (`$60`/`$62`/`$64`/`$66` = 96/98/100/102).
Example: the P-Switch's `$E0` in phase 0 is `chr097` tile `$20`.

### Counting how many frames a tile *actually* has

Do not assume 4. Extract the tile's patterns from all four banks and compare bytes — two
phases can hold identical CHR:

- question block, brick, coin → 4 distinct frames, 25% each
- **P-Switch** → banks `$62` and `$66` are identical ⇒ 3 distinct frames, cycle
  **A-B-C-B** (middle frame 50%, outer two 25%)
- **pressed P-Switch** → identical in all four ⇒ static

When a frame repeats, ship the duplicate as a real asset file (e.g. `frame_3` = `frame_1`)
so a plain linear cycle reproduces the ROM timing with no animator changes. That mirrors
what the ROM itself does with duplicated CHR.

### Trap: two different metatile orderings

A 16×16 metatile's four 8×8 patterns are listed in **different orders** by different
tables. Getting this wrong yields scrambled art that looks like garbage:

| Table | Order |
|-------|-------|
| per-tileset `Tile_Layout_TS*` | column-major — TL, BL, TR, BR |
| `OneTile_ChangeToPatterns` (`prg029.asm:2553`) | **row-major** — TL, TR, BL, BR |

The proof for the row-major reading is entry `$09` (`CHNGTILE_PSWITCHSTOMP`) =
`$FF,$FF,$E6,$E7`: `$FF` is the blank pattern, so row-major gives a blank **top row** with
art on the bottom — a switch flattened to the floor. Column-major would blank the left
half, which is not a pressed switch.

### Deriving real colours for a BG tile

CHR gives only palette indices 0–3. To emit correctly coloured PNGs without hunting down
the palette, match against an existing correct asset: for each of the 4 indices, collect
the colours at those pixel positions in the known-good PNG. If the mapping is a consistent
bijection across all 256 pixels, it is an exact match and yields the palette for free.
This is how `sprites/object/block/switch/*` and `sprites/effect/poof/*` were produced
(index `0` → transparent, `1` → black, `2` → `#CFF1FF`, `3` → `#68BAFF`).

Note that index `0` is the NES **backdrop** colour, not necessarily "nothing" — in the
P-Switch's frame A the "P" glyph itself is drawn in index 0, so it renders as a hole
showing the background layer. Mapping index 0 to transparent reproduces that faithfully.

---

## 9. Status-bar background tiles — the HUD cards (SMB3-43)

The status bar is drawn with its own CHR banks. Partway down the screen the IRQ switches the BG pattern table to
`StatusBarCHR_0000 = $5C` (2 KB: `$5C`/`$5D`) and `StatusBarCHR_0800 = $5E` (2 KB: `$5E`/`$5F`)
(prg031.asm:1963-1964, 2079-2089). So a status-bar pattern resolves to:

| Pattern range | chr file  | Local tile index |
|---------------|-----------|------------------|
| `$00`–`$3F`   | `chr092`  | `pattern`        |
| `$40`–`$7F`   | `chr093`  | `pattern - $40`  |
| `$80`–`$BF`   | `chr094`  | `pattern - $80`  |
| `$C0`–`$FF`   | `chr095`  | `pattern - $C0`  |

The HUD glyphs (`hud/*.png`) and the status-bar cards come from these banks, not from the level's.

**Lining CHR up with `hud_base.png`.** `hud_base.png` is the 240 × 48 bar from nametable column 1, row 23: a tile at
column `c`, row `r` sits at `x = (c - 1) * 8`, `y = (r - 23) * 8`. The bar's tiles are listed in the `StatusBar`
macro (prg030.asm:117-162).

**Colours.** Match the macro's frame tiles against `hud_base.png` (the method of §8). For the card area it gives
index 1 → black `(0, 0, 0)`, index 2 → white `(255, 255, 255)`, index 3 → the bar's cyan `(181, 235, 242)`; index 0
is not used. `hud_base.png` has a little noise in its black and white (`(4, 4, 4)`, `(251, 251, 251)` and similar),
so take the dominant colour per index rather than requiring a perfect bijection.

**The cards** (`hud/card/{mushroom,flower,star}.png`, 16 × 16, opaque) are the 2 × 2 blocks of `CardUL`,
`CardUR` (top row) and `CardLL`, `CardLR` (bottom row), prg031.asm:2761-2764 — row-major, top left first. The
empty box is four `$FE` tiles, all index 3; `hud_base.png`'s three boxes match it pixel for pixel, which is a good
check that the alignment and colours are right.

**The level-end messages** (SMB3-49) use the same two banks: the level's second background window is switched to
`$5E` for state 5 of the end-level card. "COURSE CLEAR!", "YOU GOT A CARD" and the box's borders are `$80-$A8` in
chr094, the blank `$FC` and the box's inside `$FE` in chr095. They are drawn with background sub-palette 0, which
has the status bar's colours (`$0F $30 $3C`): index 1 black, 2 white, 3 cyan. None uses index 0, so in the game
the cells are opaque, the letters on black. This project's pictures make that black background transparent so the
level shows through: the font cells keep only the white, and `box.png` keeps its black inner outline but is
transparent outside its white border. The letters, "!" and the blank are the bold font, `font/bold/*.png`
(`BoldFontGlyph`), one cell each; the messages are written with it from their text. The box is one composed
picture, `sprites/object/card/message/box.png`. Fonts go under `src/main/resources/font/<font>/`, one folder per
font, named by their look. See `level-completion.md`.
