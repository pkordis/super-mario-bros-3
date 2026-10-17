# Taking a hit mid-swing retroactively disarmed the tail strike

### Symptom
The original's rare "killed it *and* got hurt by it" frame could never be reproduced in the port, no
matter how close the player got before swinging. Contact always resolved as hurt-only, never
hurt-and-kill. The enemy that hit you always survived your swing.

### What went wrong
Nothing to do with the boxes — the geometry does admit both. With the player's `Player_BoundBox`
spanning X `[+3, +13]` / Y `[+5, +30]` and the tail box at X `[+17, +27]` / Y `[+16, +31]`, an enemy
box 16x8-ish overlaps **both** whenever its left edge sits in roughly `[+3, +12]`, i.e. when it is
already touching the player. The dispatch order was right too: `ActiveObjectGrid` resolves the body
collisions for a player and only then the tail strike, mirroring the object loop
(`prg000.asm` @ PRG000_C9B6 — handler → `Player_HitEnemy`, then `Object_HitByTailOrBouncer`).

The problem was *what the hurt did on the way out*. `onHurt()` on a raccoon runs `loseAdvancedSuit()` →
`neutraliseMotionForTransition()`, and that method cleared `playerTailAttackCountdown` along with flight
time and wag count. Since the tail strike is evaluated **after** the body pass, by then
`isTailAttackStriking()` saw a counter of `0`:

```
body pass  -> onPlayerOverlap -> onHurt -> ... -> playerTailAttackCountdown = 0
tail pass  -> isTailAttackStriking() == false -> no strike dispatched at all
```

So the hurt did not merely coexist with the strike, it *cancelled* it — one tick of the swing silently
deleted.

The ROM does no such thing. `Player_GetHurt` (`prg000.asm` @ PRG000_D9D3 ... PRG000_DA6D) writes
`Player_SuitLost`, `Player_QueueSuit`, `Player_Kuribo`, `Player_Grow`, `Player_FlashInv` and
`Player_Flip` — and never `Player_TailAttack`. In the entire ROM that counter is cleared in exactly two
places, neither of them a hit: kicking a shell (`prg008.asm` @ PRG008_AF52) and grabbing an ice block
(@ PRG008_B5F4). It is otherwise only set to `$12` and counted down.

### What the fix does
`neutraliseMotionForTransition()` no longer touches the swing counter; it still spends flight time and
wag count. Leaving the counter running past the poof is harmless, because the completed transition makes
the player `NORMAL` and `isTailAttackStriking()` is gated on `hasTail()`.

Regression cover: `TailStrikeDuringABodyHitTest` — a real `LevelScenePlayer` mid-swing on `$09` against a
real `ActiveObjectGrid` and an enemy double that hurts on overlap, asserting the dispatch order
`overlap` then `tail`, that the suit was lost, and that the counter survived. Verified to fail with the
old clear restored. `PlayerModeTransitionTest` now pins the same thing from the transition's side.

### Note
`ActiveObjectGridTest#bodyHitLandsBeforeTheTailStrikeOnTheSameTick` did *not* catch this: its player is a
mock whose `isTailAttackStriking()` is stubbed `true`, so it pins the grid's ordering but is blind to
anything the hurt does to the real player's state. Ordering tests over mocked participants cannot see
state cancellation — that is what the real-player test is for.

---

## Follow-up: the swing counter has to tick unconditionally

### Symptom
With the fix above in place, a player who took the hit and killed the Para-Goomba on the same frame could
never turn around again: their sprite stayed mirrored the way it was and ignored the pad's direction for
the rest of the level.

### What went wrong
Two safe-looking things that are only safe apart:

1. The hurt no longer clears `playerTailAttackCountdown` (above), so after the hit it still reads `$09`.
2. The decrement lived inside `handleTailAttack`, which `updateFrame` only calls under `if (hasTail())`
   — and `updateFrame` itself returns early while dying, transitioning or halted.

The hit costs the raccoon suit, so from the poof onwards `hasTail()` is false and nothing ever
decremented the counter again. It sat at `$09` forever, and `handleHorizontalMovement` skips the pad's
facing update for as long as a swing is running (prg008 @ PRG008_AE11) — so the facing froze permanently.

The ROM cannot get into this state. `Player_TailAttack` is one of the eight adjacent bytes decremented by
the loop at prg008 @ PRG008_A1DE, and that loop sits **after** `JSR Player_Update` and after
`Player_HaltGame` is computed, on the path nothing branches around (the one branch above it only skips
`INC Level_NoStopCnt`). The counter therefore runs down with no tail, through a suit-loss poof, through a
pipe move, and through the death throe.

### What the fix does
The decrement moved out of `handleTailAttack` into `tickTailAttackCountdown()`, called as the **first**
statement of `updateFrame()` — above the dying / transitioning / halted early returns, and outside the
`hasTail()` gate. `handleTailAttack` now only starts a swing, which stays raccoon-only as the ROM's
`Player_TailAttackAnim` call sites are.

This also shifts the whole swing one frame earlier relative to the press, since every reader now sees the
post-decrement value a step sooner. Nothing observable changes: the ROM reads the counter at two
different points of its frame (the sprite flip and the block probe inside `Player_Update`, i.e.
pre-decrement; the object strike in the object loop, post-decrement), and the port reads one value at a
single point — but the *relationships* that matter are preserved. The strike frames are still `$0C` and
`$09`, `$0C` still lands outside the flip window and `$09` still inside it, and the block probe still
resolves to `+21` in front of a right-facing player.

### Keep this in mind
The call must stay above `updateFrame`'s early returns. Anything that strands this counter does not just
leave a stale number around — it freezes the player's facing, because the facing skip is keyed off it.

Regression cover: `PlayerTailAttackSwingTest#theCounterRunsDownEvenWithoutATailAndFreesTheFacing`.
