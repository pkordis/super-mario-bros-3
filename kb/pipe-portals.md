# Pipe Portals

A **pipe portal** is an invisible marker over a pipe's lip that makes the pipe a way in (`IN`), a way out (`OUT`), or
both (`IN_AND_OUT`). It replaces the ROM's per-tileset enterable-pipe tile ids (`TILE1_PIPETB1..4`, `TILE3_PIPETB5`,
`TILE1_PIPEH1_B`, gated by `PipeTile_EnableByTileset`, dasm prg008.asm:6097).

## Model

- `VirtualObjectType` gives the `kind`, the footprint (`rows` × `columns`), the editor border `colour` and a `label`:
  - `PIPE_PORTAL_HORIZONTAL`: 1 × 2, the lip of an upright or upside-down pipe.
  - `PIPE_PORTAL_VERTICAL`: 2 × 1, the lip of a side pipe.
  - Both are of kind `VirtualObjectKind.PIPE_PORTAL`. The third type, `CARD`, is not a portal and
    never becomes a `PipePortal`; see `kb/level-completion.md`.
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

## Links

- A portal names its partner by id in `linksToId` (domain and record alike), `null` while it is free
  (`PipePortalCapabilities.isLinked()`). Not a `PipePortal` reference: two `@Data` objects holding each other recurse
  forever in `equals` / `hashCode` / `toString`.
- The ROM has no pairs: every pipe of an area leads to that area's alternate layout, at the junction start of the
  screen it was entered on (dasm prg026.asm `LevelJct_General`, `Level_JctXLHStart` / `Level_JctYLHStart`). Links are
  this port's own, one to one, stored on both ends.
- `game.level.pipe.PipePortalLinkRulesAware` (default methods; `PipePortalConverter`, and so `PipePortalService`,
  extends it):
  - `isLinkable` allows every pair with a way in and a way out: all but `IN`/`IN` and `OUT`/`OUT`.
  - `destinationOf(entered, linked)` is the partner only when it is exitable and points back, so entering the
    `IN_AND_OUT` end of an `IN`/`IN_AND_OUT` pair leads nowhere.
- **The database owns the links, not the editor.** `PipePortalService.link` / `unlink` write straight to the store.
  An editor tab loaded before a link was made still holds `null`, so `replaceAll` writes each already-stored portal's
  **stored** `linksToId` and starts new ones free, whatever the tab holds.
- **No dangling links.** `replaceAll` frees the partner of every stored portal missing from the saved list (it was
  right-clicked away), and `deleteByLevelSceneId` frees the partners of all the scene's portals, in any scene
  (`PipePortalRepository.clearLinksTo`).
- Nitrite's `ObjectRepository.update(record)` does clear a field set to `null` (checked against 5.4.2), so a link is
  cleared with a plain update. Mongo's `@Id` is `_id`, so its id filters use `"_id"`, not `Fields.id`.
- `LevelSceneService.findById` loads a scene with its portals; `findTitlesById` lists titles only (Mongo projects
  `title`; Nitrite has no projection on an object repository and reads each scene whole).

## Editor

- **Palette:** the third `LeftPane` tab, `VirtualObjectsPalettePanel`, holds one `VirtualObjectButton` per
  `VirtualObjectType`. The stamp is the real footprint at `TILE_SIZE` (64 px) per cell, transparent, with a 4 px
  border in the type's colour. Buttons are `LayerGatedToggleButton`s owned by the NPC layer, so arming one switches to
  that layer, and leaving it releases the stamp. `ActiveLayerMenu.activate` re-syncs this panel too.
- **Placing:** `LevelSceneEditorTab.paintTile` checks an armed virtual object first and dispatches on its `kind`; a
  `PIPE_PORTAL` goes to `placePipePortal`. The clicked cell is the anchor.
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
- **Type label:** each portal's `PipePortalType.shortLabel` (`IN`, `OUT`, `I/O`) is centred inside its border; the
  palette shows none, because the type is only chosen after placing.

## Editor: Pipe Portal Linkage (SMB3-38)

- **Level Objects → Pipe Portal Linkage** (`PipePortalLinkageMenuItem`, last entry) opens
  `ui.editor.level.virtualobject.linkage.PipePortalLinkageWindow`. The window only renders
  `PipePortalLinkageModel`, which holds all the behaviour and is tested without Swing.
- **Two reads per open or refresh:** `reload()` calls `PipePortalService.findAll()` and
  `LevelSceneService.findTitlesById()` once each; partitioning, sorting, filtering and pair dedupe run in memory, so
  hundreds of portals are no concern. Only **saved** portals exist there: a scene must be saved before its new
  portals can be linked.
- **Lists:** one per type of free portals (by scene title, column, row), plus the linked pairs, each pair once with
  its way in first (`IN`, then `IN_AND_OUT`, then `OUT`; equal types by id). A portal whose partner is gone, or does
  not point back, is a pair with a missing exit, so it can still be unlinked. The filter matches a scene title or a
  description, ignoring case, across all four lists.
- **Slots:** a double-click puts `IN` in Entrance and `OUT` in Exit; `IN_AND_OUT` takes Entrance, then Exit, then
  replaces Exit. A portal already in a slot is ignored. A reload empties a slot whose portal is gone or was linked
  meanwhile. **Link** is enabled when both slots hold a pair `isLinkable` allows.
- **Open tabs follow:** after a link or unlink, every open editor tab's copy of the affected portals takes the stored
  `linksToId` (`LevelSceneEditorTabSystem.getEditorTabs()`, which walks the tab components, so imported and unsaved
  tabs are covered too). Saving would be safe anyway, because `replaceAll` keeps the stored links.
- **Refusals:** an `IllegalStateException` from the service (e.g. a portal linked meanwhile) is shown as a warning and
  the window re-reads everything.

## Runtime: entering a pipe (SMB3-32)

- **No separate lookup:** the player reads `getLevelScene().getPipePortals()` directly. There is no per-level index
  and nothing on `GameEngine`: portals live on the scene and don't change during a run, and a scene has a handful, so
  a scan per tick costs nothing. Cells use the collision grid's coordinates (row 0 at the top).
- **Rules** (`game.level.pipe.PipeEntryRulesAware`, default methods the player inherits):
  - `isEnterable`: the type is `IN` or `IN_AND_OUT`.
  - `isEntryCell`: every cell of a horizontal portal; only the anchor (lower cell) of a vertical one, because the ROM
    checks `TILE1_PIPEH1_B`, the bottom half of a side lip, only.
  - `entryDirection(tiles, portal, playerX)`, worked out at the check, never stored: empty when the portal is no
    longer over `PIPE_TERMINATION_PART` (lip erased after placing). A horizontal portal gets `DOWN` when both cells
    directly below are `PIPE_BODY_PART`, else `UP` when both cells directly above are, else empty. A vertical
    portal gets `Pipe_PadDirForEnter`: `RIGHT` when `Player_X & 15 < 8`, else `LEFT`.
- **Detection:** `LevelScenePlayerPipeUsingCapabilities.enterPipeIfPossible`, called from `LevelScenePlayer.updateFrame`
  after collision, `refinePlayerState` and the tail block (the ROM's order: `Player_DetectSolids`,
  `Player_TailAttack_HitBlocks`, `Player_DoSpecialTiles`, prg008.asm:933-935). An entry ends the tick.
  - Grounded: the side pipe check first (the in-front probe at `Y+$1B`, the ROM's `Level_Tile_InFL`), then the
    upright pipe check (the left foot `X+4, Y+$20`, `Level_Tile_GndL`).
  - Airborne: only after `PlayerRuntimeState.hitCeiling` (dasm `Player_HitCeiling`, set by the collision grid's
    moving-up branch and cleared at the start of every pass), not ducking, through the head probe.
  - Directions are read with `isActive` (`Pad_Holding`), never `consumePress`.
- **Alignment window** (`PipeEntryRulesAware.isAligned`, `PRG008_BD1F`-`BD4B`): ported as 8-bit arithmetic. The ROM's
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
  prg008.asm:347-353), so `GameEngine.simpleUpdate` freezes the timer, enemies, projectiles and rewards while score
  popups keep rising. The P-switch window keeps counting (SMB3-69): the ROM's countdown sits after the halt flag in
  `Player_DoGameplay` and does not read it. `isHurtable()` is false and `onRewardConsumption` ignores rewards.
- **Tick** (`tickPipeEntering`, `Level_PipeEnter` prg029.asm:1323-1434): runs instead of input, physics, collision,
  scrolling and the pit check. The countdown is decremented first; that reads the same values as the ROM, which
  decrements `Event_Countdown` at the end of every frame including the entry frame (`PRG008_A1DE`). So:
  - up / down: 0.5 px on each of the 59 ticks reading 59 to 1 (`PipeMove_YVel = 8, -8`), 29.5 px;
  - left / right: 0.5 px only while the countdown is at least 16 (`PipeMove_XVel`), 44 ticks and 22 px; the
    sprite is drawn 2 px higher meanwhile (`pipeStepUp()`; the ROM lowers `Player_Y` around the draw only), and is
    not drawn below 16;
  - the tick reading 0 hides the player and publishes `PipePortalTransitRequested` once (guarded by
    `pipeTransitPublished`), which starts the pipe trip below; without a destination the trip restarts the level as R
    does, and `buildLevel` then restores the camera target, visibility and timer.
- **Frames** (`BaseLevelScenePlayerAnimator.renderPipePose`, checked before every other pose): face forward going
  up or down (`PipeMove_Frame`, the carry turn-around frame); the suit's walk sequence going sideways, at beat
  `(ticks since entry & $0C) >> 2`. The port had no `Counter_1` then, so the ticks since the entry stand in for
  it; SMB3-44 added `LevelFrameCounter` (see `kb/level-completion.md`), which this pose does not use yet.
- **Held objects:** a shell carried into a pipe sits right over the player (`CarriableLevelObject.carryAnchorFrom`):
  in a pipe the ROM uses `ObjectHoldXOff` entry 10, `$00`, instead of the facing and turn-around entries
  (prg000.asm:2804, 2997-3001). The Y drop is unchanged. The carry can't be released in the pipe, because the
  object's state routine is halted along with the rest of gameplay.
- **Out of scope:** exits, transit pipes and junctions, Kuribo's shoe, sound (`SND_PLAYERPIPE`).

## Runtime: coming out of a pipe (SMB3-40)

- **Entry point:** `beginPipeExit(portal)`. The pipe trip (SMB3-41) calls it; nothing else does.
- **Where the code lives:** every pipe behaviour of the player (detection, `beginPipeEntry` / `tickPipeEntering`,
  `beginPipeExit` / `tickPipeExiting`, `centreVerticalScrollOn`, their helpers and constants) is in
  `LevelScenePlayerPipeUsingCapabilities`, which `LevelScenePlayerCapabilities` extends. `LevelScenePlayer` only
  routes `updateFrame` to the two ticks and holds the `pipeTransitPublished` flag behind `isPipeTransitPublished` /
  `setPipeTransitPublished`.
- **Direction** (`PipeEntryRulesAware.exitDirection(tiles, portal)`), from the tiles like the entry's: away from
  the body, out through the lip. Horizontal portal: `UP` with body below (upright pipe), else `DOWN` with body
  above. Vertical portal: `LEFT` with body in the column to the right, else `RIGHT` with it to the left. Empty
  when the lip is gone, and then the player is simply stood on top of the portal, with any entering state dropped so
  it gets control back.
- **Timing** (`Event_Countdown_Init`, prg008.asm:735, loaded by `PRG008_A38E`): `PIPE_EXIT_TICKS_UP = $3F`,
  `_DOWN = $07`, `_SIDE = $20`. The `Pipe_Move_*` handlers (prg029.asm:1626-1740) **move first, then test** the
  countdown, and `PRG008_A1DE` decrements it after; so the player moves on every value from the start down to 0:
  up 64 × 0.5 px = 32 px, down 8 × 3.5 px = 28 px (`YVel = $30` then `PipeMove_UpDown`'s 8), sides 33 × 0.5 px =
  16.5 px. That is the opposite order to the entry, which decrements first.
- **Positions** are defined by where the exit ends (the position is the top of a 32 px box, so feet = Y + 32 for every
  size): up ends with the feet on the lip's top edge, centred on the lip (`(X & $F0) | 8`), and starts 32 px lower;
  down starts with the feet on the lip's bottom edge, centred; sides keep the feet on the mouth's bottom edge and start
  at the mouth tile's left edge, not centred (`X & $F0`).
- **End:** up and sides end in `Player_StopMovement` (standing, no velocity); down ends in the air with the duck
  cleared, `DX = 0` and `DY = $38 / 16 = 3.5`, falling out under normal physics.
- **During:** the state owns `updateFrame` (no input, physics, collision, scroll or pit check), halts gameplay, makes
  the player unhurtable and ignores rewards, like the entry. The player is behind the foreground layers so the pipe
  covers it; side exits use the walk frame (beat from the ticks since the start) and the 2 px step-up draw. The camera
  follows the player again (`setTarget(node)`), since the entry held it.
- **Shared "in a pipe" checks** now use `PlayerRuntimeState.isInPipe()` (entering or exiting): the halt, the hurt and
  reward guards, `enterPipeIfPossible`, the carried object's anchor and draw order, and the animators' pipe pose.
  `startExitingPipe` drops any entering state, so one player can go in and come out within one scene.

## Runtime: pipe trips (SMB3-41)

- **ROM model** (dasm): `Level_PipeEnter` stores the pipe action in `Level_JctCtl` when the entry countdown ends
  (prg029.asm:1325-1361); the main loop sees it (`PRG030_8ECC`) and runs `HandleLevelJunction` (prg026.asm:1496-1575):
  fade out, display off, `LevelJct_General` places the player and sets the scroll, the area reloads through
  `PRG030_897B`, and the fade-in at `PRG030_8B9A` finishes before `Level_MainLoop`. The timer is not reset
  (prg030.asm:4374-4377) and `Player_Suit` is not touched. `Pipe_Transit` (prg029) moves within an area without a
  reload.
- **Request:** `tickPipeEntering` publishes `PipePortalTransitRequested(pipePortalId, inputHandlerId)` once.
  `TestSessionEventListener` hands it to `LevelSceneTesterSession.onPipePortalTransitRequested`, which passes it to
  its `game.level.pipe.PipePortalTransit` (`@Prototype`, created on the first request with the engine, both services,
  the `FadingEngine` and the session as `LevelSceneHost`).
- **Resolve:** `pipePortalService.findById(entered)`, then `findById(entered.linksToId)`, then
  `PipePortalLinkRulesAware.destinationOf`. The database decides, so an unsaved scene's portals lead nowhere. Without
  a destination (unlinked, linked to an `IN`, not saved, lookup failed) the host restarts the level, with no fade. A
  request during a trip is ignored (`AtomicBoolean`, until the fade-in's callback).
- **Trip:** `fadeOut(STANDARD, ...)`; its callback, inside `FadingEngine.tick()`, only `enqueue`s the arrival, so the
  swap never runs in the middle of a simulation tick. The arrival:
  - **Same scene** (`destination.levelSceneId` equals the running scene's id): nothing is rebuilt, so enemies,
    rewards and blocks keep their state; the current player comes out of the exit.
  - **Another scene:** `levelSceneService.findById`; the host's `swapLevelScene(scene, LevelRunStart.carryOver(mode))`
    detaches the run (`detachLevelRun`), `GameEngine.useLevelScene(scene)`, `buildLevel(start)`. The new player then
    comes out of the exit. A scene that is not saved fades back in and restarts the level.
  - In both: `beginPipeExit(exit)`, `centreVerticalScrollOn(exit)`, `fadeIn(STANDARD, ...)`.
- **Exit waits for the fade-in:** `tickPipeExiting` only snapshots while `FadingEngine.isHaltingGameplay()`.
- **`LevelRunStart`** (`game.engine`): `FRESH` (`SHRUNK`, timer restarted, P-meter emptied by
  `PlayerData.resetPowerMeter()`) is what `buildLevel()` uses; `carryOver(mode)` keeps the suit, the P-meter charge
  and leaves the timer running. Score, coins, lives and cards live on the shared `PlayerData` and carry over anyway.
  Before the P-meter reset, a death at full power respawned with a full meter (the meter does not drain while
  dying), so the new player had the power top speed and the `POWER_ON` loop until it drained.
- **One HUD viewport at a time:** every `buildLevel` builds a new `HeadsUpDisplay`, whose `@PostConstruct` adds a
  `"HUD"` main view. `buildLevel` calls `dispose()` (`RenderManager.removeMainView`) on the HUD it replaces first;
  before that, each death, R or pipe trip left one more stale HUD viewport rendering underneath every frame.
- **Restart goes home:** the session remembers the scene the engine held before its first swap
  (`originalLevelScene`); a death or R does `useLevelScene(original)` and `buildLevel()` (fresh). A restart that lands
  during a trip calls `PipePortalTransit.cancel()`, which drops the trip and `reset()`s the fade so the screen is not
  left black.
- **Camera:**
  - X follows the player node, which `beginPipeExit` has put inside the exit pipe; `CameraState`'s clipping keeps the
    view inside the level on both sides (the ROM's `Horz_Scroll = Player_X - $80` is clamped on the left only).
  - Y: `LevelSceneVerticalScroll.snapTo(centreY, playerY)` sets the camera, previous and interpolated values at once.
    `centreY` is the pipe's middle, `rows - (topRow + rows / 2)`. It is first moved just enough to keep the player's
    top **once out of the pipe** inside the dead-zone band (`[1.6, 3.6]` tiles above the centre), then clamped to
    the level. Without that, the band would jump the view on the first tick after the exit (about 1.9 tiles after
    a downward exit). A player standing on an upright pipe is 2.5 tiles above a one-row lip's middle, inside the
    band, so the common case is exactly centred; a downward exit puts the pipe about 1.9 tiles above the centre, a
    side exit about 0.6. Above the bottom lock the scroll stays free until it is back down, as after any free scroll.
- **Loading cost:** loading a scene resolves every cell of all six layers through `TileService.findById`. Before
  SMB3-41's fix, each empty cell (`NULL_TILE`, id 0, which has no record) and each virtual or unknown id missed the
  cache and went to the database, which against the remote Mongo meant tens of thousands of round trips for a full
  level and a long black screen. Those ids are now answered from memory; an id that once missed is remembered until a
  tile is created with it. The swap logs `Pipe trip into scene ...: loaded in N ms, built in M ms`.
- **The pad carries over:** every `buildLevel` (pipe trip, R, death) spawns a new player with a new
  `PlayerInputHandler`. jME reports only press and release edges, so the new handler would read a key held through
  the rebuild as up. `buildLevel` therefore detaches the old listener and calls `carryOverFrom(previous)` on the new
  handler, which copies the held keys and the presses already acted on. Input is dispatched on the engine thread
  between frames, so no event lands mid-swap.
- **Known limits:** destination scenes are read from the database, so unsaved edits in their tabs are ignored, and
  coming back to the tested scene through a link loads its saved version. Loading runs on the engine thread while the
  screen is black. A carried object is lost when the scene changes, because the new run starts with no objects. No
  pipe sound.
