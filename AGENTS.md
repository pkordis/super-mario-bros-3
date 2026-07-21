# Developer / Agent Notes — Super Mario Bros 3

This file captures architectural decisions and conventions that are not obvious from
the code alone. Read it before making structural changes to the project.

---

## Reference Disassembly — `~/Projects/smb3dasm` aka "dasm" (MANDATORY)

For **all** new features, debugging, and improvements to existing features, agents
**must** consult the `~/Projects/smb3dasm` project (referred to as **dasm** throughout
this document and in conversations). This is the fully annotated disassembly of the
original NES Super Mario Bros 3 ROM and contains properly named code, data structures,
constants, and game logic for tiles, sprites, MIDI/sound, physics, and overall game
behavior.

### How to use the disassembly

1. **Identify the relevant PRG bank(s)** for the feature you are working on (see
   the index below).
2. **Read the original assembly** to understand the exact behavior, constants, and
   data tables that govern the feature.
3. **Consult `~/Projects/smb3dasm/index.html`** — this is a JavaScript port of the
   player physics engine featuring small/large Mario, acceleration, P-meter, raccoon
   flight, and tail-wag mechanics. Use it as a reference for how the assembly logic
   translates into a higher-level imperative style.
4. **Translate into the patterns used by this project** — prefer OOP or functional
   style consistent with what already exists in `super-mario-bros-3`. Always match
   the project's existing architecture over inventing new patterns.

### Quick index — PRG banks and their contents

| Bank(s)   | File(s)           | Content                                                  |
|-----------|-------------------|----------------------------------------------------------|
| 0         | prg000.asm        | Object support routines, Player collision, shared logic   |
| 1–5       | prg001–prg005.asm | Object handlers by ID range (init/normal/collide/kill)   |
| 6         | prg006.asm        | Object placement/layout data for levels                  |
| 7         | prg007.asm        | Special objects, cannon fire, misc routines               |
| 8         | prg008.asm        | **Player control** (movement, jumping, P-meter, flight)  |
| 9         | prg009.asm        | 2P Vs mode, auto-scroll logic                            |
| 10–12     | prg010–prg012.asm | World map BG, logic, sprites, level links, tilesets      |
| 13–23     | prg013–prg023.asm | Tileset renderers (one per tileset/group)                |
| 24–25     | prg024–prg025.asm | Title screen, ending, cinematics, large image buffers    |
| 26        | prg026.asm        | Status bar, inventory, level junctions, fade routines    |
| 27        | prg027.asm        | Palettes, palette routines, King cinematic               |
| 28–29     | prg028–prg029.asm | Sound engine, music segments, Player draw/animation      |
| 30        | prg030.asm        | Always-resident: interrupt handling, Video_Upd_Table     |
| 31        | prg031.asm        | Always-resident: core utilities, NMI/IRQ, sound driver   |

### Key constants & data locations (smb3.asm)

- **Player suits**: `PLAYERSUIT_SMALL` (0) through `PLAYERSUIT_HAMMER` (6)
- **Player velocity constants**: `PLAYER_TOPWALKSPEED=$18`, `PLAYER_TOPRUNSPEED=$28`,
  `PLAYER_TOPPOWERSPEED=$38`, `PLAYER_MAXSPEED=$40`, `PLAYER_JUMP=-$38`
- **Pad input bits**: `PAD_A=$80`, `PAD_B=$40`, `PAD_UP=$08`, `PAD_DOWN=$04`,
  `PAD_LEFT=$02`, `PAD_RIGHT=$01`
- **Object IDs**: Fully enumerated (e.g., `OBJ_GOOMBA=$72`, `OBJ_BOSS_BOWSER=$18`)
- **Tile IDs**: Per-tileset constants (e.g., `TILE1_SKY=$80`, `TILEA_COIN=$40`)
- **Sound/Music queues**: `Sound_QPlayer`, `Sound_QLevel1`, `Sound_QMusic1/2`
- **Level header flags**: `LEVEL1_SIZE_*`, `LEVEL1_YSTART_*`, `LEVEL2_BGPAL_*`, etc.

### Key variables for player physics

- `Player_X`, `Player_Y`, `Player_XVel`, `Player_YVel` — position & velocity
- `Player_XVelFrac`, `Player_YVelFrac` — fractional accumulators (4.4 FP)
- `Player_InAir` — nonzero when airborne
- `Player_Power` — P-meter charge level (0–$7F)
- `Player_FlyTime` — remaining flight frames (raccoon/tanooki)
- `Player_WagCount` — tail-wag slow-fall countdown
- `Player_Suit` — active powerup
- `Player_RunFlag` — set when grounded + B held + speed ≥ run threshold

### index.html JS port — key physics constants

```javascript
PLAYER_TOPWALKSPEED = 1.5    // $18/16 px/frame
PLAYER_TOPRUNSPEED  = 2.5    // $28/16
PLAYER_TOPPOWERSPEED = 3.5   // $38/16
PLAYER_FLY_YVEL = -1.5       // rise velocity while flying
PLAYER_TAILWAG_YVEL = 1.0    // descent cap during tail float
PMETER_LEVELS = 7            // full meter
PMETER_CHARGE_FRAMES = 8     // frames per charge step
PMETER_DRAIN_FRAMES = 24     // frames per drain step
FLY_TIME = 0x80              // total flight frames on launch
GRAVITY_SLOW = 1/16          // holding A
GRAVITY_FAST = 5/16          // not holding A
JUMP_FORCE = [-3.5, -3.625, -3.75, -4]  // indexed by speed tier
```

### Level data structure

- **Levels directory**: `PRG/levels/` — organized by tileset theme (Plains, Hills,
  Desert, Fortress, Giant, Ice, Sky, Under, Water, Airship, etc.)
- **Objects directory**: `PRG/objects/` — object placement data per level
- **Maps directory**: `PRG/maps/` — world map layouts (World1–9, suffixed with
  L=Layout, O=Objects, S=Sprites, OH/OI/OX/OY=sub-sections)

---

## Code style — `.editorconfig` (MANDATORY)

A `.editorconfig` file at the repository root defines the **canonical style** for
this project. Every agent **must** comply with it at all times:

* **Indentation** — 4 spaces (no tabs), continuation indent 8 spaces.
* **Imports** — grouped as: non-java/javax first → blank line → `javax.*` →
  `java.*` → blank line → static imports; alphabetical within each group.
* **`final` everywhere** — all method parameters and all local variables that are
  not reassigned must be declared `final`.
* **No alignment spacing** — never use more than one space between tokens to
  vertically align field declarations, variable declarations, or assignment
  operators. Use exactly one space on each side of `=`.
* **Line length** — hard limit of 120 characters.
* **Trailing whitespace** — none (editor enforces on save).
* **File endings** — LF line endings, one trailing newline, no extra blank lines
  after the closing `}` of the top-scene type.
* **Braces** — K&R style (`{` on the same line, never on a new line).
* **Prefer `Math.clamp` over `Math.min`/`Math.max` combos** — never combine
  `Math.min` and `Math.max` (or their static imports) in the same expression to
  bound a value. Always use `Math.clamp(value, min, max)` (or the static import
  `clamp`) instead. This applies to `int`, `long`, `float`, and `double` overloads.

Before committing any Java file, verify that it conforms to every rule above.
When in doubt, consult `.editorconfig` for the authoritative settings.

---
