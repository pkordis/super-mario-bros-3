# The carried shell woke too soon

**Reported as:** a feeling. "The amount of time the Koopa Troopa stays in its shell for — I have a
feeling in our implementation that interval is lower than in the original game."

The feeling was right, and it was right about exactly one half of the mechanic.

## What was actually wrong

A shell **left on the ground** was correct: 415 frames, ~6.9 s. A shell **in the player's hands** woke
up in the same 415 frames. In the ROM it takes **735 frames, ~12.25 s** — nearly twice as long.

`Objects_Timer3` is not decremented once a frame. The central object loop
(`dasm prg000.asm:1877-1900`) gates the decrement on `Counter_1` through a mask that is chosen **by the
object's state**:

```
LDA Objects_Timer3,X
BEQ  end           ; expired -> nothing
CMP #$60
BLT  dec           ; below $60 -> full rate, whatever the state
LDA #$01           ; <- shelled mask
LDY Objects_State,X
CPY #OBJSTATE_NORMAL
BEQ  dec           ; normal -> full rate
CPY #$04           ; OBJSTATE_HELD?
BNE  gate
LDA #$03           ; <- held mask
gate:
AND <Counter_1
BNE  end
dec:
DEC Objects_Timer3,X
```

So of the 255 units, the 160 above `$60` crawl — two frames each on the floor, **four each in your
hands** — and only the last 95 run at full rate:

| | throttled stretch | full-rate stretch | total |
|---|---|---|---|
| resting shell (`$01`) | 160 × 2 = 320 | 95 | **415** (~6.9 s) |
| held shell (`$03`) | 160 × 4 = 640 | 95 | **735** (~12.25 s) |

Our `runWakeUpClock` had a single `boolean wakeUpHalfTickPhase` and so could only express one rate. It
ran the held state at the shelled state's rate. The kb table in `dynamic-entities.md` had the `state 4`
row written down correctly the whole time; the code simply never read it.

## The fix

`wakeUpHalfTickPhase` (boolean) became `wakeUpThrottlePhase` (a 2-bit counter), and the mask is picked
from the state each tick:

```java
wakeUpThrottlePhase = (wakeUpThrottlePhase + 1) & WAKE_UP_THROTTLE_MASK_HELD;
final int throttleMask = state == HELD
    ? WAKE_UP_THROTTLE_MASK_HELD
    : WAKE_UP_THROTTLE_MASK_SHELLED;
if (wakeUpTicks >= SHELL_SHAKE_THRESHOLD_TICKS && (wakeUpThrottlePhase & throttleMask) != 0) {
    return;
}
wakeUpTicks--;
```

Both masks are named constants on `KoopaTroopaShellCapabilities` (`0x01` and `0x03`) so the reason for the
number is attached to the number. Because the mask is read from the *current* state rather than latched
at pickup, a shell picked up mid-drain switches rate at the moment it leaves the floor — which is what
the ROM does, the loop re-deciding every frame.

## What was checked and left alone

- **`Counter_1` is global in the ROM**, so every object shares one throttle phase; ours is per-object.
  The only difference is up to three frames of offset on a timer that runs for seven to twelve seconds.
  Nothing observes cross-object alignment.
- **Our clock is frozen during `KICKED`**, while the ROM's keeps draining (state 5 takes the `$01`
  mask). Unobservable: no routine reads `Timer3` in the kicked state, and every path back to a shell
  (`Object_SetShellState` at `prg000.asm:4030-4037`, and the group-collide re-shell at
  `prg000.asm:6045-6050`) rewrites it to `$FF` first. Both were checked; both reset.
- **The resting 415 was not touched**, and `KoopaTroopaShellTest` still pins it.
- **Frame rate is not the culprit.** Game logic runs on a fixed 60 Hz accumulator
  (`GameConstants.SIMULATION_FPS`, consumed in `GameEngine.simpleUpdate`) independently of the render
  rate, so a frame count is a real duration. This was the other candidate explanation for "feels
  shorter" and it was ruled out rather than assumed.

## Tests

`KoopaTroopaCarryTest.theShellBreaksFreeOfTheHands` now asserts the exact frame count instead of merely
"longer than the clock it started with" — the old assertion passed happily at either rate, which is why
the bug survived. The expectation is derived from the clock remaining at pickup rather than hard-coded,
since the fixture stomps the troopa and waits out its hit cooldown first:

```java
final int fullRateUnits = SHELL_SHAKE_THRESHOLD_TICKS - 1;
final int throttledUnits = clockOnPickup - fullRateUnits;
final int expectedFrames = throttledUnits * (WAKE_UP_THROTTLE_MASK_HELD + 1) + fullRateUnits;
```

The carry test's frame budget went from 600 to 900; at the correct rate the old budget would have
expired mid-carry and thrown.

Verified by unit test only — 598 tests green. Not verified by playing the game.
