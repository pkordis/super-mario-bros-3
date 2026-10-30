# Left-behind power-ups froze in place instead of being deleted (SMB3-17)

**Reported as:** a mushroom or leaf the player does not collect stays where it was when the player moves
away, and is still there when the player comes back. The original game seemed to remove them.

## What was actually wrong

`RewardLevelObjectMotionManager.update` only ticked a reward while its box met the screen plus a 2-tile
margin (`getActivationMarginPixels()`). Outside that window the reward was skipped: frozen, sprite still
attached, never removed. The only removals were each reward's own below-the-level check (`rows + 1`
tiles) and, for the mushroom, rolling out of the level's sides.

## What the ROM does

Every power-up calls `Object_DeleteOffScreen` (prg000 $D3E0) each frame, after moving and before
`PowerUp_DoHitTest`: `ObjNorm_PUpMush` (PRG001_A735), `ObjNorm_PUp1UpMush`, `ObjNorm_SuperLeaf`, and the
unported `ObjNorm_StarOrSuit` and `ObjNorm_FireFlower`. It deletes at X `<= scroll - 48`, X
`> scroll + 288` (`PRG000_D3CC`) or `Objects_YHi >= 2`. Nothing freezes a power-up before that.

A deleted power-up never comes back: block power-ups always use slot 5 (`ObjNorm_BounceDU` @ PRG001_A56F)
and are not spawned from a placement, so `Objects_SpawnIdx` is `$FF` and `Object_SetDeadAndNotSpawned`
has no spawn bit to clear. The block is already used.

While a mushroom is held in its block (timer `>= $2d`), `PowerUp_DoRaise` jumps to PRG001_A93D, past the
delete call at PRG001_A937.

## The fix

- The activation window is gone: every reward ticks every frame.
- After the tick the manager deletes a reward past `isPastTheDeletionEdge` (moved up to
  `ActiveLevelObjectMotionManager` and shared with the enemies) or at `hasFallenBelowTheLevel()`, with no
  caption and no points.
- `RewardLevelObject.isExemptFromDeletionOffScreen()`; the mushroom returns `true` during its hold.
- `SuperMushroom.hasFallenOffLevel()` and `SuperLeaf.hasFallenOffLevel()` were removed.

Not ported, as decided in SMB3-16: the `Counter_1` throttle and deleting during a halt.

## Tests

`PowerUpOffScreenDeletionTest`: both delete edges at their exact pixel, gone for good after the screen
returns, the hold exemption frame by frame, a leaf out of sight still moving, `Y >= 512` and the taller
level. Verified by unit tests only; not checked in the running game.
