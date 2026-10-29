# The mushroom was collected inside the block (SMB3-15)

**Reported as:** a Super Mushroom or 1-Up rising out of a block can be picked up while it is still
almost entirely hidden inside the block, sometimes on its first visible pixel line.

## What the ROM actually does

The ROM does **not** wait for the mushroom to emerge. Collection is allowed from the first frame of the
rise. Two things limit it: the hold at the start, and a small box.

- **Spawn.** The bounce block spawns the power-up at **block Y − 1** (dasm prg001.asm:2282-2289,
  `SUB #$01`). The 15 px creep then ends exactly on the block's top edge.
- **Hold.** `ObjInit_PUpMush` sets `Objects_Timer = $3D` and `Objects_Timer2 = $10` together. While
  `Timer >= $2D`, `PowerUp_DoRaise` branches straight to PRG001_A93D (prg001.asm:1818-1819). It does
  not move, draw or hit-test.
- **Gate.** `PowerUp_DoHitTest` checks only `Timer2 != 0`. Both timers are decremented by the central
  object loop before the state routine (prg000 @ PRG000_C975 / PRG000_C983), so `Timer2` reaches 0 on
  the last hold frame. The **first rise frame is already collectable**.
- **Box.** `OAT_BOUNDBOX01`, `Object_BoundBox` entry 1 (prg000.asm:280): `1, 13, 2, 8`. It is shared by
  the mushroom, 1-Up, leaf, flower and star. `ObjectObject_Intersect` (prg000 `$D97B`) is
  **inclusive**: on each axis, the near-edge difference must not exceed the extent of whichever box
  comes first.

So a player standing on the block (box bottom `blockY − 2`) meets the rising mushroom (box top
`blockY + 1 − k` after `k` px) at `k = 3`, with 4 px showing. From below, the mushroom's box bottom
(`blockY + 9 − k`) never leaves the solid block.

## What was wrong

1. **The box was the whole sprite.** `SuperMushroom` (which is also the 1-Up) inherited the
   `RewardLevelObject.getBounds()` default: the 16×16 image. During the rise most of that is inside the
   block cell. A player re-jumping into the block after the hold collected it through the block.
2. **The spawn pixel was missing.** The rise ended 1 px inside the block, and the first walking frame's
   ground snap popped it up.
3. **The creep was one frame late.** `tickRise` read the timer before decrementing it, so the first
   pixel landed on tick 18, one frame after the hit test opened.

The ticket said the timing "matches the ROM". It nearly did, apart from item 3.

## The fix

- **One box.** `RewardLevelObject.powerUpBoundsAt(position)` and the four `POWER_UP_BOUND_BOX_*`
  constants. `SuperMushroom` (both variants) and `SuperLeaf` override `getBounds()` with it. The leaf's
  horizontal-only `HITBOX_*` copy is gone, so the leaf gains the vertical part. `Coin` is untouched.
- **Inclusive vs half-open.** `LevelScenePlayer.getObjectCollisionBounds` keeps `Player_BoundBox`'s
  literal extents in a half-open box, so it is 1 px short on its right and bottom compared with the ROM.
  On whole pixels, the ROM's `a <= b` is `a < b + 1`, so the power-up box absorbs both missing pixels:
  left and top one pixel out, right and bottom one pixel past the inclusive edge. In engine space it is
  X `[x, x+15)`, Y `[y+1, y+11)`. The ticket's two suggested readings (`13×8` and `14×9`) both leave the
  top at `+2`, so from above they meet at `k = 4`, one pixel late. The player box was left alone, because
  enemies, stomps and the tail all read it.
- **Spawn at Y − 1**, and the timer is now decremented before it is read, so the creep lands on ticks
  17, 20, … 59 (rise frames 1, 4, … 43). That is 15 px, resting on the block top with nothing to snap.
- **The hold is the hold.** `isCollectable()` is `collectProtection.isElapsed() && !isHoldingInTheBlock()`.
  That is the ROM's own structure: during the hold `PowerUp_DoHitTest` is never reached. Because the two
  end together, in practice this only matters on the hold's last frame. Without it, tick 16 would be
  collectable. There is no "far enough out" threshold.
- **Clip.** With the spawn pixel, the sprite's top is above the block during the hold, so the hold is
  hidden explicitly (the ROM does not draw it at all). After that, the slice is the part above the block
  top: `k + 1` px, sitting on the block's top edge, and 16 px when the rise ends.
- **Shared protection.** `PowerUpCollectProtection` holds the `Timer2 = $10` window that the mushroom
  and the leaf each used to carry as their own constant and counter.

## Tests

- `SuperMushroomRiseTest`, run over both `SuperMushroomType` variants, through the real
  `SuperMushroomMotionManager` and `ActiveObjectGrid`. It covers:
  - the 16-frame hold (not moved, not drawn, not collectable, even with the boxes overlapping)
  - collection on the first rise frame
  - from above, collected on exactly tick 23 with 4 px showing (3 px the frame before), for large and
    small players
  - never collected from below over the whole rise
  - the spawn pixel, the per-frame creep, the slice aligned with the block top, and no snap afterwards
- `PowerUpBoundsTest` covers:
  - the ROM table values and the engine-space box
  - agreement with a transcription of `ObjectObject_Intersect` at every whole-pixel offset, for both
    player boxes
  - the leaf using the box, the leaf clearing a player flush against either side of the block, and the
    leaf's 16-frame protection
- A mutation run (top at `+2`) fails both the from-above frame tests and the ROM-agreement test.

## Left alone

- **Fractional positions.** The ROM tests 8-bit screen positions; the engine's player X is fractional
  while moving. The equivalence above is exact on whole pixels only.
- **Spawn tick.** The engine dispenses during the player's update and ticks the mushroom later that same
  tick, so engine tick 1 is the dispense tick. Whether that is ROM frame 0 or 1 depends on slot order in
  the ROM's object loop and was not chased. The hold, gate and creep are aligned with each other either way.
- The downward knock-out (`Player_BounceDir == 0`: `YVel $10`, no rise), and the flower and star.

Not confirmed with a video capture (the ticket's step 7). Verified by unit test only.
