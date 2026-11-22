# Slopes (ramps) and sliding - Knowledge Base

Everything about walking on ramp tiles and sliding down them (SMB3-81). dasm references are to
`~/Projects/smb3dasm`; the code names below are this project's.

---

## 1. Ramp shape

A ramp is one of six tile types, one per slope shape. Each type maps to a `SlopeProfile` constant
(`TileType.findSlopeProfile()`):

| Tile type                        | `SlopeProfile`         | Surface depth per column (left to right)               | dasm shape |
|----------------------------------|------------------------|--------------------------------------------------------|------------|
| `SOLID_RAMP_STEEP_RISING`        | `STEEP_RISING`         | 15, 14, 13, ..., 1, 0                                  | `$01`      |
| `SOLID_RAMP_STEEP_FALLING`       | `STEEP_FALLING`        | 0, 1, 2, ..., 14, 15                                   | `$02`      |
| `SOLID_RAMP_GENTLE_RISING_LOW`   | `GENTLE_RISING_LOW`    | 15, 14, 14, 13, 13, 13, 12, 12, 11, 11, 10, 10, 9, 9, 8, 8 | `$0C`  |
| `SOLID_RAMP_GENTLE_RISING_HIGH`  | `GENTLE_RISING_HIGH`   | 7, 6, 6, 5, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0, 0         | `$0D`      |
| `SOLID_RAMP_GENTLE_FALLING_HIGH` | `GENTLE_FALLING_HIGH`  | 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 6, 7         | `$0E`      |
| `SOLID_RAMP_GENTLE_FALLING_LOW`  | `GENTLE_FALLING_LOW`   | 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 13, 14, 14, 15 | `$0F`  |

- The **surface depth** of a column is how many pixels below the tile's top the ground starts. A pixel is solid at or
  below it (`SlopeProfile.isSolidAt`).
- The depths are dasm's `Slope_LUT` floor heights (prg000:62-88, low nibble), copied as they are. The gentle shapes
  are not an even 2 px per step: each has one 3-column step (columns 3-5 rising, 10-12 falling).
- Each constant also gives its **straight line**: the depth at the tile's left and right edge (16 to 0 steep rising,
  16 to 8 and 8 to 0 for the gentle halves, mirrored falling). Drawing follows it (section 5), and direction and
  steepness come from it: rising to the right when the left edge is deeper, steep when the edges are 16 apart.
- The tile's pixels play no part. A gentle slope takes two tiles per row (low half, then high half, read in the
  direction it rises). The fill under a slope stays `SOLID`. Ceiling slopes are not supported.
- `StaticEnvironmentCollisionGridCapabilities.toLevelObjects` turns a ramp tile into a `RampLevelObject` holding the
  shape. `LevelObject.findSlopeProfile()` is how the rest of the code asks whether a cell is a ramp.

dasm maps tiles to shapes the same way, through a table: `Level_SlopeSetByQuad` / `Level_SlopeQuad00..C0`
(prg000:28-60). The shapes floors use are 1 and 2 (45 degrees), `$0C-$0F` (the 22.5 degree halves), 3 (solid) and 7
(flat ground).

History: the first version read the shape from the tile's transparency (first pixel with alpha >= 128 per column) with
a single ramp type. It was replaced by the six types so a shape no longer depends on how the art was cut, the depths
are dasm's own, and a tile whose sky was never cleared cannot silently turn into a plain solid.
dasm enables slopes per tileset (`LevelInit_EnableSlopes`, prg008:659, Hills and Underground only); here a ramp works
wherever it is placed.

## 2. Player on a slope

All in `StaticEnvironmentCollisionGrid.handleCollision`. Values in px/frame.

- **When:** `isNearRamp` - a ramp in the feet columns (x+4..x+11) within 10 px above or 6 px below the feet. Otherwise
  the flat, tile-based resolution runs exactly as before.
- **Surface:** one probe under the centre of the feet, (8, 32) (`SLOPE_FEET_PROBE`), as dasm's sloped probe table
  `TileAttrAndQuad_OffsSloped` (prg008:4170) does. Ground is pixel-precise: a ramp pixel is solid at or below its
  surface depth (`SlopeProfile.isSolidAt`).
  - Feet inside the ground: the player is pulled up to the surface, at most 10 px (`SLOPE_CLIMB_LIMIT_PIXELS`), and at
    most 5 px onto flat ground (`FLAT_SNAP_LIMIT_PIXELS`, the flat resolution's own limit).
  - Feet in the air while grounded: the player is pulled down onto ground up to 6 px below (`SLOPE_STICK_PIXELS`),
    which is how a downhill walk stays on the surface. dasm does it by stepping a row down (`Slope_CorrectL` = {-1, 16},
    prg008:5554).
  - Nothing within reach: the flat resolution decides (a foot on a flat ledge holds the player up; otherwise they fall).
    Near a ramp, its feet probes ignore ramps, so a foot over a ramp never lands the player on the ramp tile's top.
  - A player can land on a slope while still rising slowly: Y velocity >= -1.75 (dasm: `>= -$1C`).
- **In-front probes:** near a ramp they sit higher, at Y+24 and Y+12 (Y+24 and Y+23 small), from dasm's sloped table,
  so the rise ahead is not a wall. A ramp is never a wall, and neither is solid ground that a ramp on that side leads
  up onto (`isClimbableFromTheSide`): a walker climbing fast sinks into the slope for a frame and would otherwise meet
  that ground from the side. A solid tile with a ramp *on top of it* stays a wall from both sides. It is a step reached
  from the flat, and treating it as climbable let the player walk into it, where the ramp above the head started the
  emexit push.
- **Pull:** while grounded on a ramp, `PlayerRuntimeState.slopePull` is set to the slope's pull in sixteenths, signed
  by the downhill side: 3 steep, 2 gentle (`Slope_PlayerVel_Effect`, prg000:91). The next position step adds it while
  the player moves (`LevelScenePlayer.updateFrame`), without changing the stored velocity (dasm `PRG008_A8F9`,
  prg008:1830). A still player does not slip.
- **Uphill:** moving against the pull (`isWalkingUphill`). The top speed becomes 13/16 walking and 22/16 with B
  (`PLAYER_TOP_UPHILL_*`; dasm `Player_UphillSpeedVals`, prg008:2317), whatever the P-meter. Acceleration is
  unchanged, so a faster player slows at the normal deceleration. The run flag is not set, so the P-meter drains
  (prg008:2353). The walk animation ticks once more per frame (`renderWalkRun`).
- Jumping is unchanged; the jump force is picked from |X velocity|, so uphill jumps are lower.

## 3. Sliding

`LevelScenePlayerActionCapable.handleSliding`, run right after the collision pass. Velocities in sixteenths.

- **Start:** grounded on a ramp, Down held with neither Left nor Right, not carrying. All modes slide (dasm:
  `PowerUp_Ability` bit 1, prg000:558, rules out only Frog and Hammer). On flat ground Down still ducks; on a slope Down
  never ducks (`handleDucking`).
- **Each grounded frame** (`PlayerRuntimeState.advanceSlide`): slide += pull, clamp to +-64, one toward zero, X
  velocity = slide (dasm prg008:5985-6056). The slide starts from 0, not from the player's speed. Net +2 a frame on a
  steep slope, +1 on a gentle one, -1 on the flat: a slide coasts to a stop.
- **Ends** (`stopSliding`) when the player is airborne (jumping is allowed), holds Left, Right or Up (prg008:3079), is
  stopped by a wall (prg008:5756), enters a pipe or dies, or the slide reaches 0.
- **Against a wall the slide goes on in place** (SMB3-89). A wall that stops a slide while Down is held
  (`slideAgainstAWall`) leaves the player sliding against it: `PlayerRuntimeState.slidingAgainstAWall` keeps
  `isSliding` true, so the player is drawn sliding and a contact still counts as a slide, but X velocity stays 0, so
  nothing moves (a still player does not slip). It ends like any slide, and also when Down is let go, which stands the
  player up; pressing Down again starts a new slide. Without Down held, the wall ends the slide as before.
  - Why: the first version stopped the slide and let it restart. With Down held, it restarted from 0 every other
    frame: the pull stepped the player up to 5/16 px into the wall, the wall pushed them back and stopped the slide,
    so X saw-toothed over a pixel and the pose flipped between sliding and standing each frame.
  - dasm restarts the slide the same way (the wall clears `Player_Slide`, prg008:5752-5757, and a slide starts on any
    frame with only Down held on a slope, prg008:5979-6046, setting the slide frame each time). The wall push comes in
    the same frame as the move and X is drawn in whole pixels, so the player shows the slide frame and stays still.
    The wall check (`PRG008_BAB1`) runs before the floor code that restarts the slide (`PRG008_BBE0`), so by the
    end of the frame the player is sliding again, and objects meet a sliding player.
- **While sliding:** no tail attack (dasm masks B, prg008:1353), no duck, no run flag.
- **Drawing:** the movement is `SLIDING`, drawn with each mode's `sliding.png` (`renderSlide`). The art in the repo is a
  placeholder (a copy of the duck frame, the still frame for small).

### Sliding into enemies

`ActiveObjectGrid.dispatchDirectionalPlayerCollision` sends every contact of a sliding player to `onSlidInto`
instead of the stomp test. `EnemyLevelObject.onSlidInto`:

- Stompable enemies, shells included (still or kicked), are killed with `onStruckByKickedObject`, knocked with their
  own X velocity plus half the player's (dasm prg000:3682-3729), for 100 points, with the kick sound. No bounce, and
  the slide goes on.
- A winged enemy (`isWinged`: Para-Goomba, Koopa Paratroopa) is killed only if `isGrounded`; in the air it hurts the
  player.
- An enemy that cannot be stomped (the piranha family) gets its usual `onPlayerOverlap`, which hurts.
- Getting hurt does not end the slide (dasm never touches `Player_Slide` on a hit).

This follows the ticket, not dasm. In dasm the slide runs the stomp test first (`PRG000_D218`) and kills only when
that changes the enemy's state: a flyer loses its wings instead (`OCSPECIAL_KILLCHANGETO`, prg004:114), and on flat
ground the test usually fails, so the player is hurt. dasm's slide kills pay `Score_Get100PlusPts(Kill_Tally)`, but
the tally resets on every grounded frame (prg008:5944), so 100 each.

## 4. Enemies and power-ups on a slope

The ground probes live in `SlopeFollowingCapable` (`findLandingY`, `isGroundBelow`, `isWallAhead`), shared by
`GroundWalkingEnemy` (Goomba, Para-Goomba, Koopa Troopa, Koopa Paratroopa and the shell) and `SuperMushroom` (Super
and 1-Up). In dasm all of them move through the same `Object_Move` / `Object_WorldDetect4` (prg000:3371; the mushroom
via `ObjNorm_PUpMush` -> `Object_InteractWithWorld`, prg001:1947), so they follow slopes alike. A new object that
walks the ground implements `SlopeFollowingCapable` and lands through `findLandingY`.

- **Landing** (`findLandingY`): `StaticEnvironmentCollisionGrid.findRampSurfaceNear` looks under the centre column in
  the feet row and the rows either side for a ramp surface, or the top of flat ground beside a ramp. It is taken from 8
  px above the feet (climbing, `WALKER_SLOPE_CLIMB_LIMIT_PIXELS`) to a stick distance below them (following the slope
  down): the whole-pixel X speed plus 1, at least 2 (`MIN_SLOPE_STICK_PIXELS`). dasm follows a slope down by moving the
  object down by its whole-pixel X speed and detecting again (`Objects_Slope`, prg000:1177-1203). Otherwise the flat
  landing runs, which now skips ramp cells.
- **Walls** (`isWallAhead`): `isClimbableFromTheSide` as for the player.
- **Speed** (`GroundWalkingEnemy.walkSpeedFixedPoint`, enemies only): 8 on the flat and downhill, 6 uphill on a
  gentle slope, 5 on a steep one (`ObjNorm_GroundTroop` / `GroundTroop_XVel`, prg004:4000-4070). The mushroom keeps
  its 16 everywhere: its routine sets no slope speed.
- A red Koopa Troopa stays grounded walking down, so its ledge guard does not turn it at the top of a slope.
- Shells follow the surface only; their speed is not changed.

### Walk animation (SMB3-85)

Walkers step through their walk cycle faster uphill and slower downhill. `WalkAnimationClock` adds a **rate** in 256ths
of a step each tick; a full step advances its step count (wrapping at 256), and every 4 steps the walk frame changes.
`GroundWalkingEnemy.walkAnimationRate(facingRight)` gives the rate, read after the tick's move:

| Ground under the centre of the feet | Rate (`SlopeProfile.enemyWalkAnimationRate`) | Ticks per walk frame |
|-------------------------------------|----------------------------------------------|----------------------|
| steep, uphill                       | 255                                          | about 4              |
| gentle, uphill                      | 192                                          | 5.3 (5, 5, 6)        |
| flat, or in the air                 | 128 (`FLAT_WALK_ANIMATION_RATE`)             | 8, as before         |
| gentle, downhill                    | 96                                           | 10.7 (10, 11, 11)    |
| steep, downhill                     | 64                                           | 16                   |

dasm (`ObjNorm_GroundTroop`, prg004:4001-4120): after `Object_Move`, `Var7 += PRG004_B353[LRBounce_Vel + 2]` with
`PRG004_B353` = 255, 192, 128, 96, 64; the carry bumps `Var5`, and the frame is `(Var5 >> 2) & 1`. `LRBounce_Vel` is
`Slope_ObjectVel_Effect` of the slope the object landed on (-2/2 steep, -1/1 gentle), negated when moving left, so -2
is steep uphill and +2 steep downhill; `Object_WorldDetect` clears it every move and sets it only on sloped ground
(prg000:944, 1105-1146).

- Goomba and green Koopa Troopa use the rate (`advanceWalkFrame`). The green Koopa Paratroopa's clock uses it too, wings
  included, although it is on the ground for one tick per hop. The red flying Paratroopa takes a full step every tick.
- A red Koopa Troopa keeps the flat rate: dasm's `ObjNorm_RedTroopa` bumps `Var5` every tick and shows
  `(Var5 >> 3) & 1`. A Para-Goomba keeps its fixed 8 ticks too (`ObjNorm_ParaGoomba`, prg004:3011, frames from the
  global `Counter_1 >> 3`).
- dasm uses the same routine for Buzzy Beetle, Spiny and the giant Goomba and green Troopa (prg004:64-87), not yet in
  the project.

## 5. Drawing: gliding instead of stepping

The physics keeps dasm's whole-pixel surface: Y snaps to the surface depth of the pixel column under the centre
(`Player_Y = (Y & $F0) + height`, prg008:5942), so Y moves a whole pixel at a time. dasm also draws X at whole pixels
on a 256 px wide screen, so there the motion reads as smooth. This project draws X with its fraction and each pixel
several screen pixels tall, so drawn at the physics Y an object climbs in visible stairs.

So only the drawing follows the **straight ramp behind the tile's pixels** (`SlopeProfile.idealSurfaceDepthAt`): a
line of 1 px per pixel across (steep) or 1 px per 2 (gentle), from the depth where the ramp meets the ground at its low
end (16, or 8 for the upper half of a gentle ramp) to where it meets the next tile (0, or 8). The line comes with the shape,
so the art does not show in the motion, and the lines of neighbouring ramp tiles, the floor
before them and the top after them all join. This is dasm's own table without its rounding to whole pixels.

`StaticEnvironmentCollisionGrid.findSlopeDrawOffset(centreX, feetY)` returns line minus physics surface when the feet
are exactly on a ramp's surface under the centre, and 0 otherwise.

- Enemies: `GroundWalkingEnemy.pixelToWorldOffset`, through `SlopeFollowingCapable.slopeDrawOffsetPixels` (0 for a
  carried shell, which is drawn with its carrier). The mushroom's `positionSprite` uses the same method, except while
  rising.
- Player: `PlayerPosition.slopeDrawOffset`, set each tick after the collision pass (`findSlopeDrawOffset(player)`, 0
  in the air, cleared on death), snapshotted and interpolated with the position, and read through `getDrawY()`.
- A first attempt interpolated between the pixel columns of the tile's surface. It made things worse: every pixel
  irregularity, and the gentle slope's two-column steps, showed up as bends in the drawn path.

## 6. Enemies meeting the player and each other on a slope

SMB3-89. Apart from the walking speed, the walk-animation rate and slide kills, the flat-ground contact rules hold on
a ramp. Two of them needed a change, because a ramp puts two bodies that touch at different heights.

### The player on the ground cannot stomp

The stomp test (kb/dynamic-entities.md, "The stomp test") only asks whether the player's Y is 19 px above the enemy's
stomp reference. A ramp lifts the uphill one by up to the full horizontal distance between them, so a player walking
down into a Goomba walking up qualified and stomped it.

`ActiveObjectGrid.dispatchDirectionalPlayerCollision` now sends the contact to `onPlayerOverlap` (the body hit: hurt,
or kick for a still shell) when the player is on the ground (`LevelScenePlayer.isOnTheGround`, not jumping, falling or
flying) and the enemy is not in the air falling slowly: it is grounded (`ActiveLevelObject.isGrounded`), rising, or
falling at 10 sixteenths a tick or more (`SLOW_FALL_SPEED_LIMIT`). A player in the air stomps as before, on a ramp
too. A sliding player never reaches this test: the slide runs first (section 3, "Sliding into enemies").

dasm: `Player_HitEnemy` (prg000:3670-3770). For an object with `OA2_GNDPLAYERMOD` and a player not in the air
(`PRG000_D205`), an object Y velocity `>= $0A` (an unsigned compare, so a rising object counts too) or an object that
hit the ground (`Objects_DetStat & 4`) goes straight to `Object_HoldKickOrHurtPlayer`, skipping the stomp test. The
flag is set on the Goomba, both Koopa Troopas, Buzzy Beetle, Spiny, the giant Goomba and Troopas and the flying green
Paratroopa (prg004:194-220). The Para-Goomba (`$73`) and the hopping green Paratroopa (`$6E`) lack it, so in dasm a
walking player can still stomp those on a slope. Here the rule applies to every enemy, as the ticket asks: on the
ground only a slide kills.

### Walkers bump on a slope

`ActiveObjectGrid.resolveObjectToObjectBumps` turned two walkers only when they met side-on (`isSideContact`, the
shallower overlap is horizontal), so a walker falling onto another's head does not turn. A Goomba's box is 13x8: on a
steep ramp the height between two walkers equals the distance across, so the vertical overlap was always the smaller
and they walked through each other. On a gentle ramp they still bumped, a few pixels later than on the flat.

Now two objects that are both on the ground are judged as if they stood level (`ActiveObjectGrid.meetSideOn`): they
meet side-on while the horizontal overlap is less than the smaller box height. On the flat that is the old rule. On a
slope the height between them, which the ground alone causes, no longer counts. A walker that falls through another
and lands inside it overlaps it by more than the height and still does not turn (`GoombaBumpTest`).

dasm's `Object_BumpOffOthers` (prg000:2423-2475) turns on any bounding-box intersection between two
`OAT_BOUNCEOFFOTHERS` objects in their normal state, facing away by sprite X; it has no side-on test. With the same
13x8 box, two walkers on a steep ramp touch only when less than 8 px apart across, in dasm as here.

### Testing

`EnemySlopeContactTest` runs a real player and real Goombas over the steep and gentle hills through the object grid:
walking up and down into a Goomba hurts, falling onto one stomps it, sliding into one kills it, and two Goombas turn
away from each other on both slopes. `ActiveObjectGridTest` covers the rules with stubs, and `PlayerSlopeTest` the
wall hold-off ("Sliding into a wall beside a ramp with Down held, the player stays still against it").