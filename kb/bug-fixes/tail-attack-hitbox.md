# Tail attack broke blocks a row too high

### Symptom
With the raccoon suit, jumping so the player's head pressed against a block would break a
breakable brick beside the player at **chest** level, not at the tail's real height. It
looked as though the whole vertical padding band on the facing side counted as "tail".

### What went wrong
The ROM has **two separate** tail tests, and the port used one hitbox for both.

**Objects** — `prg000.asm Object_RespondToTailAttack` — really is a box:
`X = Player_SpriteX + ($11 | -$0A)`, width `Temp_Var4 = $0A`, `Y = Player_SpriteY + $10`,
height `Temp_Var8 = $0F`. Fires on countdown frames `$0C` **and** `$09`. This was ported
correctly and is unchanged.

**Blocks** — `prg008.asm Player_TailAttack_HitBlocks` (:5444) — is a **single point**:

```
CMP #$09
BNE PRG008_B979              ; blocks strike on frame $09 ONLY
LDA Player_TailAttack_Offsets,Y
STA <Temp_Var10              ; Y offset
LDA Player_TailAttack_Offsets+1,Y
STA <Temp_Var11              ; X offset
JSR Player_GetTileAndSlope   ; ONE tile lookup
LDX #$04
STA Level_Tile_GndL,X        ; -> the tail's Level_Tile_Whack slot
JSR Level_DoBumpBlocks
```

with `Player_TailAttack_Offsets: .byte 28, -6 / 28, 21` (Y, X pairs).

`StaticEnvironmentCollisionGrid.resolveTailAttack` was iterating **every cell overlapped by
the object box**. At 15px tall that box straddles two tile rows whenever the player is not
tile-aligned. The worst case is exactly the reported one: the head probe sits at `y+6`, so
when the head is flush to a ceiling the sprite top is 6px above the row boundary, dragging
the box's top edge into the row above.

```
player y = topOfB - 6
  old box:  y+16 .. y+31  = topOfB+10 .. topOfB+25   -> spans rows B and A
  probe:    y+28          = topOfB+22               -> row A only
```

### What the fix does
`resolveTailAttack` now takes a probe point and dispatches to the single tile containing it.
`LevelScenePlayer` gained `getTailAttackBlockProbeX/Y()` (`+21`/`-6`, `+28`) alongside the
unchanged object box, plus `isTailAttackStrikingBlocks()` for the frame-`$09`-only gate;
`GameEngine.resolveTailAttacks` dispatches the two tests separately.

Regression cover: `CollisionGridTailAttackTest`, `CollisionGridStompDispatchTest`.

### Trap for future readers
The inline comments on `Player_TailAttack_Offsets` are **backwards** relative to the branch
above them. The code leaves `Y=0` when `Player_FlipBits != 0` and sets `Y=2` when it is
zero, so the `-6` entry is the flipped/left-facing one — the opposite of what its comment
says. The code reading is also the physically consistent one, and cross-checks against the
object path: `+21` falls inside the right box's `+17..+27` span and `-6` inside the left's
`-10..0`. The ROM's block probe is a point *inside* its own object box.
