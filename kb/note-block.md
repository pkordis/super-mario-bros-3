# Note block (SMB3-82)

The plain note block (`BlockType.NOTE_BLOCK_NO_REWARD`, dasm `TILEA_NOTE` = $2E). It bounces up when struck from
below or from the side, as often as it is struck, and never breaks, empties or turns into a coin. A player landing on
it sinks with it and is launched back up; a jump pressed during the ride doubles the launch.

SMB3-82 also aligned two things every bouncing block shares with the original: the bounce timeline (§2) and the push
a player gets from a block they bounced (§1).

Out of scope (SMB3-82 decisions): the item note blocks (`TILEA_NOTEFLOWER`/`LEAF`/`STAR`), the invisible note block,
the coin-heaven launcher, the green note block (`TILEA_GNOTE`), and the two-bounce limit of §6.

Values are px/frame; positive DY is down.

---

## 1. Behaviour

### Struck from below or from the side

- Head hit: the block bounces **up** (`BlockBounceAnimation`, §2) and the bump sound is the head hit's own `BLOCK`
  (`LevelScenePlayer`, on `isHitCeiling()`). Small and big players alike: no reward, no break, no `EmptyBlock`.
- **The push.** Every block that bounces because the player's head or tail struck it pushes the player:
  `LevelScenePlayerMoveCapable.bounceOffBumpedBlock()` sets DY = **+2.0** and takes a grounded player off the ground.
  That is the note block, any reward block (? blocks, reward bricks, switch spawners) and a brick a small player bumps
  (`BreakableBrickCapabilities.hitBrickFromBelow`, which a P-Switch coin also uses). A brick that breaks pushes nobody,
  and a plain solid ceiling still gives the ceiling's 1/16. On the ground, a tail's push is a 2 px drop that lands on
  the next tick.
- **Kicked objects** have their own dispatch: `StaticEnvironmentCollisionGrid.resolveKickedObjectStrike` →
  `LevelObject.onKickedObjectStrike`, which defaults to `onTailAttack`. `Block` answers both through
  `strikeFromTheSide`, and only the tail adds the push. The shell turns around because it hit a wall.
- Tail and shell bounces of the note block play `BLOCK` (a shell plays `BLOCK` itself as well; playing it twice on one
  tick only restarts it).
- While it bounces up, `isBlockBumpActiveAt` is true for its cell, so Koopa Troopas, Para-Troopas and mushrooms on
  top are knocked exactly as by a ? block: Goombas and Para-Goombas die, Koopa Troopas (kicked shells too) flip
  into a shell, the hopping Para-Troopa loses its wings, mushrooms hop, and a player standing on it is thrown up.
  See `kb/dynamic-entities.md`, "Who reacts to a block bump".
- It stays a note block during a P-Switch: `Block.isCoinSubstituted()` is true only for `BRICK_BLOCK_NO_REWARD`.

### Landed on (the ride)

- `Block.onCollisionFromAbove` → `NoteBlockBounceMotionManager.spawnBounceDown`: the player is marked riding and
  airborne (`fall()`), `BLOCK` plays. The ride and launch follow §2.
- **Boost window:** a *new* jump press on the player's ticks L+1 … L+10 (10 ticks, 167 ms). A press on tick L is
  handled before the landing, so it is an ordinary mid-air press; from tick L+11 a press is ordinary again (the ride
  ends when the block is back in place, a tick before the launch). Holding the button never counts.
  `LevelScenePlayerMoveCapable.handleVerticalMovement` turns the press into `PlayerRuntimeState.noteBlockBoosted` and
  does not jump or wag the tail with it.
- **Heavy rise:** the launch sets `PlayerRuntimeState.slowGravityBlocked`; while it is set the jump button does not
  give the slow gravity, and it is cleared on the first airborne tick with DY ≥ −2. Rise without the button's help:
  about 21 px for −3.5, about 82 px (5 tiles) for −7.0.
- X velocity and the P-meter are untouched.
- **Passing into the block:** while it sinks, `Block.isPassableForPlayers()` is true, and
  `StaticEnvironmentCollisionGrid.isSolidPixelForPlayer` treats the cell as empty for players only. Enemies and power-ups
  still see solid ground (`isGroundSolidTile` / `isWallOrCeilingSolidTile` read `isCollidable()` only). The original
  turns the cell into sky for everyone (§5); the erased tile already shows the AIR layer, so only collision needed a
  stand-in.
- One bounce per block: a landing or strike while the block bounces does nothing, and a player already riding one note
  block does not start a second (`Block.onCollisionFromAbove` guards; §6 has what the original does instead).
- Sides: a plain wall.
- Restarting the level (`reset()`) drops the bounces and ends any ride without a launch.

## 2. Bounce timeline

Shared by every `BlockBounceAnimation` (brick, empty block, note block). Tick 1 is the motion managers' update on
the engine step of the hit, L; the player's update runs before the managers on every step, as in the original. Heights
are for a bounce up; a sinking note block runs them negated.

| Tick | Step | Block height (px) | Rider's DY set | Original |
|---|---|---|---|---|
| 1 | L | 0 | 0 | `ObjInit_BounceDU` |
| 2 | L+1 | 0 | +4 | position 10 |
| 3 … 10 | L+2 … L+9 | 4, 7, 9, 10, 10, 9, 7, 4 | +3, +2, +1, 0, −1, −2, −3, −4 | positions 9 … 2 |
| 11 | L+10 | 0, back in place; the ride ends | −4 | position 1 |
| 12 | L+11 | the bounce ends, the tile is restored | launch: −3.5, or −7.0 boosted | position 0 |

- The player moves on step L+k by the DY set on tick k, the block on tick k+1 by the same amount, so the rider stays on
  the block and ends 4 px above it before the launch (as in the original).
- **Knocking** lasts all of steps L … L+11: a bounce that ends on tick 12 stays listed until the managers'
  `postCollision`, so `isBlockBumpActiveAt` answers the same whatever order the managers update in. In the original the
  grid holds `TILEA_BLOCKBUMP_CLEAR` from the hit until the restore at the end of step L+11, and the enemies (object
  slots 0-4) always run after the bounce objects (slots 6 and 7).
- Before SMB3-82 the bounce had no still first tick and ended on its tenth, two steps short.

## 3. Graphics

- Idle: `NoteBlockAnimator`, `sprites/object/block/note/frame_{0,3}.png`, 8 ticks per frame. Three distinct images
  played A-B-C-B, so `frame_3.png` is a copy of `frame_1.png` (the P-Switch's arrangement, `kb/sprites_extraction.md`
  §8).
- While a bounce shows, the tile is erased and the moving block is drawn with `frame_1.png`, still. The animator is
  paused for the cell (`pauseAt` / `resumeAt`).
- During a P-Switch: `NoteBlockAnimator.getFramePixels` answers frame 0 for every frame, and the animator repaints on
  the window's open/close edge, also on halted ticks (same scheme as `BrickBlockAnimator`).

## 4. Implementation map

| Concern | Where |
|---|---|
| Hits, landing, passability, animator choice | `game/object/level/block/Block` (`isNoteBlock()` branches, `strikeFromTheSide`, `getAnimator()` switch) |
| Bounce runner, ride, launch | `game/object/level/block/motion/NoteBlockBounceMotionManager` |
| Bounce timeline | `BlockBounceAnimation.tick()`, `isBackInPlace()`; `isSinking()` flips the curve |
| Bounce sprite | `game/object/level/block/animation/NoteBlockBounceAnimation` |
| Idle frames, P-Switch freeze | `game/object/level/block/animator/NoteBlockAnimator` |
| Pass-through for players | `LevelObject.isPassableForPlayers()` (default `false`, so Mockito mocks of `LevelObject` stay solid) |
| Push off a bounced block | `LevelScenePlayerMoveCapable.bounceOffBumpedBlock()`, `GameConstants.PLAYER_BLOCK_BUMP_Y_VELOCITY` |
| Kicked-object strike | `StaticEnvironmentCollisionGrid.resolveKickedObjectStrike`, `LevelObject.onKickedObjectStrike` |
| Boost press, heavy rise | `LevelScenePlayerMoveCapable.handleVerticalMovement`; flags on `PlayerRuntimeState` |

Tests: `NoteBlockTest`, `NoteBlockBounceMotionManagerTest`, `NoteBlockAnimatorTest`, `NoteBlockRideMovementTest`,
`BlockBumpPushTest`, `BlockBounceAnimationTest`, `CollisionGridPassableTileTest`, `CollisionGridTailAttackTest`.

## 5. dasm findings

- **Step order** — `Player_DoGameplay` (prg030:2444) then `Objects_HandleScrollAndUpdate` (prg030:2455), which runs
  `Level_SpawnObjsAndBounce` (prg000:1835) — turning `Player_Bounce` into an `OBJ_BOUNCEDOWNUP` in slot 6 or 7
  (prg005:4843-4890) — then the object loop from slot 7 down to 0 (prg000:1840-1925), decrementing each slot's
  timers before its handler. After the loop a queued block bump becomes `Level_ChgTileEvent` (prg000:1952-1986), and
  `BlockChange_Do` (prg030:2502, prg029:2398) writes the grid tile at the end of the same step.
- **Detection** — `Level_DoBumpBlocks` (prg008:4936). `Level_ActionTiles` lists `TILEA_NOTE` among the tiles
  "activated anytime", with `Level_ActionTiles_HitEnable` %0011 (prg008:4918): top and bottom detects (and the tail /
  kicked-shell slot, whose hit bit is %1111), never the side detects. Power-up `LATP_None` (prg008:5151) returns
  `Bouncer_PUp[1]` = 0: nothing comes out. Sound `SND_PLAYERBUMP` (prg008:5000) on every bounce.
- **Every bump block uses the same bounce object.** `LATP_Coin` and a small player's `LATP_Brick` return 1, item
  blocks their item, so all of them set `Player_Bounce` and spawn `OBJ_BOUNCEDOWNUP`; only a busted brick ($80) does
  not (prg008:5180-5330). That is why the push and the timeline apply to every bouncing block.
- **Direction** — `Player_BounceDir` (prg008:5039): 0 (down) when the player was not rising, 1 (up) when rising; the
  tail (prg008:5444) and `Object_BumpOffBlocks` (prg008:5483) force 1, and the shell also sets `Player_BounceObj`.
- **The tile during the bounce** — `BlockBump_Init` queues `CHNGTILE_DELETETOBG`, which `OneTile_ChangeToTile`
  (prg029:2541) maps to `TILE1_SKY`, written at the end of step L. For a bounce up the grid was set to
  `TILEA_BLOCKBUMP_CLEAR` ($F3) at the hit (prg008:5069) and is set again at position 10 (prg001:1193), so it reads
  CLEAR to every object handler; `Object_HandleBumpUnderneath` (prg000:3602) looks for it, which is why only an upward
  bounce knocks enemies. A sinking note block's cell is sky for everyone from the end of step L. At position 0
  `Bounce_TileReplacements[4]` = `CHNGTILE_TONOTEBLOCK` (prg001:1018) puts `TILEA_NOTE` back, patterns
  `$B8,$BA,$B9,$BB`.
- **Bounce object** — `ObjInit_BounceDU` (prg001:975): for a non-zero direction not caused by a shell,
  `Player_YVel` = $20 (+2.0); for any direction not caused by a shell, the player is marked airborne. `ObjNorm_BounceDU`
  (prg001:1032): position counter 10 → 0; each tick applies the Y velocity, then loads `Bouncer_PUpVel[pos]`
  (prg001:1029: 0, −$40, −$40, −$30, −$20, −$10, 0, $10, $20, $30, $40), negated when bouncing up. Bouncing down it
  also stores that value into `Player_YVel` — the ride — and sets `Objects_Timer2` = $0B whenever `Pad_Input & PAD_A`
  (prg001:1219, the newly-pressed edge). At position 0 (prg001:1081-1089) the object dies (it is not drawn that step),
  and a sinking one gives the player `Player_YVel` = −$38 (−3.5) and `Player_mGoomba`, or −$70 (−7.0) if `Timer2` is
  still running. The coin-heaven check that follows needs a different note block kind.
- **Heavy rise** — `Player_mGoomba` (named for the micro-Goomba, prg008:2654-2665): while `Player_YVel` < −$20 and the
  flag is set, gravity is the heavy +5 even with A held; it is cleared once `Player_YVel` ≥ −$20.
- **Graphics** — the idle cycle is the animated background bank, `PT2_Anim` = $60, $62, $64, $66 (prg030:423, 2295),
  8 ticks per phase. The note's patterns are local tiles $38-$3B of chr096/098/100/102, laid out TL $38, TR $3A, BL
  $39, BR $3B. chr098 and chr102 are identical. The bounce sprite is `BounceBlock_Tile[4]` = $79, $7B from chr004
  (prg001:1234, 1249), identical to frame 1. The P-Switch pins the bank to $3E (`kb/p-switch.md` §2.5), so the note
  block stands still on frame 0.
- **Not aligned:** a power-up comes out of its block at position 10 (prg001:1032-1100), one step after the hit; the
  port dispenses on the hit's step. Not part of the bounce timeline, left as it was.

## 6. Two bounces at once (not ported)

The original runs a block bounce as an object, and only object slots 6 and 7 (and the two `Level_BlkBump` queue
entries) can hold one, so **at most two blocks bounce at the same time**. `LATP_HandleSpecialBounceTiles`
(prg008:5087-5120) refuses a hit when:

1. `Player_Bounce` is still set: a second bump on the same step, before the bounce object took the first;
2. both slots (or both queue entries) are busy: a third block hit while two bounce;
3. the power-up in slot 5 is still rising out of its block (`Objects_Timer` running, `PowerUp_DoRaise`,
   prg001:1806) and the hit is in the same tile column.

A refused hit does nothing at all: no bounce, no item, no bump sound, no push. The block is just a ceiling or a floor.

Effects on play (read from the code, not checked in play):

- **Straddling two note blocks.** The first foot's block bounces; the second foot is refused on that step by (1),
  then succeeds on the next one (the first cell is sky by then, the second still a note block). Two bounces, one step
  apart, both drive the player: slot 6 runs after slot 7 in the loop, so its velocity wins, and the player is launched
  twice, on consecutive steps. The port starts no second bounce while a ride is on (§1), so the player stands on the
  second block while the first sinks and is launched once.
- **A third block** in quick succession (a shell or a tail through a row of blocks) does not react while two bounce.
  The port bounces any number of blocks at once.
- **The column lock**: a block above or below a rising mushroom cannot be hit until the mushroom is out. The port has
  no such lock.

The same block can never bounce twice at once in either: its cell holds CLEAR or sky while it bounces, neither of which
is a bump block.

## 7. Extracting the frames

`frame_1.png` and `frame_2.png` (and `frame_3.png` = `frame_1.png`) were generated from chr098 and chr100 with the
colours of the shipped `frame_0.png`: matching chr096's tiles against it gives an exact bijection, index 0 →
transparent, 1 → black, 2 → white `(252, 252, 252)` (method in `kb/sprites_extraction.md` §8). They match the images
attached to SMB3-82.
