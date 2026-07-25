### What went wrong
The bug was a misport of the NES wall collision logic in CollisionGrid.handleCollision.
In the original (PRG008_BA77), wall correction only runs if a solid tile is detected and Player_LowClearance is zero. Both conditions are correctly ported. The issue
is in what happens next.
In the NES, when a wall is detected, the engine always snaps the player's pixel position to the tile boundary. Then it reads Player_XVel and uses BPL - "branch if positive or zero" - to decide whether to
zero velocity:
```asm
asm
LDA <Player_XVel
BPL PRG008_BAB1 ; XVel >= 0: skip the "subtract 2" step
DEY ; XVel < 0 (moving left): force Y=0 path → stop velocity
DEY
PRG008_BAB1:
TYA
BNE PRG008_BABC ; Y ≠ 0: wall was already flush, no stop needed
STA <Player_XVel ; Y = 0: player was overlapping → zero velocity
```

The velocity zeroing only happens when the player is geometrically overlapping the wall and was moving left (into a left-side wall). When XVel = 0, BPL branches and the stop
is skipped entirely. Zero velocity is treated identically to rightward velocity: position is corrected but velocity is untouched.
The Java port translated this into a gate: skip the entire correction unless movingIntoWall, and movingIntoWall was written as getDX() >= 0. That >= is
where the bug lives. At the end of every emexit frame, DX is explicitly zeroed. On the first post-emexit frame, the player is positioned flush against the solid tile bounding the emexit end
object - so the wall probe fires. DX = 0, 0 >= 0 = true → correction fires → position.setDX(0) → next frame, DX is still 0 → same condition → infinite loop. The
camera follows position.getX(), which never changes, so the game appears completely frozen.

### What the fix does
Changing >= 0 to > 0 (and <= 0 to < 0 for the left-wall case) matches the NES semantics: a stationary player does not trigger wall correction. The
position snap is skipped, handleHorizontalMovement builds DX normally on the next frame, and the player accelerates away from the wall smoothly.
Is this a proper fix or a patch?
It's a proper fix. The original >= 0 was a straightforward misread of the NES BPL instruction. BPL means "branch if the N flag is clear", which triggers on both positive
and zero values - but in the NES wall routine it is used to decide whether to apply the wall-stop, not the position snap. The position snap always fires on overlap regardless of velocity. The
Java port collapsed both "snap position" and "zero velocity" into a single gate, and then set that gate's condition incorrectly. Changing to strict > 0 / < 0 correctly separates
"is the player moving into this wall" from "is the player merely adjacent to it after stopping", which is exactly what the original 6502 logic expresses.
