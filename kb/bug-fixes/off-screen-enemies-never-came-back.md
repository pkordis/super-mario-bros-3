# Off-screen enemies froze instead of respawning (SMB3-3)

**Reported as:** an enemy that scrolls off screen while still inside the level should come back at its
placement when the camera returns, as in SMB3. The expiry rules were also spread over each enemy class.

## What was actually wrong

- Every placed enemy was instantiated at level setup and lived for the whole level. An enemy that left
  the camera's activation window was skipped by `EnemyMotionManager.update` (a bare `continue`): frozen
  mid-walk, mid-roll or mid-death, with its sprite still attached, and never reset to its placement.
- Once removed, an enemy was gone for good, because nothing remembered its placement.
- Goomba (×3), Para-Goomba, Koopa Troopa and the shell capabilities (×2) each called their own
  `hasFallenOffLevel()`; only the sideways case (SMB3-1) was central.

## What the ROM does

- **Spawning** (`Level_ObjectsSpawnByScroll`, prg005, called every frame from prg000:1835): one column
  is examined, a fixed look-ahead from the screen picked by `Scroll_LastDir`: `Horz_Scroll + $110` (16 px
  past the right edge) after a scroll right, `Horz_Scroll - 32` after a scroll left. A `Level_Objects`
  entry in that column with `Level_ObjectsSpawned` bit 7 clear is spawned; the bit is set and the index
  stored in `Objects_SpawnIdx`. At level start `PRG005_BF49` sweeps the look-ahead over the whole
  opening screen, from 32 px left of it to 16 px right of it, and leaves `Scroll_LastDir` at "left".
- **Deleting** (`Object_DeleteOffScreen`, prg000 $D3E0, called first by `ObjNorm_GroundTroop`,
  `ObjNorm_ParaGoomba`, `ObjState_Shelled` and `ObjState_Kicked`): `Objects_YHi >= 2`, or X `<= scroll - 48`
  or `> scroll + 288` (`PRG000_D3CC`), goes to `Object_Delete` → `Object_SetDeadAndNotSpawned`, which
  **clears** the bit, so the enemy can respawn. The X test only; vertical position is irrelevant in a
  non-vertical level, where every occupied slot runs each frame.
- **Dying:** `ObjState_Squashed` and `ObjState_Killed` (via `Object_FallAndDelete`) end in
  `Object_SetDeadEmpty`, which leaves the bit set. Killed enemies never come back. Their kill action is
  `KILLACT_JUSTDRAWMIRROR`, which does not run the normal handler, so they never reach the off-screen
  delete.

## The fix

See `kb/dynamic-entities.md`, "Off-screen lifecycle", for the model. In short:

- `EnemyPlacement` + `EnemySpawner` hold the placements and which instance answers for each, and port
  the opening pass and the per-frame look-ahead column literally.
- `GameEngine` runs `ActiveObjectGrid.spawnEnemiesComingIntoView()` each tick before the managers.
- `EnemyMotionManager.update` is the one place that decides, before each enemy's tick: `Y >= 512` →
  release if alive, retire if `isDefeated()`; past the ROM's X edges → delete + release. After the tick,
  expired by itself → retire. Carried and defeated enemies are exempt from the X test. There is no
  vertical freeze and no separate level-edge rule.
- `hasFallenOffLevel()` was removed from `GroundWalkingEnemy`; `ActiveLevelObject.hasFallenBelowTheLevel()`
  is the ROM's `Objects_YHi >= 2`, moved down to the level's bottom only for a level taller than 32 rows.
- The Para-Goomba → Goomba hand-over passes the placement on (`handOverPlacement`).

## Behaviour that follows, and is intended

- A kicked or idle shell that leaves the screen is deleted, and a walking troopa respawns at its
  placement later. Before, the shell froze mid-roll.
- An enemy deleted while its placement is in the look-ahead column comes back at once. One deleted while
  its placement is anywhere else waits until the scroll brings the look-ahead onto that column.
- A Goomba that walks out of the level sideways is deleted 48 px beyond the left edge or more than 32 px
  beyond the right one (the clamped camera), not at the edge itself, and can respawn.
- Enemies above or below the screen keep moving.

## Not ported

The one-spawn-per-call limit and five general slots (`PRG005_B911`), the `Counter_1` throttle on the
delete test, the `Objects_UseShortHTest` variant and vertical levels (`Level_7Vertical`).
`Scroll_LastDir` comes from the camera's movement, where the ROM sets it in `Player_DoScrolling` even on
a frame the clamp holds the screen still.

The ROM also keeps spawning and deleting while `Player_HaltGame` is set; this port freezes the pipeline.
That only shows in the ROM *because of* the two limits above. During a halt nothing moves, so no new
column reaches the look-ahead and no enemy newly crosses a delete edge. What the ROM does then is catch
up: the throttle can leave an enemy past the edge undeleted for up to three frames, and a full slot table
can leave a placement in the look-ahead column waiting, so a delete or a spawn may land inside the halt.
Without the slot limit and the throttle there is nothing left to catch up on, apart from an enemy that
crosses the edge on the tick a halt starts: it is deleted on the first tick after the halt instead,
off screen either way. Port the halt behaviour together with those two limits, or not at all.

The spawn frame is ported: a fresh instance only runs `spawnIntoScene()` on its first tick
(`ObjState_Initializing`) and moves from the next.

## Tests

`EnemyMotionManagerTest` (the ROM's X edges, no vertical freeze, defeated and carried exemptions,
level-edge deletion through the clamped camera, `Y >= 512` release vs retire, tall levels),
`EnemySpawnerTest` (opening pass range, right and left look-ahead, once only, immediate respawn in the
look-ahead column, waiting elsewhere, dead never respawn, hand-over), `ActiveObjectGridTest`
(forwarding), `GoombaWalkTest`, `GoombaStompTest`, `ParaGoombaStompTest`. The three tests that expected an
enemy to expire itself below the level now assert that it gets there still defeated.

Verified by unit tests only; not checked in the running game.
