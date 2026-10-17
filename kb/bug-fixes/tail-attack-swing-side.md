# Tail attack struck the side the player faced, not the side the tail was on

### Symptom
Whacking a Para-Goomba felt unreliable, worst when the player was level with it and pressing a
direction: the tail visibly swept through the enemy and nothing happened. It read as "the hitbox
only works when the enemy is on the floor", and as though the Para-Goomba's box were wrong.

### What went wrong
Two separate ports of the same ROM fact were missing. **Every tail geometry the ROM computes is keyed
off `Player_FlipBits` — the drawn facing — never off the player's intent.**

**1. The box belongs behind the drawn facing.** `Object_RespondToTailAttack`
(`prg000.asm` @ PRG000_DB2F) indexes `Player_TailAttackXOff: .byte $11, -$0A` by the flip bit, and
`SPR_HFLIP` set means *facing right* (`prg008.asm:492`, `prg029.asm:1680`). So the unflipped
(left-facing) sprite takes `+$11` — the box lands on the player's **right**, i.e. behind him, which is
where the tail art is. The port placed it in front on both strike frames.

**2. The swing mirrors the sprite partway through, and the facing is frozen while it runs.**
`Player_TailAttackAnim` (`prg008.asm` @ PRG008_B103) EORs `SPR_HFLIP` on the frame the counter reads
`$0B` and EORs it back on `$03`. The pad's own facing update is skipped for the whole countdown
(`prg008.asm` @ PRG008_AE11: `LDA Player_TailAttack / BNE`, *before* the pad is read), which is what
lets that EOR survive a held direction. The port mirrored the sprite (`RaccoonAnimator`) but left the
facing steerable, so holding a direction mid-swing dragged both the sprite and the hitbox around.

Because the two strike frames straddle the flip, the consequence is that **the ROM's swing covers both
sides of the player**:

| counter | drawn facing | box | side relative to the player's facing |
|---|---|---|---|
| `$0C` | as pressed | behind the drawn facing | **behind** |
| `$09` | mirrored | behind the drawn facing | **in front** |

The port had no back-side strike at all (so an enemy walking into the player from behind, or anything
caught on the first half of the sweep, could never be hit) and a phantom front-side box on `$0C` where
nothing is drawn.

### What the fix does
`LevelScenePlayer.getTailAttackSwingFacing()` is the drawn facing: the player's own, mirrored over the
counter range `($0B, $03]`. `getTailAttackBounds()` and `getTailAttackBlockProbeX()` both place
themselves behind it, and `RaccoonAnimator` takes the same value instead of recomputing the flip, so
sprite and hitbox cannot drift. `handleHorizontalMovement` skips the facing assignment while the
countdown is running (acceleration still runs — the ROM's skip covers the facing store only).

The **block** probe comes out unchanged, and that is not a coincidence: it fires on `$09` alone, which
is inside the flip window, so "behind the drawn facing" resolves to `+21` — the tile in front — exactly
what the old facing-keyed code produced. `CollisionGridTailAttackTest` passes untouched.

Regression cover: `PlayerTailAttackSwingTest`.

### Not a bug: the vertical band
The rest of the complaint is authentic and worth not "fixing" later:

- The tail box is `Player_SpriteY + $10`, height `$0F` — the player's **waist to just above the feet**,
  never the chest.
- An ordinary enemy's box is `OAT_BOUNDBOX01` = the **top 8 px** of its 16x16 sprite (`+2 .. +10`), so
  its lower 6 px and a Para-Goomba's wings (drawn 10 px *above* the body) are in no box at all.

Together those mean a grounded player can only tail-strike an enemy within ~10 px of their own foot
level. A Para-Goomba's big hop peaks around 36 px up, so for most of that hop it genuinely is out of
reach — while its sprite still overlaps the player's chest. The stomp feels better because it uses the
player's own 25 px-tall `Player_BoundBox`, not the tail box.
