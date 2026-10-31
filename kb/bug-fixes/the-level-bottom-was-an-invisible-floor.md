# The level's bottom was an invisible floor (SMB3-14)

**Reported as:** wherever the bottom row has no solid tile (a pit), the player, enemies and power-ups walk
across it as if it were ground. Nothing ever falls into a pit, and the player cannot die in one.

## What was actually wrong

Both of the collision grid's solidity paths treated every row below the level as solid:

- `collidesAtOffset` (the player's probes) returned `true` for `ty >= rows`. A player whose feet reached
  the row below the level was snapped and stopped, so it stood on nothing at `Y = rows * 16 - 32`.
- `isGroundSolidTile` and `isWallOrCeilingSolidTile` (every object) returned `row >= 0` outside the
  level, which is `true` below it. Goombas, troopas, shells and mushrooms walked across pits.

The only player death was the hurt throe, so there was also no pit death to reach.

## What the ROM does

- **No tile below the level.** `Player_GetTileAndSlope` returns before fetching a tile once the probe is
  at or past `$1B0` (prg008 @ PRG008_B406-B414); the object probe jumps to "no tile" for the same Y
  (prg000 @ PRG000_C7F1). Tile `$00` is solid in no tileset.
- **Pit check** (prg008 @ PRG008_A3FA, lines 833-865): skipped while dying, halted, after the wand grab, or
  while `Player_AboveTop`. Otherwise, once `Player_SpriteY & $F0 = $C0` (the sprite's top on screen line
  192, the bottom of the playfield), the suit is set to small, `Player_Die` runs, and then
  `Event_Countdown = $C0` and `Player_IsDying = 2` "jump the initial part of the death sequence".
- **`Player_Die_FellOff`** (prg029) moves and draws nothing; on the frame `Event_Countdown` reads 0 it
  sets `Level_ExitToMap`. `$C0` frames is how long the death music plays.
- **Objects** below the level are deleted at `Objects_YHi >= 2` (`Object_DeleteOffScreen`), already ported
  in SMB3-3 as `hasFallenBelowTheLevel()`.

## The fix

- **Grid:** below the level is open on both paths. The sides keep their SMB3-1 rules: solid to the
  player at any height, open to objects.
- **Player line:** `CameraState.isBelowVisibleBottom(spriteTop)`, true at `max(level bottom, view bottom)`.
  With the camera at the bottom of a tall level both are the ROM's screen line 192; the level bottom wins
  while the camera is scrolled up, and the view bottom wins in a level shorter than the view. Checked at
  the end of `updateFrame`'s normal path.
- **Death kind:** `PlayerRuntimeState` holds a `PlayerDeathKind` (`NONE`, `THROE`, `FELL_OFF`) in place of
  the `dying` boolean. `isDying()` is unchanged for every caller.
- **`dieByFallingOff()`:** ignores `isHurtable()` (a flashing or big player dies too), clears flight, wag,
  tail swing and the flash, stops the player, and announces `PlayerDeathStarted(FELL_OFF)`. It does
  nothing to a player who is already dying. `die()` gained the same guard and announces
  `PlayerDeathStarted(THROE)`.
- **`tickDeath()`:** a `FELL_OFF` death only holds still and hides the node; the throe is untouched.
- **End of the death:** `DeathMusicStandIn` counts `PLAYER_PIT_DEATH_COUNTDOWN_TICKS = $C0` from
  `PlayerDeathStarted(FELL_OFF)` and publishes `PlayerDeathMusicFinished` on the 192nd tick after the death
  began; `PlayerDeathSequence` turns that into one `PlayerDied`, which restarts the level as before.
  The stand-in is to be deleted once a sound engine publishes the event.

## Deliberate differences from the ticket

- **Objects keep the ROM's line.** The ticket asked for one out-of-view rule shared by the player and the
  objects. The ROM has two (screen line 192 for the player, `Objects_YHi >= 2` for objects), and SMB3-3
  had already centralised the object rule in `hasFallenBelowTheLevel()`, so only the player got the new
  predicate. An object between the level's bottom and Y 512 is below the view either way.
- **The suit is not reset to small** in `dieByFallingOff()`: the restart respawns the player small.
- **Objects are not stopped at the level's sides.** That acceptance criterion predates SMB3-1, which opened
  the sides to objects as the ROM does.

## Tests

- `CollisionGridLevelBottomTest`: the three queries below the level, the sides still solid to the player,
  a grounded player over a pit falls, a falling one keeps falling, the bottom row's real tiles still hold.
- `PitFallTest` (real grid): a Goomba, a Para-Goomba, a green troopa, a kicked shell, a Super Mushroom and a
  1-Up all drop through a pit; a red troopa still turns at its edge.
- `CameraStateVisibleBottomTest`: the line for a tall level, a scrolled-up camera, a short centred level,
  and nothing above the view.
- `PlayerPitDeathTest`: every suit, flashing, carrying and flying, already dying, and the hurt death
  unchanged.
- `PlayerPitFallTest` (real `LevelScenePlayer` over a real grid): falls and dies in each suit, then holds
  still, undrawn, until `PlayerDied` arrives once, 192 ticks later; a player on the bottom row is safe.
- `PlayerDeathEventChainTest`: the 192-tick count, one `PlayerDied`, any publisher of the music's end, the
  throe ignored, the restart and R reset disarming it, and a second engine after the bus is purged.

Mutation-checked: putting back the solid bottom, measuring from the view alone or the level alone,
publishing a tick early, ignoring the death kind, dropping the already-dying guard and drawing the pit
death each fail the tests.

Verified by unit tests only; not checked in the running game.
