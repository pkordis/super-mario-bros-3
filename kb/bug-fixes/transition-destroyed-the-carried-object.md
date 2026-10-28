# A mode transition destroyed whatever the player was carrying

**Symptom.** Carry a Koopa Troopa shell, collect a mushroom or a leaf, and the shell is gone the moment
the suit lands. The grow appeared to *kill* it; the leaf appeared to *tail-attack* it out of your hands.

Two unrelated defects, which is why it looked like one bug with two faces. Neither has anything to do
with the transition counters: `growShrinkCounter` and `poofCounter` are never read by any of the code
involved, and `neutraliseMotionForTransition` touches nothing the carry depends on.

---

## 1. The powerup was a valid carry-contact victim

`resolveCarriedObjectKills()` runs **before** `resolveActiveObjectCollisions(...)` in the engine's object
phase. A powerup walks along the ground directly in front of the player — which is exactly where a
carried object is held (`CARRY_SIDE_OFFSET_PIXELS` 11, `CARRY_DROP_PIXELS` 13, 16 px body). So on the tick
you touched the mushroom, the pass saw the shell as having been *rammed into* it: `onCarriedInto` →
`other.onStruckByKickedObject(...)` → `onKilledByCarryContact()`, dead for 100 points. Only afterwards did
the pickup run and start the transition, so the two appeared causally linked.

The ROM has a flag for precisely this, and its own definition names the case:

> `OAT_HITNOTKILL = %10000000 ; Object will run collision routine instead of standard "Kick"-sound/100
> points/OBJSTATE_KILLED [i.e. object not killed by being rammed with held object]` — `smb3.asm:3252`

Every powerup carries it (`prg000.asm:309-333`, alongside `OAT_FIREIMMUNITY`). And the test is applied to
the **candidate**, inside `ObjectToObject_HitTest`, *before the collision is reported at all*:

```asm
LDY Level_ObjectID,X        ; Y = OTHER object's ID
LDA Object_AttrFlags,Y
AND #OAT_HITNOTKILL
BEQ PRG000_DC57             ; not HitNotKill -> report the collision
PRG000_DC51:
  DEX / BPL PRG000_DC0B     ; otherwise keep looking
```
(`prg000.asm:6165-6173`)

So the striker is unaffected too — a rolling shell passes straight through a powerup without dying and
without scoring. Ported as `ActiveLevelObject.isKilledByRammedObject()`, defaulting to `true` and
overridden to `false` on `RewardLevelObject`, filtered in **both** lethal passes through one shared
`isRammable(candidate)` because it is one test in one ROM routine that both state routines call.

Worth noting: this was never specific to transitions. A shell rammed into a *coin* would have died too.

## 2. A phantom tail swing, from a press that should have expired

The ROM arms a swing off `Pad_Input` — the buttons **newly pressed this frame**:

```asm
BIT <Pad_Input
BVC PRG008_B0E2    ; not a fresh B press -> no swing
LDA Player_TailAttack
BNE PRG008_B0E2    ; already swinging -> no swing
LDA #$12
STA Player_TailAttack
```
(`prg008.asm:3579-3586`)

`PlayerInputHandler.consumePress` is a **latch**, not an edge: it stays available until somebody reads it.
Nothing reads B while the player is tailless — carrying uses `isActive(HANDLER_RUN)`, deliberately, since
"the press edge belongs to the tail swing". So a B held down since before the leaf was collected was still
sitting unconsumed 23 ticks later, and on the first tick `hasTail()` turned true, `handleTailAttack`
converted it into an 18-tick swing nobody asked for.

Nine ticks in, the hitbox flips to the *other* side of the player (`getTailAttackSwingFacing`, the ROM's
`Player_TailAttackAnim` EOR at `PRG008_B103`) and lands squarely on the carry anchor. `resolveTailAttack`
then reached `KoopaTroopa.onTailAttack`, which flung the shell away upside down with a flash and
`endCarry()`d it. A real tail strike, on your own held object, from a button you never pressed.

Fixed in `LevelScenePlayerCapabilities#completeTransition`, which applies the queued suit and then
consumes the stale press. Nothing legitimate is lost: holding B *is* how you carry, so a genuine swing
needs a release and a fresh press — and the release is itself the kick.

### Why not fix `consumePress` instead

Making it edge-accurate per frame is the faithful fix and would close this whole class of bug (the same
phantom fires for a player holding B who collects a leaf while *not* carrying). It also changes the
meaning of every `consumePress` call in the project — jump, size toggle, layer toggle, the low-clearance
discard — so it is its own change with its own test pass, not a bug fix. **Still open.**

### What was deliberately not added

A `state == HELD` guard on `KoopaTroopa.onTailAttack`. The ROM has none: the object loop calls
`Object_HitByTailOrBouncer` for every slot unconditionally (`prg000.asm:1910-1911`) and relies on the input
being impossible rather than on a state check. Adding one would invent behaviour — and would also hide the
fact that a swing already in progress when you pick something up can legitimately clip it.

---

## Secondary, left alone

`EnemyMotionManager.updateWhileHalted()` does not call `motionUpdate()`, so a carried object's **logical**
position is frozen for the whole transition while its sprite keeps being placed every rendered frame from
`interpolateVisualPosition`. The divergence is zero in practice (nothing moves during a halt) and the
first resumed `tickHeld` re-syncs it. The wake-up clock is frozen too, so a transition can only ever
*delay* a shell breaking free in your hands, never trigger it.
