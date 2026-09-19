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

---

## The first enemy landed: `Goomba` — a *placed* entity, not a dispensed one (added 2026-09)

`Goomba` (`game/object/level/enemy/Goomba.java`, `EnemyLevelObject`,
`EnemyMotionManager`, `enemy/motion/GoombaMotionManager`) is the enemy this file
predicted. It validated `ActiveLevelObject` a third time and exposed ONE genuinely new axis, which
is **not** the one predicted above (world collision — that was already solved by the mushroom):

> **A reward is spawned during play; an enemy is spawned by the level itself.**

That single difference drove every structural decision, so start here when adding the next enemy.

### Why `EnemyLevelObject` is a SIBLING of `RewardLevelObject`, not a subtype
Both are `ActiveLevelObject`s ticked by a `MotionManager`. But a reward is constructed by whatever
dispenses it, inside a fully running scene (root node, players, baked layers all present), whereas a
placed enemy is constructed **while the collision grid is still being built** — from
`StaticEnvironmentCollisionGridCapabilities.toCollisionGrid`, i.e. inside `GameEngine.setupLevel`,
which runs BEFORE `simpleInitApp`. At that moment there is **no scene graph, no baked layer geometry
and no player**. Consequences, all of them non-negotiable:

- `@PostConstruct init()` may only set `pixelX/pixelY` from the offset. Nothing else.
- The rest of `ObjInit_*` moves into `spawnIntoScene()` (interface method): face the player, build +
  attach the quad, erase the placement tile. The manager calls it once, when the enemy first enters
  the camera activation region — which is also exactly when the ROM's `Level_LoadObjects` loads an
  object into a slot as the level scrolls.
- Anything touching `gameEngine.getCollisionGrid()` in `init()` NPEs (the grid is the very thing
  being constructed). `findClosestPlayerTo` in particular.

### Placement path (tile -> entity), and the placement-marker erase
Enemies are painted in the editor as a tile on the **`NON_PLAYABLE_CHARACTERS` layer** and classified
as **`LevelObjectTypeMultiTiled.GOOMBA`**. Two rules follow, and they are the structural core of the
whole enemy path:

1. **An enemy type is ALWAYS multi-tiled**, even the single-tile Goomba. Multi-tiled here does not mean
   "big": it means *not a cell*. An enemy walks off the tile it was placed on, the player passes through
   it, and its appearance comes from its mode's `assets.json` rather than the painted tile — so the
   single-tiled path, which hands the object its tile's `ImageResource` and treats it as terrain, is
   simply the wrong path. `LevelObjectRecordTypeResolutionTest` enforces this for every future enemy.
2. **The static collision grid only builds single-tiled types.** `toCollisionGrid` resolves each cell's
   record via `LevelObjectRecord.findLevelObjectType()` and treats a multi-tiled one as a plain tile
   (falling back to the tile's own `COLLIDING`/`ONE_WAY_PLATFORM` category). It also no longer
   consolidates the NPC layer at all — surface view is `getTilesOfLayersBelow(NON_PLAYABLE_CHARACTERS)`.
   Net effect: **`StaticEnvironmentCollisionGridCapabilities` contains no reference to enemies
   whatsoever**, and a character painted over a block cell can no longer hide that block from collision
   (which the old consolidated view did).

Placement therefore happens on the dynamic side, from `GameEngine.setupLevel`:

```java
this.collisionGrid = levelScene.toCollisionGrid(this);   // static terrain
activeObjectGrid.spawnPlacedEnemies(this);               // dynamic actors  <- the ROM's Level_LoadObjects
```

`ActiveObjectGrid.spawnPlacedEnemies` is the entry point because an enemy belongs to the active-object
world; the scan itself lives with the enemies (`EnemySpawner`) so the broadphase stays a
broadphase. The scan walks the NPC layer, and a tile is a placement when its record resolves to a type
whose `getInstanceType()` is an `EnemyLevelObject`; an ENEMY-category tile without such a record logs a
warning rather than vanishing silently. World collision is then each entity's own business, resolved
against the terrain the grid publishes (`isSolidTile`) — never by being indexed in it.

The ROM keeps enemies out of the tile map entirely, so the painted tile is a **placement marker only**:
`spawnIntoScene()` erases it from the baked NPC layer, and the moving quad is the only Goomba from then
on. Painting an enemy on another layer means it is never scanned (and its tile stays on screen).

To reach `eraseFromBakedTexture` without implementing `AnimatableLevelObject` (which would send the
enemy into `findSuitableAnimator` and throw), the two baked-texture defaults were extracted from
`AnimatableLevelObject` into **`BakedLayerPainter`**; `AnimatableLevelObject` now just extends it.

**Editor authoring pipeline (added 2026-09):** an enemy is no longer a classified single tile. It is
authored through `Level Objects > Enemies > Create from Image...`:

```
PNG (validated as whole 16x16 tiles)  ->  CreateEnemyFromImageWindow
   grid view: parts drawn at 8x, transparency shown as the single-tile editor's checkerboard
   + Description, + EnemyType dropdown, + click a cell to move the RENDERING STARTER
   (defaults to the LOWER-LEFT cell - a ground walker is anchored at its feet)
   -> Save: parts persisted to `tiles` as ENEMY_PART (de-duplicated by sha256)
            + one EnemyRecord in the new `enemies` collection
```

- **`EnemyRecord`** (`enemies` collection, both stores): `description`, `enemyType`, `rows`/`columns`,
  flattened row-major `tileIds`, `renderingStarterRow`/`Column`. It is the counterpart of
  `LevelSceneRecord`, not of `LevelObjectRecord` — an enemy is a *grid* of tiles, not one classified tile
  — and it uses the same flattened `int[]` shape because neither store has an `int[][]` codec.
- **`EnemyType`** (`enumeration.enemy`) is the editor-facing identity and carries the runtime type:
  `GOOMBA -> LevelObjectTypeMultiTiled.GOOMBA`.
- **`TileType.ENEMY_SINGLE` is gone.** Every enemy tile is an `ENEMY_PART`, single-tile enemies included:
  an enemy is always a grid of parts with an anchor. (Safe to delete — the only ordinal-dependent tiles
  are the VIRTUAL ones, declared first, whose `ordinal()` is their persisted id.)
- Nothing is written until Save, so an abandoned import leaves no tiles behind. Saved parts are pushed
  into `TilePalettePanel` immediately (`addTile` is idempotent), so they can be painted at once.

**Placing an enemy — the Enemies palette:** the left pane has a second tab beside Tiles, listing one
section per enemy, headed by the enemy's name (its description, falling back to the `EnemyType` label)
and showing that enemy's parts re-assembled by `EnemyTilesAssembler` into the picture it was imported as.
An enemy is placed as a whole, not part by part:

- Selection is armed through `SelectedTileService`, which now owns **two mutually exclusive** kinds of
  selection — a tile or an enemy. The grid has to know unambiguously whether a click paints one cell or
  stamps a grid of parts, so that exclusivity has exactly one owner and both palettes route their clicks
  through it. (It also now clears the selection when a button is toggled *off*, which it previously left
  armed.)
- `EnemyLevelSceneGridStamper` writes the parts: the clicked cell takes the **rendering-starter** part and the rest fall
  at their stored offsets, clipped at the scene edges rather than wrapped. So the anchor an author chose
  when building the enemy is the cell they click to place it — the same cell the runtime spawner will
  look for.
- **Enemies are disabled unless the NPC layer is active**, and arming is released the moment the active
  layer moves away; `stampEnemy` refuses to write to any other layer regardless, so a stale selection
  cannot leak parts into the static environment. A brand-new blank scene has no NPC layer at all (it
  starts with Air + Land Decorations), so the palette stays disabled, with an in-panel hint saying why.
  Creating an enemy from an image is *not* gated on the layer — otherwise no enemy could ever be authored
  in a fresh scene.

**Remaining link (open):** `EnemySpawner` still identifies a placement through
`LevelObjectRecord.findLevelObjectType()`. To drive it from the authored data instead, look the painted
tile up with `EnemyService.findByRenderingStarterTileId(tileId)` (already written for exactly this) and
instantiate `record.getEnemyType().getLevelObjectType().getInstanceType()`. That is the whole change; it
also makes the anchor meaningful, since the starter cell is the one an author paints to place the enemy.

### Per-level lifecycle: enemy managers only
A manager singleton outlives a level, and placed enemies are handed to it rather than accumulated
during play, so without a reset the previous level (or the previous run of the editor's tester) leaks
its population into the next scene. `EnemyLevelObjectSpawner.spawnPlaced` calls
`MotionManager.Registry.resetEnemies()` before spawning, which resets **only** the
`EnemyMotionManager` beans — deliberately: animators are `MotionManager`s too, and a
blanket reset here would undo the registration `toCollisionGrid` has just done. `GoombaMotionManager`
extends its reset to clear `GoombaAnimator`'s per-mode asset cache (those textures belong to the
outgoing scene's asset manager).

### Activation window replaces `Object_DeleteOffScreen`
The ROM streams enemies: spawn on scroll-in, `Object_DeleteOffScreen` (prg000 `$D3E0`, the FIRST
thing `ObjNorm_GroundTroop` calls) throws them away on scroll-out. Here all placed instances live
for the whole level and the camera's `getActiveObjectRegion(2 tiles)` decides which of them tick:
un-reached enemies stand still at their cell, and one that leaves the window freezes instead of
being destroyed. A level authored in an editor expects a stable population; ROM-accurate respawn
would need the object list to survive deletion.

Note `hasFallenOffLevel()` (the `Objects_YHi >= 2` delete) **cannot fire today**: the collision grid
reports below-world rows as solid, so any falling object lands on the level's lower boundary. Kept
deliberately, for the day real pits exist. Same latent situation as `SuperMushroom`.

### Goomba motion (dasm prg004 `ObjInit_GroundTroop` :3753 / `ObjNorm_GroundTroop` :4001)
`OBJ_GOOMBA = $72` shares the ground-troop handlers with the Buzzy Beetle and the troopas.

- **Facing IS direction.** Every frame the handler re-reads the `SPR_HFLIP` bit and loads
  `GroundTroop_XVel` (`prg004.asm:3994`) from it: `-$08` unflipped, `$08` flipped — half a pixel a
  frame. So `Object_FlipFace` (flip only, no velocity negate) is a complete about-face, and the port
  keeps a single `facingRight` field and derives `xVel` from it each tick.
- **`ObjInit_GroundTroop` does exactly one thing**: face the nearest player
  (`Level_ObjCalcXDiffs` `$DD2C` + `GroundTroop_FlipTowardsPlayer`). A Goomba always appears walking
  *towards* the player. Tie / no player → unflipped → walks left.
- **Gravity** `OBJECT_FALLRATE = $03`/f capped at `OBJECT_MAXFALL = $40`, applied at the END of
  `Object_Move` (after the move + world detect), so a landed object carries one frame of fall speed
  into the next tick and is re-snapped by `Object_HitGround` (`$C515`).
- **Walk animation = 8 frames per flip.** Derivation (do not guess this): frame is
  `(Objects_Var5 >> 2) & 1`, and `Var5` advances through a fractional accumulator —
  `Objects_Var7 += $80` per frame (`PRG004_B353`, indexed by `LRBounce_Vel`, normally 0), carrying
  every 2nd frame. 4 `Var5` steps × 2 frames = 8.
- **NO ledge turn.** A Goomba walks off an edge and falls. The ledge-avoiding about-face belongs to
  `ObjNorm_RedTroopa` (`PRG004_B283`, the `Objects_Var4` branch that applies X velocity twice to undo
  the step) and is never reached by the ground-troop path.
- **Omitted on purpose:** ceiling bounce (`YVel = $01`) and `Object_HandleBumpUnderneath`. Both need
  an upward velocity, and the only sources are player interactions, which this port excludes.

### Sprites: an `assets.json` per mode, exactly like the player's suits
Enemy sprites are **not** injected as `@Value` image resources (a first attempt did that and was
wrong). They follow the player's arrangement, because an enemy has modes the way a player has suits:

```
sprites/enemy/<enemy>/<mode>/assets.json      # e.g. sprites/enemy/goomba/normal/
{ "walkFrameTextures": ["goomba_left.png", "goomba_right.png"] }
```

- **Folder = mode.** Modes are declared **per enemy** (`GoombaMode`) behind the shared `EnemyMode`
  interface, which is all the generic machinery needs (the sprite folder). The ROM gives each mode its own
  object ID while sharing the handlers: Goomba `$72`, Paragoomba `$73`, para-with-Micros `$74`, giant
  `$7C` — all four enter `ObjInit_GroundTroop`. So a winged Goomba is **a new mode constant + a new
  sprite folder**, not a new entity class. `EnemyLevelObject.getMode()` selects the folder.
- **JSON key = action.** `walkFrameTextures` now; `flyFrameTextures` / `squashedTexture` when those
  land. A record component per action, named to match the key — no loader changes either way.
- **Parsing is shared.** `PlayerAnimatorAssetsLoader` was generalised into
  `model/game/asset/loader/AnimatorAssetsLoader` (+ the `AnimatorAssets` marker that both
  `PlayerAnimatorAssets` and `EnemyAnimatorAssets` extend); the player loader now just supplies its own
  context and delegates. Do NOT write a second Jackson mapper.

### `EnemyAnimator`: singleton, unlike the player's per-suit animators
`EnemyAnimator<A, E>` (shared) owns the per-mode asset cache, the quad build and the texture swap;
a concrete animator (`GoombaAnimator`, `@Singleton`) supplies only three things: its folder name, its
assets record type, and `frameTexture(enemy)` — "which sprite is this enemy showing right now". That
is deliberately the ROM's split: `Objects_Frame` and the `Var5/Var7` accumulator are **per-object
slots** (so they stay in the entity, with the ported cadence), while the draw routine
`GroundTroop_Draw` is **shared code** serving every ground-troop mode.

It can be a singleton precisely because it holds no per-entity state — the player's animators are
prototypes only because each holds that player's animation clock. Two consequences:
- The asset cache is keyed by mode and must be **cleared per level** (textures belong to the scene's
  asset manager): `GoombaMotionManager.reset()` calls `animator.reset()` on top of the interface
  default.
- The entity calls `applyCurrentFrame` only on the tick the frame actually flips, so the animator
  needs none of the player's `lastRendered*` bookkeeping.

Frame-cycle length comes from the descriptor (`animator.walkFrameCount(goomba)`), not a constant, so a
mode with three frames animates without touching the cadence. The ROM hardcodes the 2-frame case as
`(Objects_Var5 >> 2) & 1`.

### Bounds: `getImageResource()` moved off `ActiveLevelObject`
The `getBounds()` default used to derive the box from a single `ImageResource` — a reward-item
accident. An enemy has no single image (its frames live in mode bundles) and the ROM sizes object boxes
from the constant `Object_BoundBox` table anyway, independent of the frame drawn. So:
`ActiveLevelObject.getBounds()` is now abstract, `getImageResource()` + the sprite-derived default
moved down to `RewardLevelObject`, and `EnemyLevelObject` defaults `getBounds()` from
`DimensionsPixels getBoundsPixels()` (16×16 for a Goomba).

### The Goomba's two frames are a MIRROR PAIR
`goomba_left.png` / `goomba_right.png`, 16×16 each (single tile). The ROM's two normal frames are
mirrored art, which is why `GoombaAnimator` cycles frames and applies **no** quad flip for facing: the
`SPR_HFLIP` facing and the `Objects_Frame` toggle mirror the same tiles, so they are visually
interchangeable. `GoombaAnimatorAssetsTest` pins it (and pins the descriptor's filenames, loaded
through the real loader with a recording sprite loader — no asset manager needed). If a future mode's
art breaks the mirror, facing becomes a real flip (leaf-style `setLocalScale(-1,1,1)` + `+quadWidth`)
and it belongs in the animator. The only other Goomba frame in the ROM is the squashed one
(`Objects_Frame = 3`, V-flipped by `ObjState_Squashed` `PRG000_D07E`) — stomp response, no asset yet.

### Player interaction is deliberately absent
`EnemyLevelObject.onCollisionWith` is a documented no-op and enemy managers **never insert into
`ActiveObjectGrid`** — an enemy currently walks through the player. `Player_HitEnemy`, the squash
state, stomping and the bump-from-below are the next slice of work. Z is `Z_DEPTH_ENEMY = 0.07f`
(in front of items at `0.06`, behind the player at `0.1`).

### Testing note
The entity is written so `motionUpdate()` never touches the scene graph (`positionSprite()` no-ops
while `spriteGeometry == null`, and drawing is delegated to a stubbable animator), which is what makes
`GoombaWalkTest` possible with nothing but a mocked `GameEngine`, `StaticEnvironmentCollisionGrid` and
`GoombaAnimator` plus a `boolean[][]` of solids; the package-private `faceClosestPlayer()` is called
directly in place of `spawnIntoScene()`. Keep that separation for the next enemy.
`LevelObjectRecordTypeResolutionTest` guards the "no enemy is single-tiled" rule for every enemy added
from here on; `TileServiceEnemyPartsTest` and `EnemyRecordTest` guard the authoring side (part
de-duplication, ENEMY_PART typing, row-major grid, lower-left default anchor) and the placement side
(`EnemyPaletteStampingTest`: assembly, missing-part tolerance, anchor arithmetic, edge clipping).
`.\mvnw.cmd test` → 94 tests green after this change.

---

## Enemy placement → runtime spawn bridge (fixed 2026-09)

**Bug that froze placed enemies.** A placed Goomba rendered but never moved because the two
halves of enemy placement were disconnected:

- **Editor side:** an enemy is authored as a grid of `ENEMY_PART` tiles (`EnemyRecord`,
  `EnemyService`, `EnemyLevelSceneGridStamper`), anchored on its **rendering-starter** tile. Placing it
  stamps those tiles onto the `NON_PLAYABLE_CHARACTERS` layer.
- **Runtime side:** `EnemySpawner` (called from `GameEngine.setupLevel` →
  `ActiveObjectGrid.spawnPlacedEnemies`) scans that layer and must turn each placement into a
  live entity handed to its `EnemyMotionManager`.

The spawner used to resolve tiles through `LevelObjectService.findByTileId` →
`LevelObjectRecord.findLevelObjectType`. But an `ENEMY_PART` tile has **no** `LevelObjectRecord`
(those are for tile-classified single-tiled objects), so resolution always returned empty, the
spawner logged "unclassified" and created nothing. The stamped tiles just sat there.

**The correct bridge** (already existed, was simply unused):
`EnemyService.findByRenderingStarterTileId(tileId)` → `EnemyRecord.getEnemyType()` →
`EnemyType.getLevelObjectType()` (always a `LevelObjectTypeMultiTiled`, e.g.
`EnemyType.GOOMBA → LevelObjectTypeMultiTiled.GOOMBA → Goomba.class`) → instantiate with
`getBean(instanceType, gameEngine, offset)` (mirrors `LevelObjectRecordCapabilities.toLevelObject`'s
multi-tiled branch) → `MotionManager.Registry.spawnEnemy`.

Only the **anchor** (rendering-starter) tile spawns; a multi-tile enemy's non-anchor parts resolve
to nothing and are skipped (use `EnemyService.isEnemyPartTile` to keep them from tripping the
"unclassified enemy tile" warning). Guarded by `EnemySpawnerTest`.

**Also fixed:** `StaticBeanFactory.getBean(clazz, args)` forwarded an empty varargs array straight
to Spring (`getBean(clazz, new Object[0])`), which demands a no-arg constructor instead of
autowiring and fails order-dependently for dependency-bearing beans. It now routes through the
(previously dead) `resolve()` helper, which omits the array when empty. Guarded by
`StaticBeanFactoryArgumentForwardingTest`.


---

## Player-vs-enemy collision & directional dispatch (added 2026-09)

Enemies now take part in player collision. Two pieces:

1. **Broadphase insertion.** `EnemyLevelObjectMotionManager.update()` inserts every live, on-screen
   enemy into `gameEngine.getActiveObjectGrid()` each tick (after `motionUpdate`, skipping expired /
   off-screen ones) — exactly as the reward managers do. The engine's per-tick order already supports
   it: `clear()` → managers `update()` (insert) → `resolveActiveObjectCollisions` → `postCollision`.

2. **Directional dispatch, opt-in.** `ActiveObjectGrid.resolveActiveObjectCollisions` still calls the
   undirected `onCollisionWith` on every intersecting object (rewards rely on it), and *additionally*,
   for objects that return `true` from the new `ActiveLevelObject.resolvesDirectionalPlayerCollision()`
   (only enemies), resolves the hit side and calls the matching `LevelObject` method — the active-object
   mirror of `StaticEnvironmentCollisionGrid`'s terrain dispatch:
   - **stomp** → `onCollisionFromAbove`: player descending (`getPosition().getDY() >= 0`) **and** its
     lower edge (`playerBounds.bottom()`) above the object's vertical midpoint.
   - **head-hit** → `onCollisionFromBelow`: player rising (`DY < 0`) with its top edge below midY.
   - **side** → `onPlayerOverlap`: anything else.

   **Opt-in, not blanket**, because rewards override directional methods for other reasons — a flipping
   `Coin`'s `onCollisionFromBelow` is guarded for the P-switch-brick case; broadcasting directional calls
   to every active object risks acting twice. Rewards leave `resolvesDirectionalPlayerCollision()` at its
   `false` default and keep receiving only `onCollisionWith`.

`Goomba.onCollisionFromAbove` defeats the Goomba (`setExpired(true)`; its manager detaches it next tick).
Deferred, as the rest of the enemy-hit port: the squashed sprite, the player's bounce, and the
side/underside **damage** paths (`Player_HitEnemy`'s hurt branch). Guarded by `ActiveObjectGridTest`
(4 directional cases + a non-opt-in reward case) and `GoombaWalkTest` (stomp defeats).


### Refinement: minimal-penetration side + minimum-overlap gate (2026-09)

The first cut decided the hit side by the enemy's vertical **midpoint** and fired `onCollisionFromAbove`
on the **first frame** the boxes touched. In play the stomp then triggered while the player still looked
a noticeable distance above the Goomba. Verified the boxes are not the cause: the Goomba art fills its
16×16 box exactly, and the shrunk-Mario sprite fills its 16×16 quad with feet at the quad bottom =
object-collision-box bottom (`getY()+32`) — box == sprite on both sides. So the trigger was firing at
~0px overlap.

`ActiveObjectGrid.dispatchDirectionalPlayerCollision` now:
- picks the side by the **least-overlapping axis** (minimum-translation): a hit counts as vertical only
  while X-overlap ≥ Y-overlap, so brushing a top corner while running into the side is a side hit
  (`onPlayerOverlap`), not a stomp;
- requires the leading edge to have overlapped by `DIRECTIONAL_HIT_MIN_OVERLAP_PIXELS` (**6px**, one
  shared constant — tune there, not per enemy) before dispatching `onCollisionFromAbove` /
  `onCollisionFromBelow`. A shallower/again-disagreeing hit dispatches nothing and waits for the next
  tick. This is "the collision from above evaluates to true when there's a small overlap," uniform for
  every enemy. `onCollisionWith` still fires on any contact (rewards unaffected).

Covered by `ActiveObjectGridTest`: stomp (8px, fires), shallow (3px, waits), from-below, side-hit,
non-opt-in reward.

---

## Animator / animation / motion manager are three concerns — where an enemy animator is reset (2026-09)

`GoombaMotionManager.reset()` used to call `getBean(GoombaAnimator.class).reset()`. Wrong owner: a
**motion manager** drives behaviour and physics for a population of entities; it does not own the
**animator** that draws them. (Project vocabulary, unchanged: *animator* = picks the sprite frame,
*animation* = a fixed-lifetime visual FX, *motion manager* = ticks a population.)

Enemy animators still need a **per-level** reset, because their textures are loaded through the outgoing
scene's `AssetManager` and must not leak into the next scene. That now lives in:

- **`Animator`** (`game/object/Animator.java`) — a supertype **above both animator families** holding the
  one member they share: `reset()`. Its nested `@Singleton Registry` is now the **single** animator
  registry (`GameObjectAnimator.Registry` and `EnemyAnimator.Registry` are both gone) and offers:
  - `resetAll(Class<? extends Animator> family)` — reset one family by subtype;
  - `getAll(Class<A> family)` — that family's members (animator counterpart of `getMotionManagers(Class)`);
  - `getTileBoundAnimators()` — the `GameObjectAnimator`s, typed for `add` / `registerLevel`;
  - `findSuitableAnimator(LevelObjectType)` — scoped to the tile-bound family, since only it declares
    `getSupportedTypes()` (an enemy animator is chosen by its entity and keyed by `EnemyMode`).
- **`GameObjectAnimator extends MotionManager<A>, Animator`** and
  **`EnemyAnimator extends Animator, GameRenderer`**. `GameObjectAnimator` re-declares `void reset()`
  abstract to resolve the Java clash between `Animator.reset()` (abstract) and `MotionManager.reset()`
  (no-op default) — otherwise the two interfaces are "incompatible" and it will not compile.
- **Call sites select the family:** `StaticEnvironmentCollisionGridCapabilities.toCollisionGrid` calls
  `resetAll(GameObjectAnimator.class)` then `findSuitableAnimator(...)`; `GameEngineRenderer` uses
  `getTileBoundAnimators()`; `EnemySpawner.resetEnemies()` calls `resetAll(EnemyAnimator.class)`
  alongside clearing the enemy populations.

### Why `EnemyAnimator` must NOT extend `GameObjectAnimator` (asked 2026-09)

Tempting, since it would give one registry — but the two are unrelated beyond `reset()`:

| | `GameObjectAnimator` | `EnemyAnimator` |
|---|---|---|
| ticked? | **yes** — it is a `MotionManager`, `update()`-ed every sim step | **no** — the entity calls `applyCurrentFrame` only on the tick its frame changes |
| population | holds a `List<A>` and animates all cells **in lockstep** | holds **none**; each enemy owns its frame index + `Geometry` |
| draws by | painting pixels into the **baked INTERACTIVE_OBJECTS layer texture** (`registerLevel` + `writeTile`) | swapping the texture on the **entity's own sprite quad** |
| keyed by | `LevelObjectType` (`getSupportedTypes`) | `EnemyMode` |
| type bound | `A extends AnimatableLevelObject` (`LevelObject + BakedLayerPainter`) | `E extends EnemyLevelObject` (`ActiveLevelObject + BakedLayerPainter`) — **not** an `AnimatableLevelObject` |

So the inheritance would (a) fail the type bound, (b) force four meaningless members (`add`,
`registerLevel`, `getSupportedTypes`, `update`), and (c) worst, enrol enemy animators in
`MotionManager.Registry` — the engine would tick them every frame. Hoisting the shared member into
`Animator` gets the single-registry benefit without either family pretending to be the other.

`GoombaMotionManager` is now a pure population holder (type + `activeInstances`) with no `reset()`
override. Guarded by `EnemySpawnerTest.levelSetupResetsAnimatorsAndPopulation`, which asserts
`resetAll(EnemyAnimator.class)` — i.e. by family, leaving the tile-bound animators' registration intact.

**Note on `resetEnemies()`'s scoping comment:** it says resetting *all* motion managers here would undo
the collision-grid build's registration — that refers to the tile-bound `GameObjectAnimator`s, which
*are* `MotionManager`s. Enemy animators are not, so resetting them here is safe.

---

## `Enemy` vs `EnemyRecord` — domain/persistence split (added 2026-09)

`EnemyRecord` used to be passed all the way up into the editor UI and the runtime spawner, with its
behaviour (`isWellFormed`, `tileIdAt`, `renderingStarterTileId`) living on the persistence entity. Now it
follows the project's existing `Tile`/`TileRecord` shape:

| | type | package | holds |
|---|---|---|---|
| domain | **`Enemy`** — `@Data @Builder @Prototype @NoArgsConstructor @AllArgsConstructor` | `model.game.enemy` | `Tile[][] tiles` — **resolved tiles**, not ids |
| behaviour | **`EnemyCapabilities`** (`sealed … permits Enemy`) | `model.game.enemy` | `getRows`/`getColumns` (derived), `tileAt`, `renderingStarterTile(Id)`, `containsTile`, `isWellFormed`, `getName` |
| persistence | **`EnemyRecord`** — `@Entity("enemies")`, `Serializable`, `@Id` | `model.repository` | `int[] tileIds` + `rows`/`columns`, no behaviour |
| conversion | **`EnemyConverter extends TilesExtractor`** | `util.converter` | `toEnemy`, `toEnemyRecord`, `toTileGrid` |

`Enemy.id` defaults to a random UUID via `@Builder.Default`, as `LevelScene.id` does.

### Tiles, not tile ids (revised 2026-09)

`Enemy` holds `Tile[][]`, exactly as `LevelScene.LevelSceneLayer` holds `Tile[][]` while its stored
`LevelSceneLayerData` holds a flat `int[] tileIds`. `rows`/`columns` are **not stored on the domain
model** — they are the grid's own shape, derived in `EnemyCapabilities`, so they cannot desync.

Conversion is asymmetric, which is why it is a converter rather than a method on either model:
- **down** needs only the grid → `TilesExtractor.extractTileIds(Tile[][])` (reused, not reimplemented);
- **up** needs a `TilesProvider` to turn each id back into a tile → `toTileGrid(tileIds, rows, columns)`,
  filling unknown/short ids with `NULL_TILE` exactly as `toLevelSceneLayer` does.

`EnemyService implements EnemyConverter` and overrides `getTilesProvider()` to return `TileService` —
identical to `LevelSceneService`. `CreateEnemyFromImageWindow` calls `enemyService.toTileGrid(...)` to turn
the ids from `TileService.createEnemyPartTiles` into the new enemy's grid.

**What this deleted:** `EnemyTilesAssembler.resolveTiles` and its `TileService` dependency (the enemy
already carries its tiles), and the assembler dependency inside `EnemyLevelSceneGridStamper` — both now
read `enemy.tileAt(row, column)`. `EnemyRecordCapabilities` is gone too, since the up-conversion needs a
provider. `EnemyService.isEnemyPartTile` is now `enemy.containsTile(tileId)` instead of streaming a flat
`int[]`.

**Test-fixture trap:** `NULL_TILE.getId()` is **0**, and `toTileGrid` looks the fill tile up by that id.
A test provider that registers a stub under id `0` will have it returned instead of `NULL_TILE`, and
`isSameAs(NULL_TILE)` fails. `EnemyConverterTest` deliberately starts its ids at 1.

**`EnemyService` is the conversion boundary** (same role as `TileService` / `LevelSceneService`): it
caches `Map<String, Enemy>`, converts on the way in (`toEnemy(record)`) and back down on save
(`toEnemyRecord(enemy)`). `EnemyRecord` is now confined to: the repository interface + its Mongo/Nitrite
implementations, the two DB configs, and `EnemyConverter`. Nothing in `ui.*`, `game.*` or the service API
mentions it.

Knock-on renames: `EnemyButton.fromRecord` → `fromEnemy`, `EnemyButton.getEnemyRecord()` → `getEnemy()`.
`EnemiesPalettePanel`'s private `resolveName(record)` was dropped in favour of `Enemy.getName()`, since
the description-or-type-label fallback is domain behaviour.

Tests: `EnemyRecordTest` was split into `model/game/enemy/EnemyTest` (grid addressing, derived extent,
well-formedness incl. a ragged grid, naming, `containsTile`),
`util/converter/EnemyConverterTest` (id→tile resolution, `NULL_TILE` fill, short-id padding, **record →
`Enemy` → record round-trip**) and `ui/editor/level/enemy/create/EnemyTilesGridPanelTest` (the anchor
default), which had been conflated in one class. Suite: 113 tests green.
