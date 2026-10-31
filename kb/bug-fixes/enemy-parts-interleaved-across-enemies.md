# Enemy wings and feet interleaved with other enemies (SMB3-19)

**Reported as:** a Para-Goomba's wing renders over another enemy it collides with.

## What was actually wrong

Every enemy depth was a global constant. All bodies were drawn at `Z_DEPTH_ENEMY = 0.07`, and every
Para-Goomba wing and every waking Koopa foot at one shared `Z_DEPTH_ENEMY_WING = Z_DEPTH_ENEMY_FOOT =
0.065`. Sprites are in the `Translucent` bucket with the depth test off, and
`DepthOrderedGeometryComparator` sorts on world Z alone, with ties left in attach order (jME's sort is
stable). So all wings and feet of all enemies were drawn first, then all bodies. Within each group the
order was attach order.

Three visible cases follow from that, and the old code produced all three:

1. One Para-Goomba's wing over another Para-Goomba's wing (same depth, later attach wins).
1. A Para-Goomba's wing over a waking shell's feet, or the reverse (same depth).
1. One enemy split around another: a later Para-Goomba's body in front of an older enemy's body, while
   its wings stayed behind that older body.

A wing could never cover another enemy's *body*, so the report is case 1 or case 2 seen as "the wing is
on top". Which one was seen in the game was not confirmed by playing it.

The de-winged `Goomba` also jumped in the order: it was attached last, so on the frame it replaced its
Para-Goomba it moved in front of every body it had been behind.

## What the ROM does

The NES has no depth, only OAM order. Each object writes its sprites into its own contiguous
`Sprite_RAM` block (`SprRamOffsets`, dasm prg000.asm:590, stored to `Object_SprRAM,X` at :1908), so one
object's sprites always sort as a unit against another's. Inside the block, a Para-Goomba's wings
(`ParaGoomba_Draw`, prg004.asm:3130) and a shell's waking feet (prg000.asm:2398) take the slots after
the body, `+$08`/`+$0C` against `+$00`/`+$04`, so they pass behind their own body. A Troopa's head tip
and a Paratroopa's wing take the slots before it (prg004.asm:4323-4331, :4375, :4391-4433), so they pass
in front.

The ROM also rotates which object wins by adding `Counter_7to0` to the slot index every frame, which is
its sprite flicker. That rotation is not ported; a stable order is enough.

## The fix

- **Each enemy owns a base depth.** `EnemyLevelObject#getBaseDepth`/`setBaseDepth`, a field on
  `Goomba`, `ParaGoomba` and `KoopaTroopa`. `GroundWalkingEnemy#pixelToWorldOffset` returns it as the
  body's Z, so the shell inherits it through `shellDrawOffset`.
- **Allocated at spawn.** `EnemySpawner#spawnFrom` gives each placement spawn (respawns included)
  `EnemyDepthAllocator#next()`: `Z_DEPTH_ENEMY + slot * Z_STEP_BETWEEN_ENEMIES`, slot `0..63` in spawn
  order, wrapping, reset per level. Newest in front keeps the old body-over-body order.
- **Parts sit just off the base.** `depthBehindBody()` is `base - Z_OFFSET_ENEMY_DETAIL` (wings, feet).
  `depthInFrontOfBody()` is `base + Z_OFFSET_ENEMY_DETAIL`, for the Paratroopa wing and Troopa head tip.
  The offset (`0.0001`) is under half the step (`0.0003`), so no other enemy's quad fits between two
  parts of one enemy.
- **The hand-over keeps the depth.** `Goomba#replace` copies the Para-Goomba's base before registering.
  `EnemySpawner#spawnOne` allocates nothing, because the only mid-play births are successors.
- **Carried shells unchanged.** A held shell still draws relative to its holder, with its feet
  `Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT` behind it.
- `Z_DEPTH_ENEMY_WING` and `Z_DEPTH_ENEMY_FOOT` are gone, so nothing can use a global part depth again.

## The band

| what | depth |
|---|---|
| reward items | 0.06 |
| lowest enemy part (slot 0 - offset) | 0.0699 |
| highest enemy part (slot 63 + offset) | 0.0890 |
| a carried shell's feet (foreground player) | 0.092 |
| a carried shell | 0.094 |
| foreground player | 0.1 |

No baked layer sits in this range (the highest is NPCs at 0.05).

## Known limit

Two enemies share a base only if they were spawned a multiple of 64 spawns apart and are alive at the
same time. If they also overlap, they can interleave again, but each still keeps its own part order.

## Tests

- `EnemyDepthOrderTest`: two overlapping Para-Goombas, and a Para-Goomba with a waking shell, sorted by
  the renderer's own comparator, come out as contiguous units. Depth ranges do not overlap. Wings and
  feet (upright and upside down) are behind their own body. The allocator steps, wraps and resets, and
  the band stays between the reward items and a carried shell's feet.
- `ParaGoombaStompTest#theReplacementInheritsTheDepth`: the de-winged Goomba's body is at the
  Para-Goomba's depth.
- `EnemySpawnerTest`: spawns take consecutive slots, and a new level starts again from slot 0.

Verified by unit test only. Not verified by playing the game.
