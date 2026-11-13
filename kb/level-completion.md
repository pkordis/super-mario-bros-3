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

`src/main/resources/sprites/object/card/spin_away/{mushroom,flower,star}/frame_{0,1,2}.png` are the white pictures of
the collected card spinning away (SMB3-64): face-on, narrow and edge-on for each face.

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
    screen with the shared delete edges; `postCollision` detaches a collected card and starts its spin-away (SMB3-64).
    `updateWhileHalted` is the default no-op, as in the ROM.
  - The touch: `PlayerData.addCard`, `PlayerTimer.pause()`, `GameObjectAnimator.freeze()` on every tile animator
    (undone by the next `buildLevel`), `PowerSwitchTimeWindow.reset()`, the kill tally to 0, the player's DY to 0.
  - A paused timer stays on the status bar with its value (`PlayerTimer.isShown`, used by the HUD), as the ROM's
    stopped clock does. Before SMB3-44 the HUD hid any timer that was not counting.
  - Nothing to clear yet for the star, auto-scroll, projectiles or cannons: the port has none of them.
- **Not yet:** the walk-off (SMB3-45), the time bonus and the level end (SMB3-46), the coins (SMB3-47), the three-card
  bonus (SMB3-48), the messages (SMB3-49, since done, below), and the music. The card's spin-away is SMB3-64.

## The run-off (SMB3-45)

- **ROM:** from the touch, `EndLevelCard_PlayerRunOff` (prg002.asm:5651-5672) faces the player right
  (`Player_FlipBits = $41`) and sets `Player_EndLevel` every frame. While it is set, `Player_Control`
  (prg008.asm:1329-1349, `PRG008_A6D2`) zeroes `Player_XVel`, `Pad_Holding` and `Pad_Input` every frame, and
  `PRG029_D257` (prg029.asm:1099-1118) writes `Player_XVel = $14` (1.25 px per frame) only while grounded, so a player
  touched mid-jump drops straight down first. Gravity, collision, the walk animation, scrolling and the P-meter drain
  go on; a pit still kills; the crush check is skipped (prg008.asm:913). The exit's timers are SMB3-46. Since SMB3-49
  the port freezes the P-meter instead, as the original is seen to do (see the SMB3-49 section).
- **Port:**
  - The run-off lives in `LevelScenePlayerRunningOffCapabilities`, one of `LevelScenePlayerCapabilities`' mixins;
    `updateFrame` only calls into it. `LevelCompletion.collect` calls `startRunningOff()`:
    `PlayerRuntimeState.runningOffLevel` is set (`isRunningOff()`), the player's
    `PlayerInputHandler.ignorePlayerInput()` makes every key read as released
    and every press as used, and the facing and walk are applied at once.
  - `PlayerInputHandler` still tracks the keys, so a restart's handler takes over the real pad; R and L still publish
    their events, so R still restarts the level during the run-off.
  - `updateFrame` re-applies the walk after the movement handlers each tick (`applyRunOffWalk`: face right,
    `PLAYER_END_LEVEL_X_VELOCITY` on the ground, 0 in the air), overriding the friction a released pad would apply.
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
    detached at the touch, and SMB3-64's spin-away draws the flight along the same `CardFlight`.
  - `TimeBonus.convertStep` is one `DoTimeBonus` step. `PlayerTimer.takeOff` takes the time off without expiring
    the timer, so the paused clock shows 000.
  - The time bonus waits for the course-clear jingle to play to its end. The touch sets `courseClearPlaying`, and
    `SoundPlayComplete(COURSE_CLEAR)` clears it (`LevelCompletion.subscribe()`, called by `GameEngine.simpleInitApp`
    once per engine; SMB3-68 replaced a 255-tick stand-in). `LevelMusic` plays the jingle itself on
    `LevelCardCollected`. Each bonus step plays `CASINO_BEEP`. Tests call the package-private
    `onSoundPlayComplete` to end the jingle on the tick they choose.
  - The player's sprite X is `floor(X - screen left) & $FF`. `haltRunOff()` sets `PlayerRuntimeState.runOffHalted`,
    and `LevelScenePlayer.updateFrame` then only snapshots the position.
  - Three mixed cards go straight to `EXITING` until SMB3-48 adds state 6. A triple match takes the ordinary path for
    now (SMB3-48).
- **Not ported:** `SND_LEVELBLIP` and the music (sound). The messages are SMB3-49, below.

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
  - **Landing:** `Coins_Earned++`, and a caption with its `Scores_Y` 6 px above the coin, at its X, worth
    `$89 + Kill_Tally`, then `Kill_Tally++` (prg007.asm:4036-4060). `$09` is 1000. All the coins land on the same
    frame, so with several on screen they climb the ladder: 1000, 2000, 4000, 8000, 1UP. `Kill_Tally` is only reset
    by the player landing.
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
    prg007.asm:2767 and 3204) and the 4-frame spin, but not the flight. The block coin's flight is
    `BlockCoinPopMotion` (SMB3-65, below).
  - The coins of both kinds stand still while gameplay is halted, and every caption in flight keeps rising (before
    SMB3-47 the block coin's captions froze too).
- **Not ported:** the coin sound; cannon fire (`EndCard_Flag`) and `OBJ_WARPHIDE`, which the port does not have.

## The coin out of a block (SMB3-65)

- **ROM:**
  - **Launch** (prg000.asm:761-803, from `LATP_CoinCommon`, prg008.asm:5389-5402): `CoinPUp_Y` = the Y of the
    player's head probe that hit the block, minus `Level_VertScroll`, minus 24. `CoinPUp_X` = the block's X with
    `ORA #4`, minus `Horz_Scroll`. Both are screen coordinates. `CoinPUp_YVel = -5` (whole pixels), and
    `CoinPUp_Counter = 1`. The head probe (`Temp_Var14`) is not snapped to the tile grid, so the start depends on how
    far the head got into the block.
  - **Each update** (`CoinPUp_UpdateAndDraw`, prg007.asm:2786-2855), skipped while gameplay is halted: `Counter++`,
    `Y += YVel`, and `YVel++` when `Counter & 3 == 0`. The update that takes `YVel` to 5 removes the coin. That is
    the 39th update: -5 px for 3 updates, then -4 to 4 for 4 updates each. The spin is
    `CoinPUp_Patterns[Counter >> 2 & 3]`, with the pattern for index 1 flipped horizontally.
  - The coin is spawned in `Player_DoGameplay` and updated in `Gameplay_UpdateAndDrawMisc` in the same frame
    (prg030.asm:2444 and 2469), so its first drawn frame is already 5 px up. It is drawn on 38 frames.
  - **Removal** (prg007.asm:2857-2887): a `$85` (100) score at `Scores_Y = CoinPUp_Y` (5 if the coin is at 192 or
    lower) and `Scores_X = CoinPUp_X - 4`. Scores draw on the next frame, because `Scores_GiveAndDraw` runs before
    the coins.
  - **Scrolling** (prg007.asm:2807-2826): the coin's X and Y are screen coordinates. Taking `Level_ScrollDiffH` off
    X keeps it still in the level horizontally, and it is removed at screen X 248 or more. Y only takes off
    `Level_ScrollDiffV` while gameplay is halted, with removal at screen Y 197 or more. So during gameplay a
    vertical scroll carries the coin with the screen.
- **Capture** (`captures/input/smb3.avi`, 1-1, raccoon Mario jumping into the low ? block): the block's top is
  line 113. The coin is drawn on frames 62 to 99, its first opaque line at 100, 95, 90, 86 ... 50 (frames 80 to
  84) ... 86, matching the routine on every frame. Frame 100 is empty, and the 100 shows from frame 101 with its
  digits at line 96. The spin also matches, including which narrow frame is flipped. The coin puts the head probe
  14 px into the block, which fits Mario's head rising 3 px a frame (13 to 15 px): 100 = (113 + 14 - 24 - 5) + 1
  (the NES draws sprites a line down) + 1 (the coin's top row is clear).
- **Port:**
  - `BlockCoinPopMotion` runs the routine. `BLOCK_COIN_MOTION = spinning(blockCoin(), 4, 4, SPIN_PHASE_TICKS)`.
  - `BlockCoinPopMotionTest` checks the flight against the ROM's arithmetic. `PopMotionTest` cross-checks it against
    the capture above, with no tolerance. The fitted parabola (`ParabolicPopMotion`) and its old traced table are
    gone.
  - SMB3-66 (below) took the launch to the hitting probe, ported the screen rules and the score captions, and made
    tick `n` the position after `n` updates.

## The block coin's start, its screen rules and the score captions (SMB3-66)

- **The launch.** `CoinPUp_Y` is the Y of whichever probe found the block, less 24 (prg000.asm:788-792):
  `Temp_Var14` as `Level_DoBumpBlocks` sees it. That is the head probe for a hit from below
  (`Player_DetectSolids`), the tail's (`Player_TailAttack_HitBlocks`, `Player_Y + 28`, prg008.asm:5444-5472) and a
  kicked shell's (`Object_BumpOffBlocks`, prg008.asm:5483-5490).
  - `StaticEnvironmentCollisionGrid.bumpProbeY` records it, floored, before it dispatches `onCollisionFromBelow`
    (head) or `onTailAttack` (tail and shell). `Block.onCoinDispensed` passes it to `spawnCoinReward`.
  - The coin's sprite top is `probeY - 24 + 1`: `BLOCK_COIN_ABOVE_PROBE_PIXELS` and `SPRITE_LINE_DELAY_PIXELS`. The
    block coin flies against the background it came out of, so it keeps the NES's one-line sprite delay. Nothing
    else in the port does.
  - `BlockCoinPopMotion.verticalOffsetAt(n)` is the rise after `n` updates: 0 at the launch, 5 after the first,
    55 after updates 19 to 23, and 15 after the 39th, which removes the coin. The hit is found in the player's
    update, before the coins', so the coin gets its first update in the frame it spawns, as in the ROM. Ticks 1
    to 38 are drawn.
- **The screen rules** (prg007.asm:2807-2826). Only for the block coin: `FlyingCoin.screenBound`.
  - On an update that does not end it, a coin whose 8-bit screen X is 248 or more is removed. There is no caption
    and no 100 points, and the future from `spawnCoinReward` never completes. Off the left edge the X wraps to 255,
    so that removes it too. The 39th update tests the velocity first, so it always lands.
  - During gameplay nothing moves `CoinPUp_Y` with the scroll, so the coin moves with the screen.
    `followTheScreenVertically` moves it by the change in the camera's top since the last update.
  - While gameplay is halted it follows the scroll, so it stays put in the level. A coin whose 8-bit `CoinPUp_Y`
    is 197 or more is removed then, which is one that has flown above the screen's top.
  - The camera is `CameraState.getActiveObjectRegion`, as for every other screen test in the port. It moves once a
    rendered frame, so above 60 Hz a coin carried by a vertical scroll lags the screen by up to one tick's scroll.
- **The captions** (`Score_GiveAndDraw`, prg007.asm:2191-2267). `ScoreRisePopMotion` replaces
  `DeceleratingRisePopMotion`.
  - The score sprite is 8 x 16 and its digits are the lower 8 lines. `Score_PatternLeft` and `Score_PatternRight`
    are odd, bottom-half tiles in 8 x 16 mode. In the capture the 100's digits are at `Scores_Y + 9`: 8 lines, plus
    the sprite delay. So the offset starts at -8.
  - Each update takes 1 off `Scores_Counter` from $30 and rises 1 px when
    `Counter_1 & Score_RiseCounterMask[counter >> 4]` is 0: every frame for 16 updates, then when `Counter_1` is
    even for 16, then when it is a multiple of 4 for 16. It is drawn on its 48 updates and the 49th removes it.
    `ScorePopupAnimation.start()` reads `Counter_1` from `GameEngine.getLevelFrameCounter()` and gives the score
    its first update, because the ROM only draws a score after updating it.
  - A score does not rise while `Scores_Y < 4` (prg007.asm:2262-2266). `placeAt` works out how far it may rise
    from where it starts: `maxRises`.
  - `Scores_Y` is 5 when the ROM's value is 192 or more. That includes one above the screen, whose 8-bit value has
    wrapped.
  - Where each score goes:
    - **Objects** (`Score_PopUp`, prg000.asm:714-745): `Objects_SpriteY - 16` at the object's X.
      `ScorePopupAnimation.placeOver` uses the object's `getPosition()`, which is its `Objects_Y`. For a Koopa
      Troopa that is the body, with the head drawn above it, so the caption now sits lower than the old
      sprite-top-based one. Enemies and rewards spawn their captions in `postCollision`, before their next
      `tickScorePopups`, and `start()` gives them their first update. They are drawn on the frame of the stomp or
      the collection, as the ROM draws them: the objects update before `Scores_GiveAndDraw`.
    - **The block coin:** `Scores_Y = CoinPUp_Y` at `CoinPUp_X - 4`. The caption's sprite top is the coin's.
    - **The popped-out coin:** `SpecialObj_Y - 6` (prg007.asm:4046-4048) at the object's X. With the digits in the
      sprite's lower half, they now show 2 px below the coin's top, where they used to show 6 px above it.
    - Both coins run after `Scores_GiveAndDraw` (prg030.asm:2462 and 2469). So `CoinRewardMotionManager` holds a
      coin's caption in `pendingScores` and starts it on the next update, after the captions already rising.
  - The capture's 100 matches on all 48 frames, from line 96 on frame 101 to line 69 on frame 148
    (`PopMotionTest`). Its `Counter_1` was a multiple of 4 on its first update.
- **Not ported:**
  - The point award happens when the coin lands or the object scores. The ROM adds it on the score's 7th update,
    when `Scores_Counter` is $2A before the decrement (prg007.asm:2214-2217), so a score removed before then pays
    nothing.
  - A score's removal when its screen X or Y reaches 248 (prg007.asm:2268-2283). It is off the screen by then.
  - The caption's rise stop works from where it starts, so a vertical scroll during its 48 frames does not change
    it.
  - Outside the grow halts, the ROM holds a score still while gameplay is halted. The port keeps every caption
    rising through every halt, as before.
  - `SUB Level_VertScroll` followed by `SBC #24` subtracts 25 when the probe is above the screen's top. The port
    always subtracts 24.

## The card spins away (SMB3-64)

- **ROM:**
  - **The touch** (`ObjHit_EndLevelCard`, prg002.asm:6233-6259): `YVel = -$28`, state 4, and the frame becomes
    `EndLevelCard_Frame[face]`: 3, 4 or 5 for the Mushroom, the Flower and the Star (prg002.asm:6144). State 0
    then draws the new frame in place on the touch frame.
  - **Each frame after** (`EndLevelCard_WaitFlyAway`, prg002.asm:5630-5649): `Object_ApplyYVel`, which moves it 3,
    2, 3, 2 px up. There is no X movement, gravity, hit test or delete. When `Objects_SprVVis` says its bottom is
    above the screen's top, it goes to state 5, which never draws it again.
  - **The spin** (`EndLevelCard_AnimateSpinAway`, prg002.asm:5544-5563): sprite bank 5 becomes chr007. When
    `Counter_1 & 3 == 0`, the frame goes down by 3, and from below 3 it wraps by adding 12. Mushroom 3, 12, 9, 6;
    Flower 4, 13, 10, 7; Star 5, 14, 11, 8.
  - **The pictures** (`ObjP41`, prg002.asm:454-455): frames 3-5 are face-on (`$F7`, `$F9`, `$FB`), 6-8 and 12-14
    narrow (`$E3`, `$E5`, `$F5`), 9-11 edge-on (`$FD` for all three). The card turns face-on, narrow, edge-on,
    narrow, one picture every 4 frames. `EndLevelCard_Draw` (prg002.asm:6294-6327) draws two 8 x 16 sprites, the
    right half mirrored, with palette 1. Every one of the seven tiles uses colour index 2 alone.
  - **Halted:** halt action 11 is `EndLevelCard_Draw` (prg000.asm:4462): drawn, not moved or turned.
- **Assets:** `sprites/object/card/spin_away/<face>/frame_0.png` to `frame_2.png`, one folder per face. The
  manager loads them as `spin_away/<mushroom,flower,star>/frame_{0,2}.png`, so frames 0-8 come three per face in
  `LevelCompletionCardType` order (`AnimationImageResourceConverter` expands the `<...>` list, then the range).
  Each is the 8 x 16 tile from `CHR/chr007.pcx` (local tiles `P & $3E` and `+ 1`) with its mirror on the right.
  Index 2 is drawn (252, 252, 252), the palette-1 index-2 colour of the in-level Mushroom card
  (`level_completion/mushroom.png`, matched pixel by pixel against chr004's `$51`). Everything else is clear. Each
  face's `frame_2` is the same edge-on line.
- **Port:**
  - `LevelCompletionCardMotionManager.postCollision` detaches a collected card and calls
    `LevelCompletionCardSpinAwayMotionManager.spawn(card)`, found among the engine's motion managers. The
    spin-away starts at the card's cell with its face-on picture, so it shows on the touch tick in the card's place.
  - `LevelCompletionCardSpinAwayAnimation` follows the ROM frame by frame. On each tick that is not halted it tests
    whether its bottom is above `CameraState.getActiveObjectRegion(0).top()` before it moves. It then moves along a
    `CardFlight`, the same arithmetic `LevelCompletion` times state 4 with, so the two end on the same tick. Then it
    spins on `LevelFrameCounter & 3 == 0`. X stays at the card's column. The tick the test says it is off the top,
    it is expired and the manager detaches it.
  - `CardFlight` is now public, for the animation in the card package.
  - The manager is an `AnimationOnlyMotionManager`. `updateWhileHalted` is the default no-op, so it freezes on
    screen. `reset()` detaches it, so R, a death or the run-off restart clear it. It is never inserted into the
    active-object grid.
  - Tests: `LevelCompletionCardSpinAwayTest` (the touch, the start, the 3, 2, 3, 2 rise with a scrolling screen, the
    spin order on `Counter_1`, the removal tick, the halt, the reset and the grid) and
    `LevelCompletionCardSpinAwayPicturesTest` (white or clear, mirrored, the shared edge-on picture).
- **Not checked:** no capture of a card being collected exists in `captures/`, so the pictures and their white are
  checked against chr007 and the in-level card's palette, not against the original running.

## The messages, the new card on the status bar, the P-meter and the mute (SMB3-49)

- **ROM, the messages** (`EndLevelCard_MsgAndCard`, prg002.asm:5676-5694). State 5 starts with `Objects_Timer = $60`.
  - At timer `$1E` (66 frames into state 5) `Graphics_Queue = $2D` writes "COURSE CLEAR!" (`Video_CourseClear`,
    prg030.asm:345-349): 14 patterns at VRAM `$2889`.
  - At timer `$01` (95 frames in) `Graphics_Queue = $2E` writes "YOU GOT A CARD" and its box (`Video_YouGotCard`,
    prg030.asm:351-370): the words and the box's middle row at `$28E7` (19 patterns), the top border at `$28D6`,
    the second middle row at `$2916`, the bottom border at `$2936`. In the same frame `EndLevelCard_DrawCardT2`
    sets `Objects_Timer2 = $30`, and `EndLevelCard_DrawCard` (prg002.asm:5957-6002) puts the card's four patterns
    into the box at `$28F7` and `$2917` (`EndLevelCard_CardBackLower`).
  - `EndLevelCard_CardFace`'s comments pair the rows wrongly. Read with the routine's indexing (`CardFace - 4 + Y`
    and `CardFace + 8 + Y`, `Y = frame * 4 + 1`, two passes of `Y += 2`), the faces are `$E0 $E1 / $E2 $E3`,
    `$E4 $E6 / $E7 $E8` and `$AC $AD / $AE $AF`: exactly the status bar's `CardUL`/`CardUR`/`CardLL`/`CardLR`.
  - The patterns are background tiles in nametable `$2800`. The level's second background window is switched to
    `$5E` as state 5 starts (prg002.asm:5645-5646), the status bar's own bank, so `$80-$BF` are chr094 and
    `$C0-$FF` chr095 (§9 of `sprites_extraction.md`). Palette: background sub-palette 0, which is `$0F $30 $3C` in
    every tileset (`PalSet_*`, prg027.asm): index 1 black, 2 white, 3 the status bar's cyan. No index 0 is used, so
    each written cell is opaque in the game: the letters sit on black. This project draws that black background
    transparent (the font cells and the area outside the box's white border), so the level shows through.
  - **Screen position.** A horizontal level's nametables are stacked (`$2000` above `$2800`), and a level scrolled to
    its bottom shows `$2800` row 0 at its top line, with no horizontal offset at the level's right end. So row `r`,
    column `c` of `$2800` is at view `(8c, 8r)`: "COURSE CLEAR!" at (72, 32), "YOU GOT A CARD" at (56, 56), the box
    from (176, 48) to (207, 79), the card at (184, 56).
- **ROM, the new card on the status bar.** The touch gives the card with `Player_GetCard` (prg002.asm:6253), which
  does not redraw the status bar (`Player_GetCardAndUpdate`, prg031.asm:2776, would). The status bar's card slots
  are only redrawn by `StatusBar_DrawCardPiece`, and in state 5 only `EndLevelCard_DrawFlashing`
  (prg002.asm:6056-6093) calls it, once `Objects_Timer` is 0 (prg002.asm:5748-5752), and again in state 7
  (prg002.asm:5835). So the new card's slot stays empty until 96 frames into state 5. `DrawFlashing` draws the card
  while `Objects_Timer2` is non-zero, then leaves the slot empty on the frames where `Counter_1 & $10` is 0. The
  newest card is the last non-empty slot (`EndLevelCard_SetFrameToCard`).
- **Observed in the original, not derived from dasm.** With a full P-meter, the ringing stops on the touch, every
  sound but the course-clear music is muted, and the [P] keeps flashing until the level ends. dasm alone suggests
  otherwise: `Player_PowerUpdate` (prg008.asm:981-1060) runs every frame, halted or not, so the run-off would drain
  the meter, and player sounds take square 1 over the music (`Music_Sq1NoteOn`, prg031.asm:719-723). The port
  follows the observation.
- **Port:**
  - `LevelCompletion.getMessage()` is `Message.NONE`, `COURSE_CLEAR` or `YOU_GOT_A_CARD`, set in `tickCourseClear`
    on `phaseTimer` 30 and 1 (`COURSE_CLEAR_MESSAGE_TICK`, `YOU_GOT_A_CARD_TICK`), and kept until `reset()`.
  - `LevelCompletionMessageOverlayState` draws it: a post view over the game viewport, created after the
    foremost-sprite overlay and before the fade, with one unit quad textured with the 256 x 192 picture
    `LevelCompletionMessageRenderer` writes, repainted only when the message or the card changes. It is fixed to
    the view, which matches the ROM's place for a level ending at its bottom.
  - **Font-driven.** Each `Message` carries its text (`"COURSE CLEAR !"`, with the ROM's blank before the "!", and
    `"YOU GOT A CARD"`), and the renderer writes it one 8 x 8 cell per character with the bold font
    (`BoldFontGlyph`), from cell (9, 4) and (7, 7). Blank cells fill the gap up to the box, a single 32 x 32
    picture at cell (22, 6). Changing a message is changing its text.
  - **The font's limit.** In this white-on-black style the bank only holds the letters the two messages use:
    A C D E G L O R S T U Y, "!" and the blank (`$80-$9B`, `$FC`). A character outside them is refused with an
    `IllegalArgumentException`. chr094 and chr095 also hold a full alphabet (A-P at `$B0-$BF`, Q-Z and q-v at
    `$C0-$CF`, a-p at `$D0-$DF`; W, X, Y, Z also at `$81`, `$88`, `$8C`, `$8F`), but drawn in index 0 on index 3: with
    background sub-palette 0 that is backdrop-coloured letters on cyan, a different look. Using it for other texts
    would need its own colours (an asset decision, not a ROM one). That alphabet is the dialogue font, below.
  - Assets:
    - `font/bold/<glyph>.png`: the font's 14 characters, rendered from chr094/chr095 with the colours above. Fonts
      are named by their look, not their use, and live under `src/main/resources/font/<font>/`, one folder per
      font. The dialogue font (Toad and King, the princess's letter, the Toad houses; mixed case at `$B0-$EA`, its
      own palette) goes into its own folder when it is ported. If more letters of this style turn up (the in-air
      variant's bank for the triple match, SMB3-48), they join `font/bold/`.
    - `sprites/object/card/message/box.png`: the card's box, one 32 x 32 picture composed from its border patterns
      (`$A0-$A8`) and its inside (`$FE`). It is a sprite, not part of the font.
    - The card in the box is the status bar's `hud/card/<face>.png`, which matches the box's patterns pixel for
      pixel.
    - The two whole pictures the first version drew are now test fixtures
      (`src/test/resources/.../expected_course_clear.png`, `expected_you_got_a_card.png`):
      `LevelCompletionMessageRendererTest` checks that the font and the box reproduce them pixel for pixel.
  - The text writer (`LevelCompletionMessageRenderer.writeText` and `glyphFor`: character to cell, cells left to
    right) is deliberately not shared yet. Pull it out into a font-agnostic writer when the dialogue port gives it a
    second user, whose needs (typed out a character a tick, line breaks in a box, its own palette) should shape that
    API.
  - `LevelCompletion.isNewestCardHidden()` is true from the touch until state 5's timer is 0 (`newestCardRevealed`),
    and after that, once `statusBarCardHold` (`Objects_Timer2`, `STATUS_BAR_CARD_HOLD_TICKS` = 48 from the message
    tick, decremented with `phaseTimer`) is 0, on frames where `LevelFrameCounter & CARD_FLASH_BIT` is 0. So the card
    shows 96 ticks into state 5 and is drawn for the 47 ticks after that before it starts to flash. `HeadsUpDisplay`
    reads it every frame and re-renders on a change; `HeadsUpDisplayRenderer.renderCards` leaves the newest card's
    slot empty when `isNewestCardHidden()`. The card stays in `PlayerData` throughout.
  - The P-meter: `LevelScenePlayerDataAware.handlePowerMeterAndRunFlag` does not charge or drain while
    `PlayerRuntimeState.isRunningOffLevel()`, and `handleFlyTimeCountdown` lets a flight run out without emptying
    the meter. No `PlayerPowerLost` is published, so the HUD's [P] keeps flashing. The next fresh start empties it.
  - The mute: `LevelCompletion.collect` calls `SoundPlayer.muteEffectsExcept(CASINO_BEEP)` before the sweep, so the
    popped-out coins are silent too; `GameEngine.buildLevel` unmutes (see `sound.md`).
- **Not ported:** the "in the air" variant (`Video_YouGotCardH` at `$22C7` and the card patterns below `$80`, chosen
  by `Vert_Scroll`), used by the triple-match display (SMB3-48). A level that ends higher up than its bottom shows
  the messages at the same place on the view, where the ROM would put them at their nametable place.
- **Not checked:** no capture of a level end exists in `captures/`, so the place and colours come from dasm alone.
