# Pipe Portals

A **pipe portal** is an invisible marker over a pipe's lip that makes the pipe a way in (`IN`), a way out (`OUT`), or
both (`IN_AND_OUT`). It replaces the ROM's per-tileset enterable-pipe tile ids (`TILE1_PIPETB1..4`, `TILE3_PIPETB5`,
`TILE1_PIPEH1_B`, gated by `PipeTile_EnableByTileset`, dasm prg008.asm:6097).

## Model

- `VirtualObjectType` gives the footprint (`rows` × `columns`), the editor border `colour` and a `label`:
  - `PIPE_PORTAL_HORIZONTAL`: 1 × 2, the lip of an upright or upside-down pipe.
  - `PIPE_PORTAL_VERTICAL`: 2 × 1, the lip of a side pipe.
- `PipePortal` (`model.game.portal`) holds `id`, `description`, `type` (`PipePortalType`), `virtualObjectType`,
  `levelSceneId`, `row` and `column`.
- **Anchor:** `row` / `column` is always the portal's **lowest leftmost** cell. Row 0 is the top of the scene, so the
  footprint covers rows `row - rows + 1` to `row` and columns `column` to `column + columns - 1`.
  `PipePortalCapabilities.cells()` / `occupies()` / `overlaps()` work this out; nothing else should.
- A portal is never written into a layer's `Tile[][]`. It belongs to the NPC layer only in the editor's sense: that
  is the layer it is placed and drawn on.

## Placement rules

`game.level.pipe.PipePortalPlacementRules`: every cell inside the scene, every cell over a `PIPE_TERMINATION_PART`
tile on the static environment layer (`PIPE_BODY_PART` does not count), and no overlap with another portal.
`validate(...)` returns the first broken rule as a sentence for the editor. The runtime re-checks
`isOverPipeTermination` because the pipe can be erased after the portal was placed.

## Storage

- One `PipePortalRecord` per portal in the `pipePortals` collection (Mongo and Nitrite), keyed to its scene by
  `levelSceneId`. Portals are **not** embedded in the `levelScenes` document.
- `LevelSceneService` carries them with the scene:
  - `save` calls `PipePortalService.replaceAll(sceneId, portals)`: delete the scene's records, then insert the
    current list. Removing a portal in the editor therefore deletes its record on the next save.
  - `findLastEdited` and `findPage` attach each scene's portals. `findPage` must do this too, because the browse
    window's inline title/description edit saves the scene it holds, and an empty list would wipe its portals.
  - `delete` deletes the scene's portals.
- The converter writes the id of the scene being saved onto each record, not the portal's own `levelSceneId`, so a
  portal placed before the scene's first save still lands under the right scene.

## Editor

- **Palette:** the third `LeftPane` tab, `VirtualObjectsPalettePanel`, holds one `VirtualObjectButton` per
  `VirtualObjectType`. The stamp is the real footprint at `TILE_SIZE` (64 px) per cell, transparent, with a 4 px
  border in the type's colour. Buttons are `LayerGatedToggleButton`s owned by the NPC layer, so arming one switches to
  that layer, and leaving it releases the stamp. `ActiveLayerMenu.activate` re-syncs this panel too.
- **Placing:** `LevelSceneEditorTab.paintTile` checks an armed virtual object first. The clicked cell is the anchor.
  `PipePortalPlacementRules.validate` runs before anything is asked; a broken rule is shown through
  `PipePortalPrompt.warn`. Otherwise `PipePortalPrompt.ask` opens `PlacePipePortalWindow` (type defaults to `IN`,
  description optional) and **Place** adds the portal to `tab.pipePortals`.
- **No drag placement:** `LevelSceneEditorGrid` ignores drags while a virtual object is armed, or a drag would open a
  window per cell.
- **Removing:** right-click with the NPC layer active removes the portal under the cursor and leaves the NPC tiles
  alone; elsewhere right-click erases as usual.
- **Dialogs behind a bean:** the tab reaches the window and the warning only through `PipePortalPrompt`
  (`getBean`), so tests stub it instead of opening Swing windows.
- **Drawing:** after the tiles, grid lines and markers, each portal gets a 4 px rectangle in its colour around all its
  cells. Portals are drawn only while the NPC layer is visible.
- **Tab ↔ scene:** `LevelSceneEditorTabFactory.fromLevelSceneEditor` / `fromScene` copy the list both ways (a fresh
  list each time, so tab edits never leak into a loaded scene). `newScene` and `clearScene` empty it.

## Runtime: entering a pipe (SMB3-32)

- **No separate lookup:** the player reads `getLevelScene().getPipePortals()` directly. There is no per-level index
  and nothing on `GameEngine`: portals live on the scene and don't change during a run, and a scene has a handful, so
  a scan per tick costs nothing. Cells use the collision grid's coordinates (row 0 at the top).
- **Rules** (`game.level.pipe.PipeEntryRules`):
  - `isEnterable`: the type is `IN` or `IN_AND_OUT`.
  - `isEntryCell`: every cell of a horizontal portal; only the anchor (lower cell) of a vertical one, because the ROM
    checks `TILE1_PIPEH1_B`, the bottom half of a side lip, only.
  - `entryDirection(tiles, portal, playerX)`, worked out at the check, never stored: empty when the portal is no
    longer over `PIPE_TERMINATION_PART` (lip erased after placing). A horizontal portal gets `DOWN` when both cells
    directly below are `PIPE_BODY_PART`, else `UP` when both cells directly above are, else empty. A vertical
    portal gets `Pipe_PadDirForEnter`: `RIGHT` when `Player_X & 15 < 8`, else `LEFT`.
- **Detection:** `LevelScenePlayerPipeEntryCapable.enterPipeIfPossible`, called from `LevelScenePlayer.updateFrame`
  after collision, `refinePlayerState` and the tail block (the ROM's order: `Player_DetectSolids`,
  `Player_TailAttack_HitBlocks`, `Player_DoSpecialTiles`, prg008.asm:933-935). An entry ends the tick.
  - Grounded: the side pipe check first (the in-front probe at `Y+$1B`, the ROM's `Level_Tile_InFL`), then the
    upright pipe check (the left foot `X+4, Y+$20`, `Level_Tile_GndL`).
  - Airborne: only after `PlayerRuntimeState.hitCeiling` (dasm `Player_HitCeiling`, set by the collision grid's
    moving-up branch and cleared at the start of every pass), not ducking, through the head probe.
  - Directions are read with `isActive` (`Pad_Holding`), never `consumePress`.
- **Alignment window** (`PipeEntryRules.isAligned`, `PRG008_BD1F`-`BD4B`): ported as 8-bit arithmetic. The ROM's
  `Temp_Var2` is **16 for the left lip cell** and 0 for the right one, because it comes from
  `TILE1_PIPETB2_R - tile` and the left tiles (`$AD`, `$AF`) give odd results. The window is then `Player_X` from
  `16c + 3` to `16c + 12` for a pipe whose left cell is column `c`, for every suit, grounded or airborne.
- **No slopes:** the ROM also uses the airborne offset on sloped levels (`Level_SlopeEn`); the port has none.
- **State:** `PlayerRuntimeState.startEnteringPipe(portal, direction)` records the `PipePortal` entered and the
  direction held, and loads the countdown; `isEnteringPipe()` tests the portal. The portal is what a future pipe link
  resolves the other end from (by portal id), so neither end of a link has to reference the other.

## Runtime: going into a pipe (SMB3-33)

- **Start** (`LevelScenePlayer.beginPipeEntry`, dasm `PipeEntryPrepare` prg008.asm:6729-6811): the countdown is
  `PIPE_ENTRY_TICKS = $3C`; DX, DY and the duck are dropped; a side pipe turns the player to face the way it walks;
  visibility goes to `BACKGROUND` with `updateForegroundLayerBuckets()`, so the `STATIC_ENVIRONMENT` pipe tiles cover
  the player (this replaces the ROM's masking sprite and sprite trimming); `CameraState.hold()` keeps the camera where
  it is; the pipe pose is drawn at once.
- **Halt:** `isHaltingGameplay()` includes `isEnteringPipe()` (`Level_PipeMove` is ORed into `Player_HaltGame`,
  prg008.asm:347-353), so `GameEngine.simpleUpdate` freezes the timer, enemies, projectiles, rewards and the P-switch
  window while score popups keep rising. `isHurtable()` is false and `onRewardConsumption` ignores rewards.
- **Tick** (`tickPipeEntering`, `Level_PipeEnter` prg029.asm:1323-1434): runs instead of input, physics, collision,
  scrolling and the pit check. The countdown is decremented first; that reads the same values as the ROM, which
  decrements `Event_Countdown` at the end of every frame including the entry frame (`PRG008_A1DE`). So:
  - up / down: 0.5 px on each of the 59 ticks reading 59 to 1 (`PipeMove_YVel = 8, -8`), 29.5 px;
  - left / right: 0.5 px only while the countdown is at least 16 (`PipeMove_XVel`), 44 ticks and 22 px; the
    sprite is drawn 2 px higher meanwhile (`pipeStepUp()`; the ROM lowers `Player_Y` around the draw only), and is
    not drawn below 16;
  - the tick reading 0 hides the player and publishes `LevelSceneResetRequested` once (guarded by
    `pipeResetPublished`), the R key's chain; `buildLevel` then restores the camera target, visibility and timer.
- **Frames** (`BaseLevelScenePlayerAnimator.renderPipePose`, checked before every other pose): face forward going
  up or down (`PipeMove_Frame`, the carry turn-around frame); the suit's walk sequence going sideways, at beat
  `(ticks since entry & $0C) >> 2`. The port has no `Counter_1`, so the ticks since the entry stand in for it.
- **Held objects:** a shell carried into a pipe sits right over the player (`CarriableLevelObject.carryAnchorFrom`):
  in a pipe the ROM uses `ObjectHoldXOff` entry 10, `$00`, instead of the facing and turn-around entries
  (prg000.asm:2804, 2997-3001). The Y drop is unchanged. The carry can't be released in the pipe, because the
  object's state routine is halted along with the rest of gameplay.
- **Out of scope:** exits, transit pipes and junctions, Kuribo's shoe, sound (`SND_PLAYERPIPE`).
