# Enemies bounced off the level's edge (SMB3-1)

**Reported as:** a Goomba walking towards the leftmost column turns round and walks back, instead of
walking off the screen and disappearing.

## What was actually wrong

The grid's two object-facing queries, `isGroundSolidTile` and `isWallOrCeilingSolidTile`, treated
every column off the left or right edge as solid. Every walker's `isWallAhead` reads
`isWallOrCeilingSolidTile` at its leading column, so the level's sides acted as walls. Goombas,
troopas and kicked shells all turned round at the edge.

The ROM has no such wall for objects. `Object_DetectTile` (dasm prg000 @ PRG000_C832) returns tile
`$00` ("No tile detected") when the probe's `ObjTile_DetXHi >= $10`. That unsigned compare also catches
a negative X (`XHi = $FF`). Tile `$00` is in quadrant 0, below every tileset's ground (`$25`) and wall
(`$2D`/`$2E`) thresholds, so it is neither a wall nor a floor. `ObjNorm_GroundTroop` (prg004) turns
only on `Objects_DetStat & $03`, so the walker keeps going, and the off-screen check deletes it.

The player is a separate case. The ROM keeps it in the level with the screen clamp in
`Player_DoScrolling` (prg008 @ PRG008_B1CE: sprite X held to 16 / 232 in the direction of travel,
with the camera clamped to the level). This port does the same job with `collidesAtOffset`, which has
its own bounds check and never went through the object queries.

## The fix

- **Grid:** off the level's sides is now open (`false`) in both object queries, at every row, including
  below the world. Inside the level's columns nothing changed: below the world is solid, above it is sky.
  `collidesAtOffset` is untouched, so the player is still blocked.
- **Expiry, in one place:** `ActiveLevelObject.hasLeftTheLevelSideways()` is true once the sprite lies
  wholly beyond either edge. `EnemyMotionManager.update` checks it after every tick and calls
  `expire()`, so the existing retire path detaches the enemy and removes it from the list exactly once.
  No enemy class has its own copy. **Superseded by SMB3-3** (`kb/dynamic-entities.md`, "Off-screen
  lifecycle"): enemies no longer use `hasLeftTheLevelSideways()` at all. The ROM's own off-screen
  deletion, against the clamped camera, removes them 48 px beyond the left edge or more than 32 px beyond
  the right one, and releases the placement so they can respawn.
- **Super Mushroom:** it had worked around the left wall with a `leadingColumn < 0` special case, and
  expired only past the left edge. The grid made the special case redundant, and the right edge
  is now open too. Its expiry uses the shared predicate so it also retires off the right edge. Without
  that it would fall out of the activation window and stay dormant.

## Behaviour that follows from the ROM, and is intended

- **Off the level there is no floor either.** A Goomba's feet probe is at its centre (`x + 8`), so it
  starts to fall 8 px before it is fully off screen. The ROM does the same (Group 1 feet probe `$10,$08`).
- **A red troopa turns round at the level edge.** It finds no ground there, so its ledge guard
  (`ObjNorm_RedTroopa` @ PRG004_B283) fires exactly as it does at a pit.
- **A held shell at the edge is no longer "pressed into the world"** and is not crushed when it wakes,
  because the off-level cell is empty to `isPressedIntoTheWorld` as well.

## Why not the ROM's deletion margin (at the time)

`Object_DeleteOffScreen` deletes at `x <= camera - 48` or `x > camera + 288`, which is two tiles beyond
fully off screen when the camera is clamped at an edge. At the time, enemies outside a two-tile
activation window froze instead of being deleted, and a Goomba's bounds (`x+1 .. x+14`) left it at
`x = -46`, so it would have frozen two pixels short of `-48` and never been deleted. SMB3-3 replaced the
freeze with the ROM's deletion, so the ROM's margin now applies and this section is history.

## Tests

- `CollisionGridLevelEdgeTest`: object queries are open beside the level and unchanged above/below,
  outermost-column terrain is still solid, and the player is still blocked and nudged back at both edges.
- `GoombaWalkTest`: walks off both edges without turning (x strictly monotonic every frame), and
  `hasLeftTheLevelSideways` flips exactly at the fully-off frame. A real wall in column 0 still turns it.
  The fixture's `isSolid` mirror was updated to the new bounds.
- `EnemyMotionManagerTest`: past either edge → expired, detached once, removed, never ticked again.
  An enemy straddling the edge stays in play and in the broadphase.

The other enemy tests (`GoombaStompTest`, `KoopaTroopaCarryTest`, …) still carry their own copies of
the old "sides are solid" `isSolid` helper. None of them probes off the level, so they pass either way.
They no longer mirror the real grid, though.

Verified by unit test only: 641 tests green. Not verified by playing the game.
