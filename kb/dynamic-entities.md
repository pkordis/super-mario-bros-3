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
{ "walk": ["goomba_left.png", "goomba_right.png"] }
```

- **Folder = mode.** Modes are declared **per enemy family** behind the shared `EnemyMode` interface,
  which is all the generic machinery needs (the sprite folder). `EnemyLevelObject.getMode()` selects the
  folder, and `EnemyAnimator`'s cache is keyed by it, so a new mode is a new folder and nothing else.
  The whole Goomba family shares one `GoombaMode` (`NORMAL`/`RED`): the enum names only the leaf folder,
  while the root comes from each animator's own `framesPath` (`goomba` vs `paragoomba`), so there is
  nothing left for a per-entity copy to say. It is also what makes the stomp handover type-safe — a
  de-winged Para-Goomba passes its mode to the replacement `Goomba`, which used to work only because two
  separate enums happened to spell their constants alike (**SIMPLIFIED 2026-10**: `ParaGoombaMode` was a
  byte-identical duplicate and is gone).
  > **CORRECTED 2026-10 (SMB3-4).** This section used to claim that "a winged Goomba is a new mode
  > constant + a new sprite folder, not a new entity class", on the grounds that `$72`, `$73`, `$74` and
  > `$7C` all enter `ObjInit_GroundTroop`. **The shared init is the only thing they share.** `$73` has
  > its own per-frame handler `ObjNorm_ParaGoomba` (`prg004.asm:3011`), wholly separate from
  > `ObjNorm_GroundTroop`, and its own draw `ParaGoomba_Draw` (`:3130`) which adds two hardware sprites
  > on top of the Goomba body. A winged Goomba is therefore **its own entity class** — see the
  > Para-Goomba section below. Mode constants are for palettes (`NORMAL` / `RED`), not for behaviour.
- **Mode travels as a placement attribute.** `EnemyType` has one constant per *placeable variant*
  (`PARA_GOOMBA_NORMAL`, `PARA_GOOMBA_RED`) carrying the `EnemyMode`; `EnemySpawner` writes it as the single
  `EnemyMode.MODE_ATTRIBUTE` (`"mode"`) entry of the `LevelObjectData` it hands to `configure`. Every
  enemy must therefore override `configure` to consume it, or `LevelObject`'s default warns and discards.
- **JSON key = action.** `walk` now; `flyFrameTextures` / `squashedTexture` when those
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
  `EnemyStampService`, `EnemyLevelSceneGridStamper`), anchored on its **rendering-starter** tile. Placing it
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

## `EnemyStamp` vs `EnemyRecord` — domain/persistence split (added 2026-09)

`EnemyRecord` used to be passed all the way up into the editor UI and the runtime spawner, with its
behaviour (`isWellFormed`, `tileIdAt`, `renderingStarterTileId`) living on the persistence entity. Now it
follows the project's existing `Tile`/`TileRecord` shape:

| | type | package | holds |
|---|---|---|---|
| domain | **`EnemyStamp`** — `@Data @Builder @Prototype @NoArgsConstructor @AllArgsConstructor` | `model.game.enemy` | `Tile[][] tiles` — **resolved tiles**, not ids |
| behaviour | **`EnemyCapabilities`** (`sealed … permits Enemy`) | `model.game.enemy` | `getRows`/`getColumns` (derived), `tileAt`, `renderingStarterTile(Id)`, `containsTile`, `isWellFormed`, `getName` |
| persistence | **`EnemyRecord`** — `@Entity("enemies")`, `Serializable`, `@Id` | `model.repository` | `int[] tileIds` + `rows`/`columns`, no behaviour |
| conversion | **`EnemyConverter extends TilesExtractor`** | `util.converter` | `toEnemy`, `toEnemyRecord`, `toTileGrid` |

`Enemy.id` defaults to a random UUID via `@Builder.Default`, as `LevelScene.id` does.

### Tiles, not tile ids (revised 2026-09)

`EnemyStamp` holds `Tile[][]`, exactly as `LevelScene.LevelSceneLayer` holds `Tile[][]` while its stored
`LevelSceneLayerData` holds a flat `int[] tileIds`. `rows`/`columns` are **not stored on the domain
model** — they are the grid's own shape, derived in `EnemyCapabilities`, so they cannot desync.

Conversion is asymmetric, which is why it is a converter rather than a method on either model:
- **down** needs only the grid → `TilesExtractor.extractTileIds(Tile[][])` (reused, not reimplemented);
- **up** needs a `TilesProvider` to turn each id back into a tile → `toTileGrid(tileIds, rows, columns)`,
  filling unknown/short ids with `NULL_TILE` exactly as `toLevelSceneLayer` does.

`EnemyService implements EnemyConverter` and overrides `getTilesProvider()` to return `TileService` —
identical to `LevelSceneService`. `CreateEnemyFromImageWindow` calls `enemyStampService.toTileGrid(...)` to turn
the ids from `TileService.createEnemyPartTiles` into the new enemy's grid.

**What this deleted:** `EnemyTilesAssembler.resolveTiles` and its `TileService` dependency (the enemy
already carries its tiles), and the assembler dependency inside `EnemyLevelSceneGridStamper` — both now
read `enemy.tileAt(row, column)`. `EnemyRecordCapabilities` is gone too, since the up-conversion needs a
provider. `EnemyService.isEnemyPartTile` is now `enemy.containsTile(tileId)` instead of streaming a flat
`int[]`.

**Test-fixture trap:** `NULL_TILE.getId()` is **0**, and `toTileGrid` looks the fill tile up by that id.
A test provider that registers a stub under id `0` will have it returned instead of `NULL_TILE`, and
`isSameAs(NULL_TILE)` fails. `EnemyConverterTest` deliberately starts its ids at 1.

**`EnemyStampService` is the conversion boundary** (same role as `TileService` / `LevelSceneService`): it
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
`EnemyStamp` → record round-trip**) and `ui/editor/level/enemy/create/EnemyTilesGridPanelTest` (the anchor
default), which had been conflated in one class. Suite: 113 tests green.

---

## Para-Goomba — `OBJ_PARAGOOMBA = $73` (SMB3-4, 2026-10)

The hopping winged Goomba, ported as **its own entity class** `ParaGoomba`. See the CORRECTED note in
the sprites section above for why it is not a mode of `Goomba` — though it does wear the family's
shared `GoombaMode` palettes, since the body art it tints is the Goomba's own.

### What it shares with the Goomba, and what it does not

| concern | shared? | site |
|---|---|---|
| Init — face the nearest player, nothing else | **yes**, `ObjInit_GroundTroop` | `prg004.asm:34` |
| Walk speed `±$08`, facing IS direction | **yes**, `GroundTroop_XVel` | `prg004.asm:3019-3027` |
| Collision box `OAT_BOUNDBOX01` (left +1, w 13, top +2, h 8) | **yes**, same table row | `prg000.asm:413` |
| Body draw — the mirror pair, unmodified | **yes**, `GroundTroop_DrawMirrored` | `prg004.asm:3131` |
| `OA3_HALT_NORMALONLY \| OA3_SQUASH`, points, kill action | **yes**, byte-identical | `:244`, `:286`, `:328` |
| Per-frame handler | **no** — `ObjNorm_ParaGoomba` | `prg004.asm:3011` |
| `OAT_BOUNCEOFFOTHERS` | **no** — `$73` lacks it, `$72` has it | `prg000.asm:412-413` |
| `OA2_GNDPLAYERMOD` | **no** — `$73` lacks it, `$72` has it | `prg004.asm:201-202` |
| Collide table | **no** — `OCSPECIAL_KILLCHANGETO \| OBJ_GOOMBA` | `prg004.asm:119` |

The missing `OAT_BOUNCEOFFOTHERS` is a **correction to the ticket**, which listed `:413` as "the same
row the Goomba uses" — the *box* is the same row, but the bounce flag is not set. Two Para-Goombas walk
straight through one another, so `bouncesOffOtherObjects()` keeps the interface default of `false`.
`OA2_GNDPLAYERMOD` is deliberately unmodelled: the branch it selects (`PRG000_D205`) is a documented
no-op in the dasm itself, and the Goomba port does not model it either.

### Motion: one `DEC` is the whole "flying" feel

- **Net fall `+$02`/frame.** `Object_Move` applies `OBJECT_FALLRATE = $03` and caps at
  `OBJECT_MAXFALL = $40`; the handler then does one `DEC Objects_YVel`. Because the decrement lands
  *after* the cap, terminal velocity is `$3F`, not the Goomba's `$40`. Do **not** model wing lift any
  other way.
- **Hop cadence — `Objects_Var4`.** On a grounded frame with `Objects_Timer` expired: `Var4++` and
  `YVel = -$10`. On the 4th, `Var4 = 0`, `Objects_Timer = $50` and `YVel = -$30`. Object timers tick
  every frame unconditionally (`prg000.asm:1855`), so `$50` is 80 frames, spent sitting on the ground.
  At `+$02`/frame that is a 4.5 px little hop and a 37.5 px big one.
- **Ceiling** `DetStat & $08` → `YVel = $01` (a stall, not a bounce).
- **Grounded re-face every 64 ticks.** `Objects_Var5` increments every frame but is only *read* inside
  the ground-contact branch, so a beat that falls mid-flight is simply lost.
- **Four clocks, all separate**, which is the thing to get right:

| clock | ROM slot | rate |
|---|---|---|
| hop counter | `Var4` | per ground contact |
| hop cooldown | `Objects_Timer` | −1/frame |
| wing flap | `Var3` | +1/frame, **+3** while rising with `Var4 == 0`, pinned to `0` on the ground while `Var4 == 0` |
| body frame | `Counter_1 >> 3` | flat 8-frame toggle |

### Rendering: three quads, never pre-composed

`ParaGoomba_Draw` is the Goomba body plus two hand-written sprites, so `ParaGoomba` owns three
geometries. **Do not bake body+wings into composed frames** — `Var3` and `Counter_1` run at different
and varying rates, so baked frames would fuse two clocks the ROM keeps apart.

- Two wing patterns only: `$CD` raised, `$CF` lowered, chosen by `Var3 & $08` (**set** = raised).
- Each wing is one 8x16 sprite, **10 px above** the body's sprite top and **2 px outward** from each of
  the body's two 8x16 halves: left at `bodyX - 2`, right at `bodyX + 8 + 2`.
- New `Z_DEPTH_ENEMY_WING = 0.065f` (between items at `0.06` and enemies at `0.07`) so the wings pass
  **behind** the body, as OAM slots 2/3 do behind 0/1.
- The art ships all **four** wing files rather than two-plus-a-quad-flip, keeping `ParaGoombaAnimator`
  flip-free exactly as `GoombaAnimator` is. `ParaGoombaAnimatorAssetsTest` pins the mirror in pixels.
- `ParaGoomba` overrides `detach()`: the `ActiveLevelObject` default only knows about the body quad.

### Two stomps to kill — the slot is rewritten, not replaced

The first stomp does **not** kill it. `PRG000_D2B4` (`prg000.asm:3940`, the normal stomp path) tests the
collide-table entry's upper byte **with no attribute gate**, and for `$73` the entry is `$0872`, so it:
bounces the player `-$40`, awards `Score_Get100PlusPts`, overwrites `Level_ObjectID,X` with
`OBJ_GOOMBA`, **increments `Objects_HitCount` back**, zeroes `YVel` and returns. From the next frame the
object genuinely *is* a Goomba. The second stomp then finds `$72` with `OA3_SQUASH` and runs the
flattened 16-frame death already ported in `Goomba`.

> The `smb3.asm:3139` comment that `OCSPECIAL_KILLCHANGETO` "requires `OA3_DIESHELLED`" is **misleading**:
> that gate applies only to bump-from-underneath (`prg000.asm:3628`) and `Enemy_Kill`
> (`prg000.asm:6010`), not to the normal stomp — which is the path that governs de-winging.

Port shape:

- `ParaGoomba.onCollisionFromAbove` awards `SCORE_100`, bounces the player, hands the slot over and sets
  `expired`. It implements **none** of the aftermath — no fall, no grounding, no walk.
- `ParaGoomba.handSlotOverToAGoomba(hitCooldownTicks)` is the shared half of **both** defeat responses
  (see the tail-attack section): it builds the `Goomba` from the *live* pixel position, hands it the slot
  and registers it, returning it so the caller can act on it.
- `Goomba.adoptSlotOf(pixelX, pixelY, facingRight, hitCooldownTicks)` is the port of the in-place ID
  rewrite. It carries the **live sub-tile position** (the constructor's `Offset` is whole tiles and would
  snap a mid-air de-winging), carries `facingRight`, and starts `YVel` at rest per `PRG000_D31E`. It also
  sets an `adoptedSlot` flag that makes `spawnIntoScene` skip `faceClosestPlayer` (the direction is
  already settled) and `eraseFromBakedTexture` (the Para-Goomba already erased that cell).
- **`Objects_Timer2 = 8` is not optional.** The replacement appears inside the player who just stomped
  it, so without a brief hit-cooldown one stomp chains straight into squashing it and the enemy dies in
  one hit. `Goomba.hitCooldownTicks` ticks down in `motionUpdate` and folds into `isHittable()`, which is
  what keeps it out of the broadphase — structurally the same thing the ROM does by gating
  `Player_HitEnemy` on the timer (`prg000.asm:3685`).
- **Single-instance spawn.** `EnemySpawner.spawn()` is the level-setup pass and *resets* every enemy
  manager and animator first, which is wrong mid-level. New: `EnemySpawner.spawnOne(EnemyLevelObject)`,
  reached through `ActiveObjectGrid.spawnEnemy`, which registers one enemy and disturbs nothing else.
  The manager's per-tick pass then builds its sprite and starts inserting it into the broadphase.

### Terrain solidity is two-tiered, and that is not a player concession

Every active object probes the terrain through `StaticEnvironmentCollisionGrid`, and there are **two**
queries, not one:

| query | ROM tier | includes a jump-through panel? | used for |
|---|---|---|---|
| `isGroundSolidTile` | `Tile_AttrTable[0..3]` — "solid at the ground" | **yes** | the falling/feet probe |
| `isWallOrCeilingSolidTile` | `Tile_AttrTable[4..7]` — "solid at the head and walls" | **no** | the rising/head and side probes |

Those two halves of the per-tileset table are described in `smb3.asm:2763`, and the shared object tile
detection (dasm prg000 @ PRG000_C559, which builds `Objects_DetStat`) uses exactly that split: the wall
test and the ceiling test both `CMP Tile_AttrTable+4,Y`, while the floor test — taken when
`YVel >= 0` — uses `CMP Tile_AttrTable,Y`. The player's own routine does the same (`prg008.asm:1483`:
"Wall/ceiling-solid tile quadrant limits begin at `Tile_AttrTable+4`").

So a `PANEL_WALKABLE_TOP` (or a `LAKITU_CLOUD`, same `ONE_WAY_PLATFORM` category) stops a *falling*
Goomba and is thin air to a hopping Para-Goomba's head and to anything walking into its side. Using one
flat "is it solid" query for all three probes is how Para-Goombas ended up bouncing their heads on panel
tops the player walks straight through. There is no third tier and no per-object exception: if an object
needs terrain, it picks the tier by the direction it is moving.

Covered by `ParaGoombaHopTest` (`aPanelTopIsNotACeiling`, `aPanelTopIsNotAWall`, `aPanelTopIsStillAFloor`).

### The collision box — one per slot, and the wings are not in it

Worth stating flatly, because the wings make it look otherwise: **an SMB3 object has exactly one
bounding box, chosen by object ID from a constant table, and nothing that is merely drawn contributes to
it.**

- `Object_CalcBoundBox` (`prg000.asm:5435`) masks `Object_AttrFlags[ID]` for the box index and reads
  `Object_BoundBox` (`:277`). `$73` is `OAT_BOUNDBOX01` with no other flag (`:413`) — row 1,
  `left +1 / width 13 / top +2 / height 8`, byte-identical to `$72`'s. The Y pair is *(top offset,
  height)*, not the "Bot Top" the table's comment suggests: `ObjectObject_Intersect` (`:5523`) uses the
  second byte of each pair as an extent in a difference comparison, exactly as it does the X width.
- That one box serves **every** interaction: the player's body (`Object_HitTest` @ PRG000_D836, against
  `Player_BoundBox` = `3/10/5/25` for large standing), the tail (`Object_RespondToTailAttack`), left/right
  bouncers, and object-to-object (`Object_CalcBoundBox2`). There is no second box, no per-part box, and no
  wing box.
- `ParaGoomba_Draw` (`prg004.asm:3129`) is pure OAM: it reads the body's two sprite slots, writes two more
  at `bodyY - 10` and `bodyX - 2` / `bodyX + 8 + 2`, sets their attributes, and picks pattern `$CD` or
  `$CF` off `Var3 & $08`. It never touches a box, a hit test or a state.

So the hittable area is the **top 8 px of the 16x16 body** — the body's own lower 6 px are outside it too
— while the drawn figure is ~26 px tall counting wings. That gap is the whole reason the tail feels like
it misses (see `kb/bug-fixes/tail-attack-swing-side.md`), and it is authentic.

### Tail attack — one hit, killed *through* a replacement Goomba

`Object_RespondToTailAttack` (`prg000.asm:5906`) falls into `Enemy_Kill`, which gates on `OA3_DIESHELLED`
— `$73` does not carry it (`prg004.asm:244`), so the branch that would consult the collide table, and
with it `OCSPECIAL_KILLCHANGETO`, is never taken. One hit, not two, and no de-winged stage to survive.

`Enemy_Kill` itself (`prg000.asm:5996`) is worth reading before touching the throw: it sets
`Objects_Timer2 = 12`, queues `SND_PLAYERKICK`, scores `Score_Get100PlusPts`, sets state `6`
(`OBJSTATE_KILLED`), sets `YVel = -$50` for a tail kill (`-$30` for a bouncer — it branches on
`Temp_Var4 == $0F`, the caller's box width, which is `$0A` for the tail), picks `XVel` from
`EnemyKill_XVels` by which side the player is on, and ORs in `SPR_VFLIP`. `EnemyKnockMotion`'s `-80` and
its away-from-the-player X are those numbers.

The port nevertheless routes the kill through the **same slot handover the stomp uses** and then strikes
the replacement — `handSlotOverToAGoomba(0).onTailAttack(player)` — rather than owning a second copy of
the death throw:

- The corpse the ROM throws **is** a ground Goomba's. `$73`'s kill action is `KILLACT_JUSTDRAWMIRROR`
  (`prg004.asm:328`), so `ParaGoomba_Draw` stops running and the wings go from the frame after the blow;
  the tumble itself is the one shared `ObjState_Killed`.
- `hitCooldownTicks = 0`, because the kill path sets no `Objects_Timer2` — and a grace period would make
  `Goomba.isHittable()` swallow the very blow that created the replacement.
- `EnemyKnockMotion`, `SPR_VFLIP` and the `TailAttackFlashMotionManager` flash therefore live only in
  `Goomba`. `ParaGoomba` draws nothing inverted and holds no knock state; `positionSprite()` is
  unconditionally upright and winged.
- Score: the `SCORE_100` caption is queued on the **replacement**, which its own manager's
  `postCollision` awards later in the same tick (`GameEngine` order: managers update → bumps →
  collisions → `postCollision`). Queuing it on both would pay twice.

> Earlier revisions duplicated the whole throw inside `ParaGoomba`. If you are tempted to put it back,
> note that the only observable difference the handover makes is that the wings disappear one frame
> earlier — which is what `KILLACT_JUSTDRAWMIRROR` does.

### Killed and still hurting you, on the same frame — a ROM quirk, not a bug

Tail-whacking an enemy that is already touching you hurts you *and* kills it on one frame, and the
original does this too. The order is fixed by the object loop (`prg000.asm` @ PRG000_C9B6), which per
slot runs:

1. `Object_DoStateAction` → the enemy's handler. A ground troop's and `$73`'s both end in
   `JMP Object_HandleBumpUnderneath`, and *that* routine tail-calls `Player_HitEnemy` (`:3608`) — so the
   hurt/stomp resolution is the last thing the handler does.
2. `Object_HitByTailOrBouncer` → `Object_RespondToTailAttack` → `Enemy_Kill`.

The window is exactly one frame wide, and only reachable when the enemy's box overlaps *both* the
player's `Player_BoundBox` and the tail box — i.e. the enemy was already on top of you when you swung.
From the next frame the object is in state `6`, whose `ObjState_Killed` (`:3167`) only falls and draws
(`KILLACT_JUSTDRAWMIRROR`), and it is excluded from the hit tests by `Obj2Obj_EnByState` and by
`Objects_Timer2 = 12` — so a corpse can never hurt anyone. (Watch out for kill actions `5`
`Object_NormalAndKilled` and `9` `Object_NormalWhileKilled`: those *do* keep running the normal handler
while killed, so for objects carrying them the dying frames stay dangerous. Neither applies to `$72` or
`$73`.)

The port reproduces the ordering structurally: `ActiveObjectGrid.resolveActiveObjectCollisions` resolves
every body collision for a player and only then dispatches the tail strike. Pinned by
`ActiveObjectGridTest#bodyHitLandsBeforeTheTailStrikeOnTheSameTick` (ordering) and
`TailStrikeDuringABodyHitTest` (the same thing end-to-end, against a real player). Do not "fix" it by
moving the tail first — that would silently make the swing a shield.

> Ordering alone was not enough to make it happen: the hurt used to clear the swing counter on its way
> out, which disarmed the strike the same tick was about to resolve. See
> `kb/bug-fixes/hurt-cancelled-the-tail-strike.md` — and note that `Player_GetHurt` writes no part of the
> swing state.

### Deliberately not ported

- `Object_DeleteOffScreen`, which opens the handler — left to the central enemy expiry (SMB3-3). Only the
  fall-off-the-level expiry the Goomba already has is kept.
- `Object_HandleBumpUnderneath`, which closes it — the **block-bump** half only, which the port does not
  model; the same omission the Goomba makes. Its tail call into `Player_HitEnemy` is very much ported —
  that is the engine's collision pass, and the section above depends on where it sits.
- `OBJ_PARAGOOMBAWITHMICROS = $74`, the Micro-Goomba bomber. It shares this body draw and box but has its
  own handler `ObjNorm_ParaGoombaBomber`; the class is left open to it and it is **not** implemented.

### Open questions, resolved

1. **Is `RED` vs `NORMAL` a palette or `$73` vs `$74`?** A palette. `$73` carries `OA1_PAL1`
   (`prg004.asm:161`) where `$72` carries `OA1_PAL3` (`:160`), and `$74` is a different handler, not a
   second palette. So `RED` is the authentic ROM appearance and `NORMAL` is a project extension — kept as
   the default because it is the Goomba-palette body the art drop targets. The de-winged replacement is
   always a plain `NORMAL` Goomba, matching `$72`'s own `OA1_PAL3`.
2. **`red/` art is a placeholder.** The folder currently holds a copy of the `normal/` art so the mode
   loads and animates; replacing those six PNGs is a pure file swap.

### Testing

`ParaGoombaHopTest` (hop cadence, hop heights, the `$50` cooldown, net float rate and `$3F` terminal,
ceiling stall, wall about-face, the 64-tick grounded re-face and the beat that is lost mid-flight, the
shared box, no bump-off-others), `ParaGoombaWingClockTest` (8-up/8-down, the `+3` rising rate, wings
pinned through the cooldown, and more wing flips than body flips over one climb),
`ParaGoombaStompTest` (sub-tile handover, facing, rest `YVel`, the 8-tick untouchability, two-stomps-to-
kill, single expiry, no re-face and no cell erase), `ParaGoombaTailAttackTest` (the kill handover: the
replacement is struck on the spot with no cooldown, inherits the strike position, owes the single
caption, flips and launches on the second tick and sinks through the floor, while the Para-Goomba itself
drives none of it), `ParaGoombaModeTest`, `ParaGoombaAnimatorAssetsTest`.

The entity keeps the Goomba's testability contract: `motionUpdate()` never touches the scene graph
(`positionSprite()` no-ops while `spriteGeometry == null`), so a mocked `GameEngine`,
`StaticEnvironmentCollisionGrid` and `ParaGoombaAnimator` plus a `boolean[][]` of solids is enough, and
the package-private `faceClosestPlayer()` is called directly in place of `spawnIntoScene()`.
`.\mvnw.cmd test` → 374 tests green after this change.

---

## Koopa Troopa - `OBJ_GREENTROOPA = $6C` and `OBJ_REDTROOPA = $6D` (SMB3-6, 2026-10)

### One class for both colours - and why that is NOT a retreat from the Para-Goomba rule

The Para-Goomba section above establishes "a separate `ObjNorm_*` handler means a separate entity
class, and mode constants never encode behaviour". The troopa is the first case where the first half of
that rule had to be read more carefully, and the second half still holds.

`$6C` runs `ObjNorm_GroundTroop` - the Goomba's own handler - while `$6D` runs `ObjNorm_RedTroopa`
(`prg004.asm:69-70`). But `ObjNorm_RedTroopa` is not a different handler in any meaningful sense: it is
`ObjNorm_GroundTroop` with `PRG004_B283` spliced in, the branch that "keeps a red troopa from marching
himself right off an edge". Everything else is shared, and shared *in the ROM*, not just in spirit:

| Concern | Green `$6C` | Red `$6D` |
|---|---|---|
| Init | `ObjInit_GroundTroop` | `ObjInit_GroundTroop` |
| Draw | `Troopa_Draw` | `Troopa_Draw` |
| Bound box | `OAT_BOUNDBOX01` | `OAT_BOUNDBOX01` |
| Sprite geometry | `OA1_HEIGHT32 \| OA1_WIDTH16` | `OA1_HEIGHT32 \| OA1_WIDTH16` |
| Walk speed / gravity | `±$08`, `$03` capped `$40` | same |
| Palette | `OA1_PAL2` | `OA1_PAL1` |
| **Ledge guard** | **absent** | **`PRG004_B283`** |

Contrast the Para-Goomba, which earned its own class on *two* counts - `ObjNorm_ParaGoomba` is a
genuinely different handler **and** `ParaGoomba_Draw` adds hardware sprites. The troopas differ by one
conditional. Splitting them would have meant duplicating the init, the draw, the box, the walk, the
wall about-face and the gravity in order to express an `if`.

So `KoopaTroopa` is one class, `configure` reads the `EnemyMode.MODE_ATTRIBUTE` into `mode`, and a
private `turnsAtLedges()` (`mode == RED`) gates `guardAgainstLedge()`. **The knowledge still lives in
the entity, not in the enum** - `KoopaTroopaMode` remains a folder name with no behavioural accessor on
it, which is the part of the rule that matters. The next enemy should read the table above before
reaching for this precedent: one spliced branch justifies it, a different handler does not.

Default mode is `RED`, which is the more forgiving of the two to get wrong - a troopa that turns at an
edge it should have walked off stays on screen and reportable, whereas the reverse quietly leaves the
level.

### Palette: the two folders are a one-colour swap, derived not eyeballed

`sprites/enemy/koopa/troopa/{green,red}/` hold the same two 16x32 frames differing in exactly 43 pixels
each. That is forced by the ROM: the object attribute table picks `OA1_PAL2` or `OA1_PAL1`
(`prg004.asm:154-155`), and in `PalSet_Plains` (`prg027.asm:1474`) those two sprite palettes agree on
colour indices 1 and 2 and differ only at index 3 - `$16` red vs `$2A` green. The head and feet are
forced to `SPR_PAL3` by `Troopa_Draw` regardless of colour (`prg004.asm:4375`), so they are untouched.
RGB values came from the project's own capture (`captures/_probe/f0000.png`, where `$16` reads
`#B53120` and `$2A` reads `#5CE430`), not from a guess at a palette table.
`KoopaTroopaAnimatorAssetsTest.theTwoColoursDifferOnlyInTheShell` pins this pixel by pixel, so a future
re-extraction of one folder cannot silently drift from the other.

### Known-bad: the walk frames are not both 27-px bottom-anchored

Carried over from the commit that added the red troopa and **still failing**:
`KoopaTroopaAnimatorAssetsTest.artIsBottomAnchored` and `artFacesLeft` assert a uniform 27-px band of
art sitting against the canvas bottom (rows 5-31), and the shipped frames do not have it -
`koopa_troopa_walk_1.png` runs rows 6-31 (26 px) and `koopa_troopa_walk_2.png` runs rows 4-30, leaving
its bottom row empty. Frame 2's silhouette is therefore a pixel *higher* in its canvas, where
`Troopa_YOffByFrame = {$06, $05}` (`prg004.asm:4266`) says the odd frame is drawn a pixel *lower*. Two
things follow and both need settling before the art is trusted:

1. `KoopaTroopaFamilyEnemy`'s claim that the two frames "agree on where the feet are" is false as
   shipped, so `walkFrameBob()` is applied on top of a bob already baked into the art - and, having the
   opposite sign, cancels it rather than doubling it. The net on-screen bob today is nothing.
2. Settling it needs `Troopa_Draw`'s sprite layout reconstructed from CHR - head tip `$C1` at body
   `Y-16`, body 8x16 pair at `Objects_SpriteY - Troopa_YOffByFrame[frame]`, feet at body `+16` - and the
   frames re-composed from it, rather than the assertions being relaxed to match whatever is on disk.

### Testing

`KoopaTroopaWalkTest` (walk speed and direction, spawn facing, wall about-face, fall rate and terminal
velocity, floor alignment, walk cadence, non-collidability, the shell-only box, the 16x32 sprite, the
mirror compensation and the draw bob), `KoopaTroopaLedgeTurnTest` (red: the about-face, the double
`Object_ApplyXVel`, indefinite patrol of a narrow island, the `Objects_Var4` one-shot latch, re-arming
on landing - green: walking straight off the edge, never about-facing in the air, and the paired
red-vs-green comparison that pins `configure` as the switch), `KoopaTroopaAnimatorAssetsTest` (per-mode
descriptors, left-facing art only, 16x32 canvases, and the green/red palette-swap identity).

`.\mvnw.cmd test` -> 422 tests, 2 failing: the two pre-existing `artIsBottomAnchored` /
`artFacesLeft` assertions described above, which were already red on this branch before the green
variant landed.
