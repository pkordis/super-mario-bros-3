# P-Switch (switch block) — Knowledge Base

Everything needed to continue the P-Switch feature set without re-deriving it from the
disassembly. All dasm references are `~/Projects/smb3dasm`.

---

## 1. Feature list and status

| # | Feature | Status |
|---|---------|--------|
| 1 | Switch block spawner | **done**, committed |
| 2 | `SwitchBlock` object + 4-frame animation | **done**, committed |
| 3 | Press behaviour (collision box gone, static pressed art) | **done** as scoped, committed |
| 4 | Screen shake | **done** |
| 5 | Brick↔coin substitution for the duration | **done** as scoped |
| 6 | Music swap and restore on expiry | not started |
| 7 | Spawn poof cloud | **done** |

Sound is **out of scope for the whole feature set** for now (there is no sound engine yet), so the
stomp's `SND_LEVELBABOOM` "Wham!" is not tracked as work. Item 6 stays deferred.

---

## 2. dasm evidence

### 2.1 Spawner — `prg008.asm LATP_PSwitch` (:5320)

A P-Switch is dispatched as a block's **contents**, through the same `LATP_JumpTable`
as Coin/Leaf/1up (`prg008.asm:5148`, entry `$A`). Key facts:

- The switch tile is written **one row above** the hit tile
  (`LDA <Temp_Var14 / AND #$F0 / SUB #16`), unconditionally overwriting whatever is there.
- `LDY #$01` before `RTS` indexes `Bouncer_PUp` at "nothing" ⇒ **no item is launched**;
  the switch is placed as a tile.
- `Player_Suit` is never read ⇒ size-independent (small Mario gets a switch, not a
  substituted item).
- A `SOBJ_POOF` is claimed at `PRG008_B8C9` with `SpecialObj_Data = $20`, and
  `SpecialObj_YLo/YHi/XLo/XHi` receive the **same** coordinates as `Level_BlockChg*`
  ⇒ the poof covers the switch's own cell.

### 2.2 Press — `prg008.asm PRG008_B623` (~:4750)

```
CMP #TILEA_PSWITCH
BNE PRG008_B64F
CPX #$02
BGS PRG008_B64F              ; reject head-probe detections -> feet/body only
LDA #CHNGTILE_PSWITCHSTOMP
CMP Level_ChgTileEvent / BEQ PRG008_B64F   ; already queued, skip
JSR Level_QueueChangeBlock
LDA #$10 / STA Level_Vibration             ; screen shake, 16 frames   (feature 4)
LDA Sound_QLevel1 / ORA #SND_LEVELBABOOM   ; "Wham!"                   (no sound engine)
LDA #$80 / STA Level_PSwitchCnt            ; duration                  (feature 5)
LDA #MUS2B_PSWITCH / STA Sound_QMusic2     ; music                     (feature 6)
```

`CPX #$02 / BGS` rejecting head probes is the evidence the switch is solid and standable.
The `CMP Level_ChgTileEvent / BEQ` is the ROM's own idempotence guard.

### 2.3 Screen shake — `prg008.asm Player_DoVibration` (~:950, feature 4)

```
VibrationOffset:                     ; ~:945
	.byte 0,  2,  3,  1      ; used while Vert_Scroll  < $80
	.byte 0, -2, -3, -1      ; used while Vert_Scroll >= $80
Player_DoVibration:
	LDA Level_Vibration / BEQ done
	DEC Level_Vibration
	AND #$03                 ; index = PRE-decrement count & 3
	LDY <Vert_Scroll / BPL + / ORA #$04
+	TAY / LDA VibrationOffset,Y / PHA
	ADD Level_VertScroll / STA Level_VertScroll
	PLA
done:	STA Vert_Scroll_Off
```

- **Vertical only, whole pixels, amplitude ≤ 3.** No horizontal component.
- **The visible cycle is `0, 1, 3, 2`, not the table's declared order.** `DEC` touches memory, so
  `A` still holds the pre-decrement count at `AND #$03`; with the count running `$10..$01` the
  indices descend `0, 3, 2, 1`. Keep the table in declared order **and** index it with `count & 3`
  — reordering it to playback order desynchronises the pair (a unit test caught exactly this).
- **It bypasses the scroll limits**: the offset lands in `Vert_Scroll_Off`, written after
  `Player_DoScrolling` has applied its rules and clamps. That is why the shake is visible even in a
  horizontal level locked at the bottom.
- The second table half only flips the sign by which nametable half `Vert_Scroll` addresses — a PPU
  artefact, no gameplay meaning; port one sign.
- Called from the player chain at ~:936, immediately after `Player_DoSpecialTiles` — the routine
  that presses the switch. So the press frame itself shows offset 0 (`$10 & 3 == 0`).
- **Shared primitive, not a P-Switch detail**: `Level_Vibration` is also set by the heavy
  Koopalings' slams and other impacts — `prg001.asm:4569`, `:5335`, `:6140`, `prg004.asm:1354`,
  `:2205`, `prg005.asm:4644`, `:6226` (`smb3.asm:2678`: "from impact of heavy fellow").

### 2.4 Duration countdown — `prg008.asm` (~:391)

While `Level_PSwitchCnt > 1` it decrements **only** when `Counter_1 AND #$03 == 0`
⇒ once every 4 frames ⇒ `$80 × 4 = 512` frames ≈ **8.5 s** at 60 Hz. At count `== 1` it
restores music via `Level_MusicQueueRestore`, or the invincibility song (`$0A`) if
`Player_StarInv >= $20`, then decrements to 0.

### 2.5 Brick↔coin substitution — `prg000.asm:1599` (feature 5)

```
PrePSwitchTile:   .byte TILEA_COIN, TILEA_BRICK, TILEA_MUNCHER, TILEA_PSWITCHCOIN
PostPSwitchTile:  .byte TILEA_BRICK, TILEA_COIN,  TILEA_COIN,   TILEA_COIN
PostPSwitchAttr:  .byte $03,         $00,         $00,          $00
PSwitch_SubstTileAndAttr:   ; no-op unless Level_PSwitchCnt != 0
```

Critical: this is a **read-time substitution during tile lookup**, not a rewrite of the
level grid. It is **bidirectional** (coins→bricks as well as bricks→coins) and also maps
munchers and P-switch-only coins to coins. Called from
`Player_GetTileAndSlope_Normal` / `Player_GetTileV` (`prg008.asm:4331`, `:4368`), and
skipped while `Level_PipeMove != 0`.

**The look and the behaviour are substituted by two different mechanisms — do not conflate them.**

- **Look = CHR bank override.** While `Level_PSwitchCnt != 0` the animated background pattern bank is
  pinned to page `$3E`: `prg030.asm:1422` at screen init and `PRG030_8E24` (`:2257`) per frame, the
  latter also `JMP`-ing past the whole animation routine. Bricks draw with the same pattern indices
  whatever they contain, so **every** brick shows coin art — including reward bricks — and it is static
  because the bank no longer cycles. Nothing in the level map changes.
- **Behaviour = this tile table.** `PrePSwitchTile` lists only the contents-free `TILEA_BRICK` ($67).
  A brick with contents is a *different* tile — `TILEA_BRICKFLOWER` $68, `BRICKLEAF` $69, `BRICKSTAR`
  $6A, `BRICKCOIN` $6B, `BRICKCOINSTAR` $6C, `BRICK10COIN` $6D, `BRICK1UP` $6E, `BRICKVINE` $6F,
  `BRICKPSWITCH` $70 (`smb3.asm` ~:3669) — and none appear in the table, so reward bricks stay solid
  and keep dispensing while looking like coins.

The three callers are all *logic* lookups, which is why the split falls out this way: object tile
detection (`prg000.asm:1578`), player projectile tiles (`prg007.asm:1059`) and player tile detection
(`prg008.asm:4331`, `:4368`). The routine also writes the replacement attribute to `Player_Slopes`,
which is what makes the substituted brick non-solid.

Also relevant: the same bank pinning is why the substituted coins appear static.

### 2.6 Collecting a substituted brick — `prg008.asm PRG008_B604` (~:4728)

```
	LDA Level_Tile_GndL,X
	CMP #TILEA_COIN
	BNE PRG008_B623            ; not a coin -> fall through to the P-Switch test
	LDA #CHNGTILE_DELETECOIN
	JSR Level_QueueChangeBlock ; erase to background (TILEA_COINREMOVED = $41)
	JSR Level_RecordBlockHit   ; "so it does not come back"
	LDA Sound_QLevel1 / ORA #SND_LEVELCOIN / STA Sound_QLevel1
	LDA #$00 / STA Level_Tile_GndR ; stop a straddled coin being collected twice
```

No special case for the P-Switch: this branch compares the tile the player's detection *just
fetched*, and that fetch already went through `PSwitch_SubstTileAndAttr` — so a substituted brick is
collected by the ordinary coin path. Consequences worth keeping: it is **permanent**
(`Level_RecordBlockHit`, and `$41` is not in `PrePSwitchTile`, so it does not revert when the window
closes), it awards **no score** — only the coin counter — and it fires from the feet *and* head
detects (no `CPX #$02` guard, unlike the P-Switch stomp below it).

### 2.7 Tiles, constants, change table

- `TILEA_PSWITCHCOIN = $05`, `TILEA_PSWITCH_BLANK = $C1` (hides a spent switch on level
  reload), `TILEA_PSWITCH_PRESSED = $D7`, `TILEA_PSWITCH = $F2`.
- `CHNGTILE_PSWITCHSTOMP = $09`, `CHNGTILE_PSWITCHAPPEAR = $12`.
- `MUS2B_PSWITCH = $A0` (`smb3.asm:1427`).
- `Level_PSwitchCnt` (`smb3.asm:1651`): non-zero ⇒ active; init `$80`.
- `prg029.asm:2545` `OneTile_ChangeToTile` (1-indexed): `$09 → TILEA_PSWITCH_PRESSED`,
  `$12 → TILEA_PSWITCH`.
- `prg029.asm:2553` `OneTile_ChangeToPatterns`: `$09 → $FF,$FF,$E6,$E7` (blank top row,
  flattened slab on the bottom), `$12 → $E0,$E2,$E1,$E3`.
- `prg008.asm:1258` and `prg000.asm:1257` both special-case `TILEA_PSWITCH_PRESSED` out
  of the solid-floor comparison against `Tile_AttrTable` — the pressed switch is walked
  through. (`Tile_AttrTable` is filled per-tileset by `Fill_Tile_AttrTable_ByTileset`,
  `prg030.asm:3583`; the data table itself was never located and the thresholds were
  never verified.)

### 2.8 Animation timing (verified against CHR)

The switch and its poof are both driven by the global animated-bank cycle — see
`kb/sprites_extraction.md` §"Animated background tiles" for the mechanism.

- **Switch block**: patterns `$E0,$E2,$E1,$E3`. Banks `$62` and `$66` hold
  byte-identical CHR, so there are only **3 distinct images**, played **A-B-C-B** at
  8 ticks each (middle frame 50%, outer two 25%). The only pixels that change are the
  20 forming the "P" interior, cycling palette index `0 → 2 → 3 → 2`.
- **Pressed switch**: patterns `$FF,$FF,$E6,$E7`, byte-identical across all four banks
  ⇒ genuinely static, no animator needed.
- **Poof**: `prg007.asm SObj_Poof` (:5249). Counter decrements per tick but the index is
  `data >> 3` ⇒ 4 frames × 8 ticks = 32, then `SpecialObj_Remove`.
  `Poof_Patterns: .byte $47, $45, $43, $41` (`prg007.asm:5247`) — **identical** to
  `SuitLost_Poof_Patterns` (`prg029.asm:2262`).

---

## 3. Implementation map

| Concern | Type |
|---|---|
| Spawner flavours | `BlockType.BRICK_SWITCH_BLOCK_SPAWNER` / `QUESTION_SWITCH_BLOCK_SPAWNER`, handled in `Block` |
| Switch tile | `game/object/level/block/SwitchBlock` + `animation/SwitchBlockAnimator` |
| Pressed tile | `game/object/level/block/PressedSwitchBlock` (non-collidable, static art) |
| Press dispatch | `LevelObject.onCollisionFromAbove`, fired by `StaticEnvironmentCollisionGrid` |
| Screen shake | `game/camera/LevelSceneVibration` (`@Singleton`) |
| Active window | `game/time/PowerSwitchTimeWindow`, owned + ticked by `StaticEnvironmentCollisionGrid` |
| Brick→coin look | `BrickBlockAnimator.getFramePixels` override + `sprites/object/coin/flipping/frame_0.png` |
| Brick→coin collision | `Block.isCollidable()` / `Block.onTailAttack` guard |
| Brick→coin collection | `LevelObject.onPlayerOverlap` ← the grid's `tick()` → `resolvePlayerOverlaps()` |
| Poof | `game/object/level/effect/{PoofAnimation,PoofMotionManager}` + `model/game/effect/PoofSequence` |
| Assets | `sprites/object/block/switch/frame_{0,3}.png`, `pressed.png`, `sprites/effect/poof/frame_{0,3}.png` |

### Press flow
`onCollisionFromAbove` → guard on `pressed` → `switchBlockAnimator.unregisterAt(offset)`
→ place `PressedSwitchBlock` in the grid → `paintToBakedTexture(pressed art)` →
`gameEngine.getLevelSceneVibration().powerShake()`.

### Shake flow
`LevelSceneVibration` is a singleton owned by `GameEngine`: reset in `setupLevel`, ticked last in
each 60 Hz step of `simpleUpdate` (matching `Player_DoSpecialTiles` → `Player_DoVibration`), and
read by `CameraState` through `setVibrationProvider`, installed next to `setVerticalScrollProvider`
in `LevelScenePlayer.updateInCameraState` (so the world map never shakes). Any future caller —
Koopaling slam, etc. — just needs its own named preset beside `powerShake()`.

### Brick↔coin flow (feature 5)
`PowerSwitchTimeWindow` (`game/time/`) belongs to **`StaticEnvironmentCollisionGrid`**, not the engine:
the press is a tile event the grid dispatches, and everything the window changes is a tile lookup the
grid answers. The grid takes it as a constructor argument from `toCollisionGrid`, which resets it, so its
lifetime is the grid's = the level's — no cross-level leak to guard against. `SwitchBlock` opens it with
`collisionGrid.pressPowerSwitch()`, and `GameEngine` only drives the clock: one `collisionGrid.tick()`
per sim step (once per step, *not* per player, or a 2P scene would count down twice as fast). Nothing is
written to the level grid — both halves are read-time, exactly as `PSwitch_SubstTileAndAttr` is:

- **Look**: `BrickBlockAnimator.getFramePixels(frame)` returns the coin sprite instead of the brick
  frame while the window is open, so *all three* brick flavours it owns (no-reward, with-reward,
  brick spawner) wear a coin — which is what the ROM's CHR bank override does too (§2.5). `update()`
  also fires `repaintCurrentFrame()` on the open/close edge, so the swap lands on the same tick rather
  than up to 8 ticks later. The frame counter keeps advancing (every frame resolves to the same coin),
  which self-heals a tile another effect repainted mid-window — the ROM instead pins the animated bank,
  same visible result. This animator reads the window singleton via `getBean` rather than through the
  grid, because animators are singletons handed only a geometry and dimensions — they have no level
  context.
- **Collision**: `Block.isCollidable()` returns `false` while
  `!hasReward() && gameEngine.getCollisionGrid().isPowerSwitchActive()`, and `onTailAttack`
  early-returns in the same condition. The grid consults `isCollidable()` live on every probe
  (`StaticEnvironmentCollisionGrid:327`, `:376`), so nothing needs restoring on expiry.
- **Collection**: the grid's own `tick()` runs `resolvePlayerOverlaps()` — a sweep over every level
  player, dispatching `LevelObject.onPlayerOverlap` to each cell that player's hitbox covers, the port of
  the ROM's `Level_Tile_Gnd*` detects — and then counts the window down, in that order so the final frame
  of the window can still be collected. `Block.onPlayerOverlap` collects when coin-substituted: unregister
  from the animator → erase the tile → `removeLevelObjectAt` → `playerData.addCoin()`. Permanent and
  score-free, per §2.6.
  <br>**Trap**: the sweep is gated on `hasOverlapSensitiveTiles()`, which today is just
  "P-Switch window open". Any new pass-through tile (a placed coin, a muncher, `TILEA_PSWITCHCOIN`) must
  add its own term there or its `onPlayerOverlap` will silently never fire. The ROM has no such gate —
  `Player_DoSpecialTiles` runs every frame — and the sweep is only a handful of cell lookups, so the gate
  states intent rather than saving work.

Scoped-out for now: real `Coin` objects do **not** turn into bricks (the ROM's substitution is
bidirectional), and munchers / `TILEA_PSWITCHCOIN` have no port yet. Note `Coin.onCollisionWith` is
still dormant — nothing inserts level coins into the active-object broadphase — so `onPlayerOverlap` is
the obvious way to make placed coins collectable when that is picked up.

### Spawn flow
`Block.hit` → `dispenseReward` → `onSwitchBlockDispensed` → place `SwitchBlock` above,
register with its animator, bake frame 0, then `PoofMotionManager.spawn` at the same cell.
A spawner's contents are ordinary tile-record data (`reward: SWITCH_BLOCK`), set in the DB like any
other block's reward — `Block.configure` reads it, nothing is hardcoded per flavour.

---

## 4. Invariants and traps

- **Every `BlockType` constant must have an identically named
  `LevelObjectTypeSingleTiled` constant** whose `instanceType` is `Block.class` —
  `Block.configure` resolves its type with
  `Enum.valueOf(LevelObjectTypeSingleTiled.class, blockType.name())`. A missing
  counterpart throws at configure time.
- **`Block.getAnimator()` duplicates the type→animator mapping.** It is a ternary on
  `blockType`, while registration at level load goes through
  `GameObjectAnimator.Registry.findSuitableAnimator(type)` driven by each animator's own
  `getSupportedTypes()`. If the two disagree, `hit()`'s `unregisterAt` reaches an
  animator that never held the block and the real one keeps repainting the tile forever
  — the tile appears to animate after being spent. Keep both sides in sync when adding a
  flavour.
- **Retiring a tile that animates**: always `unregisterAt(offset)` on the owning animator
  *before* baking replacement art, or the next frame flip paints over it.
- **Layer consolidation is lossy — never clear a cell to empty.** The collision grid is built from
  `getTilesOfConsolidatedLayers()`, where a higher layer *overwrites* the cell, so a walkable
  decoration sharing a cell with an interactive brick leaves no `LevelObject` behind it. Clearing the
  cell on removal therefore deleted the decoration from collision while the renderer kept drawing it
  (layers are separate geometries) and the player fell through visible ground. `removeLevelObjectAt`
  now restores the grid's `underlayObjects` view — the same level consolidated *without* its
  interactive-objects layer (`getTilesOfLayersBelow(INTERACTIVE_OBJECTS)`) — which fixes brick breaking
  and coin collection too, not just the P-Switch. The underlay is one level deep and its objects are
  deliberately **not** registered with any animator, since an exposed underlay tile is static art.
- **Z order**: the interactive-objects layer is order 4 ⇒ `Z = 0.04`; block bounce is
  `0.05`. `Z_DEPTH_POOF = 0.045f` sits between, so the poof covers the switch but passes
  behind the empty block bouncing out of the spawner.
- **The shake must be applied outside the camera's level-bounds clamp.** `CameraState.update`
  adds it after `clipping.clamp` and after the pixel snap, and deliberately not into the stored
  `position` field (which `getActiveObjectRegion` reads). Folding it into the vertical-scroll
  provider — the literal `Level_VertScroll += offset` port — looks right but gets swallowed by
  `Clipping.clamp` in a horizontal level, whose camera Y is pinned at `minCameraY`.
- **Never interpolate the shake.** Unlike `LevelSceneVerticalScroll.interpolate`, the offset is a
  step function held for a whole 60 Hz tick; easing it across render frames smears the jitter away.
  Same reason its countdown lives in the sim loop, not in the per-render-frame `CameraState.update`.
- **Texture row order**: `GameRenderer.loadTexture(rgbData, dimensions)` emits source rows in
  reverse, because jME3 reads the buffer bottom-up — so the 2-arg call yields the **upright** image
  and `loadTexture(..., true)` the vertical mirror. `PoofAnimation` had its own hand-rolled copy of
  that loop with the labels inverted (its `flipVertically = false` was the mirrored one); it now uses
  the shared helpers, so the mirror phase of the puff is the opposite of what shipped before. The two
  states are exact mirrors and the ROM's own phase comes from the global `Level_NoStopCnt`, so this is
  a phase difference, not a correctness one.

---

## 5. Outstanding

1. `LargeToRaccoonAnimator` passes a fixed `LEFT` to `rebuildWithTexture`, but the ROM
   sets `%11000001` (H+V flipped) for the suit poof (`prg029.asm Player_SuitLost_DoPoof`)
   — that animator's orientation looks independently wrong. Unverified.
2. `sprites/object/brick/poof_1..4.png` are staged for deletion; nothing references them.
3. `LevelObjectService.getLevelObjectIdOfType(type)` ignores its parameter and always
   looks up `EMPTY_BLOCK.name()`. Pre-existing bug, unfixed.
