# Strategy: dynamic / self-driven entities (items, enemies, projectiles)

## When this applies
Adding any entity that moves under its own logic each frame **and** that the player can
collide with: powerup items (Super Leaf, Mushroom, Fire Flower, Star), enemies (Goomba,
Koopa, etc.), and projectiles (fireballs, hammers). The first such entity is `SuperLeaf`
(`game/object/level/reward/SuperLeaf.java` + `SuperLeafMotionManager`).

## Project vocabulary (do not confuse — decided 2026-08)
- **Animator** (`QuestionBlockAnimator`, `RaccoonAnimator`, `BaseLevelScenePlayerAnimator`):
  PRESENTATION only — given entity state, pick the sprite frame / flip / rebuild the quad.
  Never put physics or motion simulation here.
- **`...Capable` mixins** (`LevelScenePlayerMoveCapable`, `...ActionCapable`): the player's
  behavior/physics.
- **`PopMotion` + `PopAnimation`** (`CoinPopAnimation`, `ScorePopupAnimation`): DETERMINISTIC,
  NON-INTERACTIVE, FIXED-LIFETIME visual FX. Motion is a PURE function `f(tick) -> offset`
  (`verticalOffsetAt` / `textureIndexAt`), dies at `durationTicks()`. Do NOT put interactive
  or stateful/integrative-motion entities here.
- **`LevelObject` / `AnimatableLevelObject`**: interactive world entities with collision.
- **`MotionManager`**: registry-discovered (`getBeansOfType`), ticked every fixed 60Hz sim
  step by `GameEngine.simpleUpdate`. Both pure-FX managers and entity managers implement it today.

## Why a moving + collidable entity does NOT belong in PopAnimation
1. Its motion is stateful/integrative (velocity accumulates, sway reverses at a limit via an
   oscillation counter, phase transitions depend on prior state) — not a closed-form `f(tick)`.
2. Open-ended lifetime (until collected / off-screen), not a fixed `durationTicks()`.
3. It is interactive (`intersectsPlayer` -> `onCollisionWith`); PopAnimation has no player concept.

`tick()` on such an entity is BEHAVIOR/PHYSICS, not sprite animation — it stays with the entity.

## The dasm blueprint (why the abstraction is "Active Object", not "Animation")
The NES ROM uses ONE unified "Objects" system for leaves, mushrooms, goombas, koopas, and
projectiles — the same four-phase handler contract. Map project concepts to it:

| dasm handler | project equivalent | responsibility |
|---|---|---|
| `ObjInit_*` | `@PostConstruct init()` | spawn/initialize, apply spawn offsets/velocities |
| `ObjNorm_*` | `tick()` | per-frame behavior + physics; apply X/Y vel |
| `ObjHit_*` | `onCollisionWith(player)` | player-collision response |
| `ObjKill_*` | `expired` flag + `detach()` | death/removal from scene graph |

Object state lives in `Objects_X/Y/XVel/YVel/Var1/Var2/Timer/State` slots; velocities are 4.4
fixed-point (`value/16` = px/frame). Always port constants/tables from dasm (see `../AGENTS.md`
PRG index; items+enemies are `prg001`–`prg005`, shared object routines in `prg000`).

## Recommended future abstraction (extract only when the 2nd consumer arrives)
A tiny interface that `SuperLeaf` ALREADY satisfies informally:

```java
public interface ActiveLevelObject extends LevelObject, GameRenderer {
    void tick();                                   // ObjNorm
    boolean intersectsPlayer();                    // hit test
    void onCollisionWith(LevelScenePlayer player); // ObjHit
    boolean isExpired();                           // ObjKill state
    void detach();                                 // scene-graph removal
}
```

Then make the manager generic over `ActiveLevelObject` (`SuperLeafMotionManager` is really
an ENTITY manager, not an animation manager — a generic `ActiveObjectManager` holding a
`List<ActiveLevelObject>` with the tick/collide/remove loop is the natural home).

## When to extract (avoid premature abstraction)
- **Do NOT** extract for a population of one; a base lifted from a single example encodes that
  example's accidents. Concrete accident to watch: `SuperLeaf` ignores tile/world collision
  (leaves pass through blocks) — enemies will NOT. World collision must be optional/composed,
  not baked into a shared base.
- **DO** extract when the FIRST ENEMY lands, so the interface is validated by two genuinely
  different implementations (item that ignores tiles vs. enemy that walks on them, turns at
  ledges, reacts to stomp). Split world-collision into a separate concern/mixin at that point.

## Coordinate / rendering cheat-sheet (already solved in SuperLeaf)
- Sprite-pixel space: `TILE_SPRITE_SIZE = 16`, top-left origin, Y down. Store `pxX/pxY` as double.
- 4.4 FP velocity -> px: multiply by `1/16`. Apply `pxX += xVelFp/16`, `pxY += yVelFp/16`.
- World mapping (jME quad is bottom-left origin): `worldX = px/16`;
  `worldY = (rows-1) - pyPixels/16`; `z ~ 0.06` (FOREGROUND = 0.1, behind player).
- Left/right mirror (sprites authored facing LEFT): flip like the player animators —
  material `FaceCullMode.Off`, geometry `setLocalScale(-1,1,1)`, translate x by `+quadWidth`.
- Spawn Y offset from a bumped block (up-hit, `Player_BounceDir = 1`): leaf top-left =
  block top edge `- 14px` (dasm: bump-handler `-1` [`Var2 != 0`] + `ObjInit_SuperLeaf` `-13`).
  Emerges from the block's TOP, not inside its cell.
- Player hitbox AABB (sprite-px): `left = x+1`, `right = x+15`, `bottom = y+32`,
  `top = y + (large && !ducking ? 6 : 16)`. Standard AABB overlap test.

## Bean wiring pattern
- Entity: `@Prototype @Getter @RequiredArgsConstructor`, final ctor fields (`GameEngine`,
  `Offset`, `LevelScenePlayer`), `@Value` `ImageResource`, `@PostConstruct init()`. Create via
  `getBean(SuperLeaf.class, gameEngine, offset, player)` (varargs positional; prototype = not cached).
- Manager: `@Singleton @RequiredArgsConstructor implements AnimationManager`; `update()` loop:
  `tick(); if (!expired && intersectsPlayer()) onCollisionWith(player); if (expired) { detach(); remove(); }`.
- Dispensing: `RewardDispensingLevelObject.dispenseReward` switches on `getReward()` (`ItemType`);
  add a `case` + default `onXDispensed()` that calls `getBean(Manager).spawn(player, getOffset())`.

## Style (`../.editorconfig`)
4-space indent, `final` everywhere, K&R braces, 120-col limit, `Math.clamp` over `min`/`max`
combos, import groups (non-java/javax, javax, java, blank, static; alphabetical within group).
Javadoc should cite dasm handler / line references (project norm).

---

## Second consumer landed: `SuperMushroom` — a world-colliding item (added 2026-09)

`SuperMushroom` (`game/object/level/reward/SuperMushroom.java` + `SuperMushroomMotionManager`)
is the predicted "genuinely different" second `ActiveLevelObject`: an item that **walks on
tiles**, validating the interface against the leaf's "ignores tiles" behaviour. Built by
mirroring the leaf's structure (entity + manager + `ActiveObjectGrid` broadphase + score popup
co-render + dispensing switch case) but with the mushroom's own dasm motion.

### dasm blueprint (prg001 `ObjInit_PUpMush` / `ObjNorm_PUpMush`; prg000 `Object_Move`)
Two phases inside the single `tick()` (not `PopAnimation` — same reasoning as the leaf):

1. **Rise = `PowerUp_DoRaise`.** `Objects_Timer` starts `$3d` (61f). While `>= $2d` it holds
   still (the bumped block still hides it, ~16f). Below `$2d` it creeps up **1 px every 3
   frames** (`Objects_Var1` cycles 2→0, `Objects_Y--` on underflow) — emerges ~one tile.
   Un-collectable until `Objects_Timer2 = $10` elapses (`PowerUp_DoHitTest`).
2. **Move = `ObjNorm_PUpMush` + `Object_InteractWithWorld`.** Gravity `OBJECT_FALLRATE = $03`/f
   capped at `OBJECT_MAXFALL = $40` (4 px/f). Once grounded with `XVel == 0`,
   `PowerUp_BounceXVel` kicks a constant `$10` (1 px/f) **away from the player** (direction from
   `Mushroom_SetFall`: object left-of-player → rolls left, else right). Rests on solid floors;
   `Object_AboutFace` reverses `XVel` at walls. All velocities 4.4 FP (`/16` = px/f).

### World collision: reuse `StaticEnvironmentCollisionGrid`, don't reinvent
The item resolves tiles against the SAME grid the player uses. The player-facing probe methods
(`collidesAtOffset`, `handleCollision`) are **player-relative** (`fromPlayerOffset`) — do NOT use
those for objects. Instead use the ABSOLUTE-coordinate accessor:
`grid.getLevelObjectAt(Offset.of(col, row)).isCollidable()`. Fetch the grid lazily from any live
`LevelScenePlayer` (`gameEngine.getPlayers()` → instanceof → `getCollisionGrid()`); all players
index identical tiles. Treat out-of-columns / below-world as solid, above-world as open.
Ground probe: solid tile under feet-center (`floor((y+16)/16)` at center column) → snap
`pixelY = feetRow*16 - 16`, `yVel = 0`, `grounded = true`. Wall probe: solid tile at the
leading edge (right edge `floor((x+15)/16)` when moving right, else `floor(x/16)`) at vertical
mid-row → reverse `xVel`, don't advance into the wall this frame. This confirms the KB's
prediction: **world collision is composed into the specific entity, NOT baked into a shared
base** — `SuperLeaf` still ignores it. If/when a 3rd walker arrives, extract a `WorldCollision`
mixin/helper (the ground+wall probe pair above) rather than a monolithic base class.

### Manager difference vs. leaf
Identical to `SuperLeafMotionManager` except the broadphase insert is gated on
`mushroom.isCollectable()` (the `Objects_Timer2` window), so an emerging mushroom cannot be
picked up. Sprite is symmetric 16×16 (`sprites/reward/mashroom/mushroom_normal.png`), so
`positionSprite()` needs NO mirroring (dropped the leaf's negative-X-scale flip). Reward is
`SCORE_1000` (`PUp_GeneralCollect` → `Score_PopUp #$09`), same as the leaf. Grow/Super-suit
grant from `ObjHit_PUpMush` is still deferred (mirrors the leaf's deferred Raccoon grant).

### Build/test note
Maven wrapper on Windows/PowerShell must be invoked as `.\mvnw.cmd` (bare `mvnw.cmd` is not on
PATH). `.\mvnw.cmd test` → 29 tests green after this change.
