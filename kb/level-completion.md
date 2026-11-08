# Level Completion

The end of a level: the end-level card the player touches, and what follows it. The feature is ticketed as
SMB3-42 to SMB3-49; this file grows with each of them.

## What the ROM does (dasm)

- The card is an ordinary level object, `OBJ_ENDLEVELCARD = $41` (smb3.asm), 16 × 16
  (`OA1_PAL1 | OA1_HEIGHT16 | OA1_WIDTH16`, prg002.asm:183), so it covers one tile.
- Its three faces are frames 0, 1 and 2: Mushroom, Flower, Star (`EndLevelCard_Untouched`, prg002.asm:5388-5406;
  `Player_GetCard`, prg031.asm:2883-2889).
- The Flower is drawn with sprite palette 2 and the other two with palette 1 (`EndLevelCard_AttributeByFrame`,
  prg002.asm:5336). The right half is the left half mirrored (`EndLevelCard_Draw`, prg002.asm:6294-6318).

## Assets

`src/main/resources/sprites/object/card/level_completion/{mushroom,flower,star}.png`, one 16 × 16 PNG per face. They
are the in-level card, used by the runtime card (SMB3-44). The editor does not draw them.

`src/main/resources/hud/card/{mushroom,flower,star}.png` are the status-bar cards (SMB3-43), different art: see
`kb/sprites_extraction.md` §9 for how they were extracted. `LevelCompletionCardType` carries both paths per card.

## Editor placeholder (SMB3-42)

- **Type:** `VirtualObjectType.LEVEL_COMPLETION_CARD` (yellow, 1 × 1, "Level Completion Card"), of kind
  `VirtualObjectKind.LEVEL_COMPLETION_CARD`. The kind is what the editor dispatches on, so a card never reaches the pipe
  portal code (`PipePortalPlacementRules`, `PipePortalPrompt`, `PipePortal`). `TileType.LEVEL_COMPLETION` is
  unrelated: it is for the decoration tiles of the level-completion area.
- **Palette:** `VirtualObjectsPalettePanel` builds one button per type, so the card has its own yellow 1 × 1 stamp
  with no card-specific code.
- **Placing:** `LevelSceneEditorTab.paintTile` → `placeLevelCompletionCard`. NPC layer only, any cell inside the scene,
  no prompt and no rules. A scene has at most one card, so placing it again moves it. It may share a cell with a pipe
  portal.
- **Removing:** right-click on its cell with the NPC layer active. `eraseTile` tries the card before the pipe portals
  (the card is drawn on top), so on a shared cell the first right-click removes the card and the second the portal.
- **Drawing:** `LevelSceneEditorGrid.drawLevelCompletionCard`, a 4 px yellow border on its cell after the pipe
  portals, only while the NPC layer is visible. Like every 4 px editor marker it bleeds one antialiased pixel past
  the cell edge.
- **Storage:** like the spawn point, one `Coordinates` field on the scene (`x` = column, `y` = row) rather than a
  collection of its own.
  - The tab holds `levelCompletionCard`, `null` for none (`hasLevelCompletionCard()`); `resetData` (new and
    cleared scenes) and `fromImport` reset it.
  - `LevelScene` and `LevelSceneRecord` hold it as `Coordinates`, `null` for none. `LevelSceneEditorTabFactory`
    opens a scene whose card has a negative component with no card.
  - `LevelSceneConverter` copies it both ways, and `LevelSceneRecordNormalizer` must copy it too: it rebuilds the
    record field by field, so a field it forgets is dropped on every save.
  - Mongo stores it as a nested `levelCompletionCard: {x, y}` document (the driver's record codec). Scenes saved
    without a card have no such field and read as `null`.
- **Runtime:** the card the player collects (SMB3-44, below).

## Held cards and the status bar (SMB3-43)

- **ROM storage:** `Inventory_Cards` (smb3.asm:2729), three bytes per player: `0` empty, `1` Mushroom, `2` Flower,
  `3` Star. The in-level card passes its frame + 1 (prg002.asm:6251-6253); `Player_GetCard`'s header comment, which
  says 0-2, is wrong. The cards are not reset between levels.
- **ROM adding:** `Player_GetCard` (prg031.asm:2883-2924) puts the card in the first empty slot. With all three full
  it copies slot 2 into 1 and slot 3 into 2, then writes the card into slot 3. It has no side effects; the status-bar
  redraw is `Player_GetCardAndUpdate`'s fall-through into `StatusBar_Update_Cards`. The in-level card calls
  `Player_GetCard` directly and checks for three matching cards right after (prg002.asm:6270-6290).
- **ROM drawing:** `StatusBar_DrawCardPiece` (prg031.asm:2810-2876) writes four BG tiles per slot from `CardUL`,
  `CardUR`, `CardLL`, `CardLR` (prg031.asm:2761-2764): empty `` x 4, Mushroom `-`, Flower
  `,,,` (`` is skipped), Star `-`. The slots start at nametable columns 22, 25 and 28 of rows
  25-26 (`CardVStartU`/`L`), which are x = 168, 192, 216 and y = 16 in `hud_base.png`.
- **Port:**
  - `enumeration.LevelCompletionCardType`: `MUSHROOM`, `FLOWER`, `STAR` in frame order; the ROM's stored value is
    `ordinal() + 1`.
  - `PlayerData`: three slots, `null` for empty. `addCard` follows `Player_GetCard`, `clearCards` empties them,
    `getCards()` returns an unmodifiable copy. `PlayerData.State` holds the cards, so a change re-renders the HUD
    and an unchanged set (a fourth Flower rotating into three Flowers) does not.
  - `PlayerData` belongs to the tester session's `GameEngine` and survives `buildLevel`, so the cards survive R,
    deaths and pipe trips. A new tester session starts with none.
  - `HeadsUpDisplayFontRenderer` loads the three status-bar PNGs into `CARD_CACHE`;
    `HeadsUpDisplayRenderer.renderCards` blits each held card over its box. An empty slot keeps the base image's box,
    which is already the `` tiles.
- **Not yet:** clearing the cards after a level with three, and the three-card bonus (SMB3-48).

## The card in the level (SMB3-44)

- **ROM, untouched state** (`EndLevelCard_Untouched`, prg002.asm:5378-5390): on every frame where
  `Counter_1 & 7 == 0` the frame goes 0, 1, 2, 0; then `Object_HitTestRespond` and `Object_DeleteOffScreen`.
  `Counter_1` is incremented every frame in the NMI (prg031.asm:1634), so the phase is global, not the card's.
  A new object starts on frame 0 (`Level_PrepareNewObject`, prg000.asm:4375). While gameplay is halted the card only
  draws (halt action 11, `OA3_HALT_ENDCARDSPECIAL`, prg000.asm:4446-4462). Nothing reads the player's speed.
- **ROM attributes:** bound box 1 (`OAT_BOUNDBOX01`, left 1, width 13, top 2, height 8: the top half),
  `OAT_HITNOTKILL` and fire-immune (prg000.asm:363), `OA3_TAILATKIMMUNE` (prg002.asm:273).
- **ROM touch** (`ObjHit_EndLevelCard`, prg002.asm:6146-6292): clears `Player_StarInv`, `Kill_Tally`,
  `Level_PSwitchCnt`, `Level_AScrlConfig`, `Player_YVel` and the player's projectiles; `Level_TimerEn = $81` stops the
  clock, and its bit 7 makes `PRG030_8DF4` skip the background tiles' bank cycling (prg030.asm:2216-2217);
  `EndCard_Flag`; `Player_GetCard` with frame + 1; then the coins, the flight, `MUS1_COURSECLEAR` and the three-card
  check that later tickets port.
- **Port:**
  - `game.engine.LevelFrameCounter` is `Counter_1`: `GameEngine.simpleUpdate` ticks it first on every simulation
    tick, halted or not, and `buildLevel` resets it, so each run of the level replays the same phases.
  - `game.level.completion.LevelCompletion`, one per engine (`GameEngine.getLevelCompletion()`), reset by
    `buildLevel`. It owns a `LevelCompletionCardSpawner` and runs the touch once per run, publishing
    `LevelCardCollected(card)`. `isUnderway()` and `getCollectedCard()` are for SMB3-45 to SMB3-49.
  - `game.object.level.card.LevelCompletionCardSpawner` places the card while its column lies between the enemies'
    look-ahead columns (32 px past the screen's left edge, 16 px past its right edge,
    `ActiveLevelObjectMotionManager.SPAWN_LOOK_AHEAD_*`), and again after it was deleted. That window is inside the
    delete edges, so a card never spawns where it would be deleted. A collected card is retired for the run.
  - `LevelCompletionCard` (`LevelObjectTypeVirtual.LEVEL_COMPLETION_CARD`) turns in `advance(counter)`. The tick it
    was placed on does not count, so the first change comes 1 to 8 ticks later. Its box reuses the power-ups'
    box 1 arithmetic, widened a pixel per side for the half-open test. `onTailAttack` is an explicit no-op and
    `isKilledByRammedObject` is false. The sprite is one quad on the card's cell at `Z_DEPTH_ITEM_REWARD`; a face
    change swaps its texture (`LevelCompletionCardImageResource`).
  - `LevelCompletionCardMotionManager` turns, hit-tests (inserts into the broadphase) and deletes the card off
    screen with the shared delete edges; `postCollision` hides a collected card. `updateWhileHalted` is the default
    no-op, as in the ROM.
  - The touch: `PlayerData.addCard`, `PlayerTimer.pause()`, `GameObjectAnimator.freeze()` on every tile animator
    (undone by the next `buildLevel`), `PowerSwitchTimeWindow.reset()`, the kill tally to 0, the player's DY to 0.
  - A paused timer stays on the status bar with its value (`PlayerTimer.isShown`, used by the HUD), as the ROM's
    stopped clock does. Before SMB3-44 the HUD hid any timer that was not counting.
  - Nothing to clear yet for the star, auto-scroll, projectiles or cannons: the port has none of them.
- **Not yet:** the walk-off (SMB3-45), the time bonus and the level end (SMB3-46), the coins (SMB3-47), the three-card
  bonus (SMB3-48), the flight and the messages (SMB3-49), and the music.
