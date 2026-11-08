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
    screen with the shared delete edges; `postCollision` detaches a collected card. `updateWhileHalted` is the default
    no-op, as in the ROM.
  - The touch: `PlayerData.addCard`, `PlayerTimer.pause()`, `GameObjectAnimator.freeze()` on every tile animator
    (undone by the next `buildLevel`), `PowerSwitchTimeWindow.reset()`, the kill tally to 0, the player's DY to 0.
  - A paused timer stays on the status bar with its value (`PlayerTimer.isShown`, used by the HUD), as the ROM's
    stopped clock does. Before SMB3-44 the HUD hid any timer that was not counting.
  - Nothing to clear yet for the star, auto-scroll, projectiles or cannons: the port has none of them.
- **Not yet:** the walk-off (SMB3-45), the time bonus and the level end (SMB3-46), the coins (SMB3-47), the three-card
  bonus (SMB3-48), the messages (SMB3-49), the card's spin-away (SMB3-64), and the music.

## The run-off (SMB3-45)

- **ROM:** from the touch, `EndLevelCard_PlayerRunOff` (prg002.asm:5651-5672) faces the player right
  (`Player_FlipBits = $41`) and sets `Player_EndLevel` every frame. While it is set, `Player_Control`
  (prg008.asm:1329-1349, `PRG008_A6D2`) zeroes `Player_XVel`, `Pad_Holding` and `Pad_Input` every frame, and
  `PRG029_D257` (prg029.asm:1099-1118) writes `Player_XVel = $14` (1.25 px per frame) only while grounded, so a player
  touched mid-jump drops straight down first. Gravity, collision, the walk animation, scrolling and the P-meter drain
  go on; a pit still kills; the crush check is skipped (prg008.asm:913). The exit's timers are SMB3-46.
- **Port:**
  - The run-off lives in `LevelScenePlayerRunningOffCapabilities`, one of `LevelScenePlayerCapabilities`' mixins;
    `updateFrame` only calls into it. `LevelCompletion.collect` calls `startRunningOff()`:
    `PlayerRuntimeState.runningOffLevel` is set (`isRunningOff()`), the player's
    `PlayerInputHandler.ignorePlayerInput()` makes every key read as released
    and every press as used, and the facing and walk are applied at once.
  - `PlayerInputHandler` still tracks the keys, so a restart's handler takes over the real pad; R and L still publish
    their events, so R still restarts the level during the run-off.
  - `updateFrame` re-applies the walk after the movement handlers each tick (`applyRunOffWalk`: face right,
    `PLAYER_END_LEVEL_XVEL` on the ground, 0 in the air), overriding the friction a released pad would apply.
  - `StaticEnvironmentCollisionGrid.collidesAtOffset` opens the level's right edge to a running-off player; the left
    edge stays solid.
  - Once the step would put the left foot (X + 4, both suits) past the last column there is nothing to stand on:
    `tickBeyondTheLevel` walks the player on at its height, with no gravity, collision or pit check, so it never
    "falls" off the end of the level.
  - Until SMB3-46, `LevelCompletion` published `LevelCompleted` once the player's X passed the camera's right edge.
    SMB3-46 replaced that with the ROM's exit (below). `TestSessionEventListener` turns `LevelCompleted` into
    `requestLevelSceneRestart()`, the R path. A player who dies on the way never completes the level.
  - A carried object is let go because `isCarryButtonHeld()` reads B through the handler.
- **Not ported:** hiding the player's sprites once off screen (`EndLevelCard_ClearPlayerIfOff`); the port's player is
  simply outside the view by then. No Kuribo's shoe, no crush check to skip.

## The time bonus and the level end (SMB3-46)

- **ROM**, the non-triple-match path of `ObjNorm_EndLevelCard` (prg002.asm:5367-5385):
  - **State 4**, `EndLevelCard_WaitFlyAway` (prg002.asm:5630-5649): the card rises at `YVel = -$28` (3, 2, 3, 2 px a
    frame from a zero 4.4 fraction) until `Objects_SprVVis` says it is off the top of the screen: its bottom (Y + 16)
    above `Level_VertScroll` (`Object_DetermineVertVisY`, prg000.asm:5132-5180). The flags are worked out before the
    frame's move, so the state changes one frame after the card has gone. State 5 then starts with
    `Objects_Timer = $60`.
  - **State 5**, `EndLevelCard_MsgAndCard` (prg002.asm:5676-5755): `DoTimeBonus` (prg000.asm:603-651) every frame.
    While music plays (`SndCur_Music1`, here `MUS1_COURSECLEAR` from the touch) it does nothing and reports "not
    done". Otherwise a step takes 10 off and scores 500 while the hundreds or tens digit is non-zero, else 1 off and
    50 (`TimeBonus_Score`), and plays `SND_LEVELBLIP`; at 000 it reports "done". The state ends when the bonus is
    done, the timer is 0, and `Player_SpriteX / 2 == $10`. Then state 7 with `Objects_Timer = $80`, or with three
    mixed cards state 6, the 1UP display (SMB3-48).
  - **State 7**, `EndLevelCard_Exit` (prg002.asm:5808-5829): at timer 0 the level is left, and three held cards are
    cleared.
  - `Objects_Timer` goes down once per frame that is not halted, before the object runs (prg000.asm:1849-1855).
  - **The halt at the exit.** In states 4 and 5, `EndLevelCard_PlayerRunOff` (prg002.asm:5651-5671) sets
    `Player_HaltTick` when `Player_SpriteX / 2 == $10`, and that skips the player's whole update (prg008.asm:834-836).
    The 8-bit sprite X has wrapped round by then: the player has walked off the right of the screen and stops 32 px
    past it, out of sight. The check does not know about the wrap, so a player touching the card in the screen's
    leftmost 32 px would stop on screen at X 32. The port keeps that.
- **Port:**
  - `LevelCompletion` runs the states as `Phase` (`CARD_FLYING_OFF`, `COURSE_CLEAR`, `EXITING`, `COMPLETED`), from
    `tick()` on each un-halted tick after the players move, with `phaseTimer` as `Objects_Timer`.
  - `CardFlight` times state 4 from the card's cell (the spawner's, read before it is retired). The card itself is
    still detached at the touch; SMB3-64 draws the flight.
  - `TimeBonus.convertStep` is one `DoTimeBonus` step. `PlayerTimer.takeOff` takes the time off without expiring
    the timer, so the paused clock shows 000.
  - `CourseClearMusicStandIn` stands in for `MUS1_COURSECLEAR` with `COURSE_CLEAR_MUSIC_TICKS = 0`: no music yet, so
    the bonus starts as soon as the card is off screen. The package-private `LevelCompletion` constructor takes a
    longer one for tests.
  - The player's sprite X is `floor(X - screen left) & $FF`. `haltRunOff()` sets `PlayerRuntimeState.runOffHalted`,
    and `LevelScenePlayer.updateFrame` then only snapshots the position.
  - Three mixed cards go straight to `EXITING` until SMB3-48 adds state 6. A triple match takes the ordinary path for
    now (SMB3-48).
- **Not ported:** `SND_LEVELBLIP` and the music (sound), the messages (SMB3-49).

## The objects turn into coins (SMB3-47)

- **ROM**, `ObjHit_EndLevelCard` (prg002.asm:6146-6226):
  - Every special-object slot is cleared (prg002.asm:6158-6163). In the port's terms: the piranha fireballs
    (`SOBJ_PIRANHAFIREBALL`) and the switch-block puffs (`SOBJ_POOF`). Block coins (`CoinPUp_*`) and brick debris
    (`BrickBust`) are not special objects and are left alone; `SOBJ_BRICKDEBRIS` is only the giant-world brick and
    the Piledriver.
  - Then object slots 4 to 0 (the card always spawns into slot 6, prg005.asm:5097-5107), except `OBJ_WARPHIDE`.
    Whatever the slot's state (walking, held, kicked, already dying), an object **on screen** gets a
    `SOBJ_POPPEDOUTCOIN` at its X and Y with `YVel -$28`, `XVel 0`, goes into `OBJSTATE_POOFDEATH`, and the coin
    sound plays. One off screen is deleted.
  - **On screen** is `Object_AnySprOffscreen` after `Object_DetermineHorzVisY` / `Object_DetermineVertVisY` with
    `Y = 0`: whatever the object's size, only a 16 x 16 box at its X and Y counts. X must be within the 256 px
    screen, and Y + 16 within the 240 lines below `Level_VertScroll` (prg000.asm:5071-5180). An object hanging off
    the right edge still counts; one a pixel off the left does not.
  - **The coin** (`SObj_CoinOrDebris`, prg007.asm:3413-3521): each un-halted frame Y += YVel (4.4, from a zero
    fraction), then YVel += 2 (`SObj_ApplyXYVelsWithGravity`, prg007.asm:3903-3920). It is removed on the frame
    YVel reaches `$20`: 36 frames, peaking 27 px up and ending 12 px above its start. Drawn 4 px right of the X,
    spinning through the block coin's frames every 4 frames (`Var1 >> 2 & 3`).
  - **Landing:** `Coins_Earned++`, and a caption 6 px above the coin at its X worth `$89 + Kill_Tally`, then
    `Kill_Tally++` (prg007.asm:4036-4060). `$09` is 1000. All the coins land on the same frame, so with several on
    screen they climb the ladder: 1000, 2000, 4000, 8000, 1UP. `Kill_Tally` is only reset by the player landing.
- **Port:**
  - `LevelCompletion.clearTheObjects`, at the end of `collect`, sweeps every `ActiveLevelObjectMotionManager`
    except the card's. Placed enemies not yet spawned into the scene and rewards collected this tick are in no ROM
    slot and are skipped. Each swept object is expired, detached and removed, an enemy's placement is retired so it
    does not come back this run, and a held one is cleared from the player's `carriedObject`.
  - On screen (`LevelCompletion.isOnScreen`): `PoofMotionManager.spawnPoofDeath` over its top-left and
    `CoinRewardMotionManager.spawnPoppedOutCoin`.
  - `PiranhaFireballMotionManager.removeAll` and `PoofMotionManager.removeSwitchPuffs` clear the special objects.
    Poof deaths already playing are object states and are left to finish.
  - The sweep runs inside the collision pass, so `ActiveObjectGrid` now skips expired objects in the body and tail
    passes (`ActiveLevelObject.isExpired`, default false): in the ROM the card in slot 6 runs before slots 4 to 0,
    which by then are poofing.
  - `PoppedOutCoinPopMotion` is the flight. The sprite and the bookkeeping are the block coin's: `CoinPopAnimation`
    is given its flight, and `CoinRewardMotionManager.spawnPoppedOutCoin` places the coin and lands it with
    `PlayerData.addCoin()`, then `KILL_TALLY_LADDER[min(4 + nextKillTally(), 8)]` from the touching player's runtime
    state, with the caption.
  - The two coins share the ROM's sprite patterns (`CoinPUp_Patterns` and `PUpCoin_Patterns` hold the same bytes,
    prg007.asm:2767 and 3204) and the 4-frame spin, but not the flight. The block coin (`CoinPUp_UpdateAndDraw`,
    prg007.asm:2786-2887) moves in whole pixels from `YVel = -5`, adds 1 every 4th frame and lands at 5, 24 px above
    the block (prg000.asm:779-803). The port's `BLOCK_COIN_MOTION` is still the parabola fitted to a capture
    (`PopMotionTest`), not this routine.
  - The coins of both kinds stand still while gameplay is halted, and every caption in flight keeps rising (before
    SMB3-47 the block coin's captions froze too).
- **Not ported:** the coin sound; cannon fire (`EndCard_Flag`) and `OBJ_WARPHIDE`, which the port does not have.
