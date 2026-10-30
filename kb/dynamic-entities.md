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
- Player hitbox AABB (sprite-px): `LevelScenePlayer.getObjectCollisionBounds()` ports `Player_BoundBox`
  (prg000:5190). Large standing: X `[x+3, x+13)`, Y `[y+5, y+30)`. Small or ducking: X `[x+4, x+12)`,
  Y `[y+17, y+30)`. Half-open, with the ROM's literal extents (see the SMB3-15 note under the mushroom
  for what that means against the ROM's inclusive `ObjectObject_Intersect`).

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
   > **CORRECTED 2026-10 (SMB3-15).** The block spawns the mushroom at **block Y − 1**
   > (prg001.asm:2282-2289), so the 15 px creep ends exactly on the block top. During the hold
   > `PowerUp_DoRaise` branches out before drawing **and** before `PowerUp_DoHitTest`. `Timer2` runs
   > out on the last hold frame, so the **first rise frame is already collectable**. No "far enough
   > out" gate exists: the real limit is the `OAT_BOUNDBOX01` box (`1, 13, 2, 8`), shared by every
   > block power-up via `RewardLevelObject.powerUpBoundsAt`. A player standing on the block meets it
   > with exactly 4 px showing (k = 3, tick 23). From below it is never reachable. The ROM's
   > `ObjectObject_Intersect` is inclusive while `AxisAlignedBoundingBox.intersects` is half-open and
   > the player box keeps the ROM's literal extents, so the power-up box absorbs both missing pixels:
   > X `[x, x+15)`, Y `[y+1, y+11)`. `PowerUpBoundsTest` pins it against a transcription of the ROM
   > test at every whole-pixel offset. The creep also used to land one frame late (timer read before
   > its decrement); it now moves on the same frame the hit test first runs. The 16-frame protection is
   > one shared `PowerUpCollectProtection`. See `bug-fixes/the-mushroom-was-collected-inside-the-block.md`.
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
> **CORRECTED 2026-10 (SMB3-1).** Out-of-columns is **open**, not solid, for objects: the ROM's
> `Object_DetectTile` reads tile `$00` for any X off the level. Only the player's probes
> (`collidesAtOffset`) keep the sides solid. See `bug-fixes/enemies-bounced-off-the-level-edge.md`.
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
  attach the quad, erase the placement tile. The manager calls it once per instance, on its first tick,
  which is the ROM's `ObjState_Initializing` moment. Since SMB3-3 an instance is only built when the
  scroll's look-ahead reaches its placement's column (see "Off-screen lifecycle"), and a respawned one
  goes through `spawnIntoScene()` again (the erase repeats harmlessly).
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
broadphase. Since SMB3-3 the scan only records placements; instances are built as the scroll reaches
their columns (see "Off-screen lifecycle" below). The scan walks the NPC layer, and a tile is a placement
when its record resolves to a type
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

- Selection is armed through `PaletteSelectionService` (formerly `SelectedTileService`), which holds the
  one armed palette entry as a single `LayerGatedToggleButton` — a `TileButton` or an `EnemyButton`. The
  grid has to know unambiguously whether a click paints one cell or stamps a grid of parts, and a single
  field makes the two kinds mutually exclusive by construction; `getSelectedTile()` / `getSelectedEnemy()`
  read it back by type. Both palettes route their clicks through it, so the selection has exactly one
  owner. (It also clears the selection when a button is toggled *off*, which it once left armed.)
- `EnemyLevelSceneGridStamper` writes the parts: the clicked cell takes the **rendering-starter** part and the rest fall
  at their stored offsets, clipped at the scene edges rather than wrapped. So the anchor an author chose
  when building the enemy is the cell they click to place it — the same cell the runtime spawner will
  look for.
- **Enemies wear the out-of-layer look unless the NPC layer is active** — greyed thumbnail, dashed
  border, a tooltip naming the NPCs layer — but stay **clickable**: a click switches the active layer to
  NPCs through `ActiveLayerMenu.activate` and then arms the enemy, exactly as picking an off-layer tile
  does. That look and click flow live once, in `LayerGatedToggleButton` (`ui/editor/level/palette`),
  shared by `TileButton` and `EnemyButton`; `EnemyButton.OWNING_LAYER` comes from
  `TileType.ENEMY_PART`'s owning layer so the enemy-to-layer binding has one source. Activation runs
  **before** arming, because the palette re-sync it triggers releases any selection on the layer being
  left. `activate` returns whether the layer exists: when it does not, the button is un-toggled and
  nothing is armed (which also stops an off-layer tile in a blank scene being armed and then painted into
  Air, as it used to be).
- Arming is released the moment the active layer moves away from NPCs, by the menu or by a tile click;
  `stampEnemy` refuses to write to any other layer regardless, so a stale selection cannot leak parts into
  the static environment. A brand-new blank scene has no NPC layer at all (it starts with Air + Land
  Decorations), so there is nothing to switch to: there the entries are **disabled** outright, with an
  in-panel hint saying the scene has no NPCs layer. Creating an enemy from an image is *not* gated on the
  layer — otherwise no enemy could ever be authored in a fresh scene.

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

### Off-screen lifecycle: delete and respawn, as `Object_DeleteOffScreen` does (SMB3-3)
The ROM streams enemies, and so does this port. Before SMB3-3 every placed instance lived for the
whole level and froze when it left a 2-tile activation window. That has been replaced by the ROM's
model, with the ROM's thresholds:

- **Placements outlive instances.** `EnemySpawner.spawn()` only *reads* the NPC layer into
  `EnemyPlacement`s (the ROM's `Level_Objects` + bit 7 of `Level_ObjectsSpawned`). Nothing is
  instantiated at setup.
- **Opening screen.** The first `ActiveObjectGrid.spawnEnemiesComingIntoView()` (run by `GameEngine`
  every tick, before the managers) is the ROM's start-of-level pass (prg005 `PRG005_BF49`): it spawns
  every unspawned placement whose column lies from 32 px left of the screen to 16 px right of it, and
  leaves `Scroll_LastDir` at "left".
- **Look-ahead column, every tick.** After that, `Level_ObjectsSpawnByScroll` looks at ONE column only:
  16 px past the screen's right edge if the screen last moved right, 32 px past the left edge if it
  last moved left (`LOSBS_LookAhead`). Every unspawned placement in that column spawns. Only the column
  counts, whatever the row. The check runs even while the screen holds still, so a placement released
  while it sits in the look-ahead column spawns again at once. One released anywhere else waits until
  the scroll brings the look-ahead onto its column.
- **Delete off screen.** `EnemyMotionManager.update` deletes a live enemy, before its tick, once its X is
  `<= screen left - 48` or `> screen right + 32` (`PRG000_D3CC`), and **releases** its placement
  (`Object_SetDeadAndNotSpawned`). In a non-vertical level the ROM's test is X only, so an enemy above
  or below the screen keeps running. There is no separate level-edge rule: at a level's edge the camera
  is clamped, and this same test removes an enemy that walks out of the level sideways.
- **Fallen out of the level.** Also before the tick: `Y >= 512` (`Objects_YHi >= 2`) deletes a live enemy
  and releases its placement (`Object_Delete`), or retires a defeated one (`Object_FallAndDelete`). A
  non-vertical SMB3 level is at most 27 rows (432 px), so the line is always below it; for a taller
  authored level the line drops to the level's bottom edge (`ActiveLevelObject.hasFallenBelowTheLevel`).
- **The dead stay dead.** A defeated enemy (`EnemyLevelObject.isDefeated()`: squashed, tail-struck,
  shell-killed) is exempt from the off-screen deletion and plays its death out wherever it is; when it
  ends, the placement is **retired** (stays spawned). This is `ObjState_Squashed` / `ObjState_Killed` →
  `Object_SetDeadEmpty`, which never clears the bit. Carried shells are exempt too (`ObjState_Held`).
- **Slot hand-over.** The Para-Goomba's Goomba inherits its placement (`handOverPlacement`), because the
  ROM rewrites `Level_ObjectID` and leaves `Objects_SpawnIdx` alone. If that Goomba leaves the screen
  alive, a Para-Goomba comes back.

The right-hand thresholds are measured from the screen's right edge rather than as `Horz_Scroll + $110`
/ `+ $120`, which is identical at the NES's 256 px and keeps a wider window from spawning in view.
`Scroll_LastDir` is taken from the camera's own movement; the ROM sets it in `Player_DoScrolling` even on
a frame where the clamp then holds the screen still, a difference that only matters at the level's ends.

Not ported: the one-spawn-per-call limit and the five general object slots (`PRG005_B911`), the
`Counter_1` throttle that tests each side of the delete only every fourth frame, the narrow
`Objects_UseShortHTest` variant, and vertical levels. One timing difference remains: the ROM keeps
spawning and deleting while `Player_HaltGame` is set (the spawn call precedes the halt check at
prg000:1835), where this port freezes the whole pipeline. With no slot limit and no throttle that is not
observable here (see the SMB3-3 bug-fix note).

A freshly spawned instance gets the ROM's init frame (`ObjState_Initializing`): on its first tick it only
runs `spawnIntoScene()` and moves from the next. The Para-Goomba's Goomba has none
(`EnemyLevelObject.hasAdoptedASlot()`), because the ROM only rewrites `Level_ObjectID`.

The lifecycle is pinned by `EnemyMotionManagerTest` (the manager's decisions) and `EnemySpawnerTest`
(the placements).

Below-world rows are still solid to the collision grid, so a *live* walker inside the level's columns
never actually falls out of the bottom; the rule is there for corpses and for the day real pits exist.

### Power-ups use the same delete, and never come back (SMB3-17)
Every power-up's normal routine calls `Object_DeleteOffScreen` each frame, after moving and before its
hit test: prg001 `ObjNorm_PUpMush`, `ObjNorm_PUp1UpMush`, `ObjNorm_SuperLeaf` (and the unported
`ObjNorm_StarOrSuit`, `ObjNorm_FireFlower`). So `RewardLevelObjectMotionManager.update` now ticks every
reward wherever it is (the 2-tile activation window is gone, as it went for enemies) and then deletes it
past the same edges, `ActiveLevelObjectMotionManager.isPastTheDeletionEdge` (48 px left / more than 32 px
right of the screen, shared with `EnemyMotionManager`), or at `hasFallenBelowTheLevel()`. The rewards'
own `hasFallenOffLevel()` checks are gone; a mushroom that rolls out of the level sideways is removed by
the same test through the clamped camera.

- **Gone for good.** A block power-up always goes in slot 5 (`ObjNorm_BounceDU` @ PRG001_A56F) and is
  not spawned from a placement: slot 5's `Objects_SpawnIdx` is `$FF` (reset at PRG000_C9B6 whenever a
  slot empties), so `Object_SetDeadAndNotSpawned` falls straight through to `Object_SetDeadEmpty`. Its
  block is already used. The port's rewards have no placement either, so deleting one is final: no
  caption, no points.
- **The hold is exempt.** `PowerUp_DoRaise` jumps to PRG001_A93D while the timer is `>= $2d`, past the
  delete at PRG001_A937, so a mushroom held in its block for its first 16 frames is kept
  (`RewardLevelObject.isExemptFromDeletionOffScreen`). From the first rise frame it can be deleted.
- Not ported, as for enemies (SMB3-16): the `Counter_1` throttle and deleting while `Player_HaltGame` is
  set.

Pinned by `PowerUpOffScreenDeletionTest`.

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
> **2026-10 (SMB3-15):** that accident was still live for the mushroom and the 1-Up, which inherited
> the sprite-sized box and could be collected through the block while rising. Every block power-up now
> overrides `getBounds()` with `RewardLevelObject.powerUpBoundsAt`. `Coin` keeps its own box.

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

### Where the Koopa Troopa's code lives

The troopa is split over three types by purpose, not by state, and the hierarchy is the shell first:

| type | owns |
|---|---|
| `KoopaTroopaShellCapabilities` | the base: everything the shell does - enter/wake, the wake-up clock, resting and bouncing, carry, kick, rolling, chain kills, crush kills - plus the motion helpers the walker shares (`restOn`, `applyGravity`, `faceClosestPlayer`) |
| `KoopaTroopaFamilyEnemy` | extends the shell with the family's drawing constants and all drawing: the state as the renderer reads it (`isShellShowing`, the shake, the waking feet), the walker, the shell, the feet, carried depth |
| `KoopaTroopa` | lifecycle (`configure`, `spawnIntoScene`, `detach`), walking and the ledge guard, the answer to each kind of contact, and the per-state tick dispatch |

The family extends the shell and not the other way round because there is no troopa without a shell.
The shell is a separate type at all because in the ROM it is not the troopa's code: `ObjState_Shelled`,
`ObjState_Held`, `ObjState_Kicked`, `Player_KickObject`, `ObjState_Killed` and `Object_ShellDoWakeUp`
are shared object-state handlers in prg000 that every shelled object runs, while only the walk lives in
the troopa's own prg004 handler.

The shell needs drawing but cannot know how, so it declares `positionSprite` and `showGeometryForState`
(supplied by the family) and `refreshShellFrame` (supplied by the class, which owns the animator).

The price of mixins is that they reach state through accessors, so `KoopaTroopa` exposes setters for
its motion and shell fields. They are the family's internals - nothing outside it should call them.

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

### The walk frames meet the bob halfway (clarified in SMB3-7)

`Troopa_YOffByFrame = {$06, $05}` (`prg004.asm:4266`) draws the odd walk frame one pixel *lower* than
the even one, and this project reproduces that as a draw-time quad offset
(`KoopaTroopaFamilyEnemy#walkFrameBob`). The art is **not** authored identically between the two frames,
and that is correct rather than a defect: `koopa_troopa_walk_2.png`'s silhouette sits one row higher in
its own canvas (rows 4-30 against `walk_1`'s 6-31), so the draw-time drop cancels at the feet and both
frames plant them on the quad's bottom edge. What survives the cancellation is the bob itself — the odd
frame reaches a pixel further up, so the troopa's head bobs while its feet stay planted.

The trap, and it cost a wrong "fix" before being understood: **measure the drawn band, not the raw
canvas.** Asserting that both frames carry art on canvas row 31 looks like the right invariant and is
not one — `walk_2` legitimately stops at row 30. `KoopaTroopaAnimatorAssetsTest` adds the frame's bob to
each measurement before asserting, so it pins the real invariant (both feet lines coincide once drawn,
and no frame reaches higher than 27 px above them) without over-constraining the art. Repad one frame
without the other and the feet start sinking.

### Testing

`KoopaTroopaWalkTest` (walk speed and direction, spawn facing, wall about-face, fall rate and terminal
velocity, floor alignment, walk cadence, the shell-only box, the 16x32 sprite, the mirror compensation
and the draw bob), `KoopaTroopaLedgeTurnTest` (red: the about-face, the double
`Object_ApplyXVel`, indefinite patrol of a narrow island, the `Objects_Var4` one-shot latch, re-arming
on landing - green: walking straight off the edge, never about-facing in the air, and the paired
red-vs-green comparison that pins `configure` as the switch), `KoopaTroopaAnimatorAssetsTest` (per-mode
descriptors, left-facing art only, 16x32 canvases, and the green/red palette-swap identity).

`.\mvnw.cmd test` -> 422 tests, 2 failing when this landed: the `artIsBottomAnchored` /
`artFacesLeft` walk-art assertions. **Resolved in SMB3-7** - see "The walk frames must agree on the feet
line" below; the art was wrong, not the test.

---

## Koopa Troopa collision and the shell state (SMB3-7, 2026-10)

### The headline: nothing in the game can kill a Koopa Troopa

`ObjectGroup03_Attributes3` gives `$6C`/`$6D` **`OA3_DIESHELLED` and withholds `OA3_SQUASH`**
(`prg004.asm:238`). Every defeat path therefore converges on `Object_SetShellState`:

| input | site | outcome |
|---|---|---|
| stomp | `PRG000_D2B4` (`prg000.asm:3940`) | `Timer2 = 8`, player `YVel = -$40`, **100 pts**, → shelled |
| tail wham | `Enemy_Kill` → `PRG000_DBB6` (`prg000.asm:5997`) | `Timer2 = 12`, `YVel = -$50`, `XVel = ±$08`, `FlipBits \|= SPR_VFLIP`, **no score**, → shelled |
| block bumped underneath | `Object_HandleBumpUnderneath` (`prg000.asm:3602`) | `YVel = -$30`, `XVel = ±$08`, `FlipBits = SPR_VFLIP` (**assignment** — facing wiped too), no flash, no `Timer2`, **no score**, → shelled |
| tail wham **on a shell** | `Obj2Obj_EnByState` (`prg000.asm:6094`) enables it | re-launched, re-flipped, **wake-up clock reset to `$FF`** |

So a shell can be juggled on the tail indefinitely. There is no second-hit kill — do not add one.
Only a kick removes a troopa, which is why the carry/kick chain exists at all.

**Resolved by SMB3-10**, which is where "the enemy can finally die" became true. See the kicking section
at the end of this document for the kill paths it brought: a held shell kicked into an adjacent wall, a
rolling shell striking another object (and two of them meeting), a kicked shell leaving the level, and —
added just after — a **carried** shell walked into another object, which kills both. Stomping or
tail-whamming a troopa still only ever shells it.

### `Objects_Timer3` is NOT decremented once a frame — the biggest trap in this ticket

The ticket said "255 ticks (~4.25 s)". It is **415 frames (~6.9 s)**. The central object loop
(`prg000.asm:1875-1899`) gates the decrement:

```
Timer3 == 0            -> nothing
Timer3 <  $60          -> DEC every frame
Timer3 >= $60, state 2 -> DEC every frame            (normal)
Timer3 >= $60, state 4 -> DEC when Counter_1 & 3 == 0 (held)
Timer3 >= $60, else    -> DEC when Counter_1 & 1 == 0 (shelled: HALF RATE)
```

160 units (`$FF`..`$60` inclusive, tested before the decrement) at half rate = 320 frames, then 95 at
full rate = 95. The dasm's own inline comment there ("every 4 ticks") is loose; read the branch. A
per-object phase counter reproduces this exactly for one object — `Counter_1` only matters for
cross-object phase alignment, which nothing observes. `KoopaTroopaShellTest` pins 415.

**The `state 4` row of that table is not decoration — it doubles the carry.** A held shell drains at
*quarter* rate, so the throttled stretch costs 640 frames rather than 320: **735 frames (~12.25 s)**
against 415 resting. That is what makes walking a shell across a level a plan rather than a gamble, and
it was the one row the first implementation dropped (see
`kb/bug-fixes/the-carried-shell-woke-too-soon.md`). The two masks now live as
`WAKE_UP_THROTTLE_MASK_SHELLED` / `_HELD` on `KoopaTroopaFamilyEnemy`, and the per-object phase is a
2-bit counter rather than a boolean so it can express both; `KoopaTroopaCarryTest` pins the held total.

Also note the ordering: the loop decrements **before** `Object_DoStateAction`, and
`Object_ShellDoWakeUp` is the first thing `ObjState_Shelled` does, so the frame the timer reaches zero
is the frame the troopa stands up. `Object_ShellDoWakeUp` then pops its caller's return address
(`PRG000_D155`), so the rest of the shelled routine is skipped on that one frame.

### The shake, precisely — three things on one clock

| `Timer3` | effect |
|---|---|
| `$FF .. $60` | still, and the clock is running at half speed |
| `< $60` | `Timer4 = Timer3 & 7` → **1-px horizontal jitter**, and the clock goes to full speed |
| `< $50` | **feet flicker**, on ticks where `Timer3 & PRG000_CAF1[Timer3 >> 4] != 0` |
| `0` | wake up |

Two details worth not re-deriving:

- **The jitter is always *leftward* and never an added offset.** `Object_ShakeAndCalcSprite`
  (`prg000.asm:4551`) sets the carry, conditionally `LSR`s `Timer4` so the carry takes bit 0, then does
  `Objects_X SBC Horz_Scroll`. A clear carry makes the subtraction borrow, i.e. the sprite X comes out
  one *less*. With `Timer4 = Timer3 & 7` (always `< $40`, so exactly one `LSR`) the jitter is on when
  the phase is even and non-zero — phase `0` leaves the carry set.
- **The flicker accelerates in rate, not in duty.** `PRG000_CAF1 = {$02,$02,$04,$08,$10,$10}` indexed by
  `Timer3 >> 4`, and `Timer3` counts *down*, so the index walks `4 → 3 → 2 → 1 → 0` and the mask shrinks
  `$10 → $08 → $04 → $02`. Work the bands out before asserting on them, because the obvious guess is
  wrong — the proportion of ticks showing feet is a flat **half** throughout; what changes is the run
  length, `8 → 4 → 2` ticks. Measured as on-runs per 16-unit band the ramp is `0, 1, 2, 4, 4`.
- **The topmost band shows no feet at all.** The gate is `Timer3 < $50`, but the mask for band 4 is
  `$10` — a bit never set anywhere in `64..79`. So the feet really begin at `$40`, a whole band later
  than the threshold suggests. Pinned by test rather than left to be rediscovered.

Wake-up assigns `Objects_FlipBits = ObjectHeld_WakeUpDir[side]` (`prg000.asm:3431`) — a **whole-byte
write**, which is why the same instruction that faces the troopa at the player also clears `SPR_VFLIP`
and rights it. `Objects_Var5` is not reset, so the walk frame clock resumes wherever it was.

### `isHittable()` is too coarse, and this is where it first shows

A shell must stay **in the broadphase** (`Obj2Obj_EnByState` keeps it tail-reachable) while being inert
to the player's body. Since `EnemyMotionManager.update()` gates insertion on `isHittable()`, that flag
has to stay `true` and the gating moves into the responses: a private `respondsToPlayerContact()`
(`state == NORMAL && hitCooldownTicks == 0`) guards the stomp, the from-below hit and the overlap, while
`onTailAttack` is guarded by nothing at all. The ROM arranges the same thing one level down —
`Player_HitEnemy` returns while `Objects_Timer2 != 0` (`prg000.asm:3683`) and routes a shelled object to
`Object_HoldKickOrHurtPlayer` rather than the hurt branch. **The carry work owns the proper split**; this
is the stopgap, and it is the second consumer that will justify the abstraction.

### The shell is simulated, not parked

`ObjState_Shelled` runs `Object_Move` every tick. Gravity `$03` capped `$40`; on a descending floor
contact `XVel` is halved (sign preserved) and `YVel` becomes `-(YVel / 4)` **only if that is `< -$02`**
(`CMP #-$02 / BGE`), which is what stops a settling shell twitching forever; ceiling → `YVel = $10`;
wall → `Object_AboutFace` (negate `XVel` **and** flip facing, unlike the walk's `Object_FlipFace`, since
a shell's velocity is not derived from its facing). Java's truncating `/` is the same arithmetic as the
ROM's negate-shift-renegate, odd negatives included.

A shell **does not inherit the red troopa's ledge guard** — `ObjState_Shelled` replaces
`ObjNorm_RedTroopa` wholesale. "Never walks off its platform" is a property of the walking state only.

### Reuse the launch constants, not `EnemyKnockMotion`

`EnemyKnockMotion` is a fire-and-forget parabola ending in `expired` — right for a Goomba, wrong for an
`OA3_DIESHELLED` object that has to arc, land and settle. Its constants are now `public`
(`LAUNCH_Y_VELOCITY_FIXED_POINT = -$50`, `BUMP_LAUNCH_Y_VELOCITY_FIXED_POINT = -$30`,
`KNOCK_X_VELOCITY_FIXED_POINT = $08`, gravity/cap) plus a `Direction.struckFrom(playerOnTheLeft)`
factory, so the troopa writes the ROM's one launch onto its own velocity and lets the shell state carry
it. `-$30` is also the left/right bouncer-block figure, so adding bouncers later needs no new constant.

### Two quads for the states, three for the waking shell, and the block bump is polled

- **Geometry, not texture.** `Troopa_Draw` is 16x32 and `Object_DrawShelled` is 16x16, so the state
  change swaps which quads are visible. All four are built once in `spawnIntoScene` — a troopa being
  juggled re-shells on every strike and must not rebuild a quad each time. The anchor is shared for
  free: the shell's top sits on the tracked Y and it is one tile tall, so its bottom edge is the same
  feet line the walking quad's is.
- **Upside down is object state, not an animation.** `SPR_VFLIP` survives for the whole shell life and
  is cleared only by the wake-up's whole-byte write, so it is a persistent flag driving a negative Y
  scale plus a one-quad-height lift — the `Goomba#positionSprite` mechanism, but keyed off state rather
  than off "a knock motion is in progress". The feet inherit bit 7 of the shell's attributes
  (`prg000.asm:2398`) and so invert with it.
- **Nothing pushes a block bump.** `Object_HandleBumpUnderneath` reads a tile
  (`Object_TileFeet2 == TILEA_BLOCKBUMP_CLEAR`); here the equivalent is polling
  `collisionGrid.isBlockBumpActiveAt(cell)` from `motionUpdate`, the arrangement `SuperMushroom`
  established. The residency lasts up to ten frames, so the `grounded` test is what stops it re-firing —
  the launch clears `grounded` immediately. Unlike the mushroom's, the enemy path's horizontal direction
  **is** player-dependent.

### Art: three quads for the waking shell, which is the ROM's own decomposition

`red/` and `green/` each gained `koopa_troopa_shell.png` (16x16) plus
`koopa_troopa_shell_foot_{left,right}.png` (8x16 each). **The feet are separate quads, not baked into
shell frames**, and the reason is not symmetry with the Para-Goomba's wings but a plain rendering fact:
the shell is drawn on *every* frame while the feet flicker in and out around it
(`prg000.asm:2333-2344`). Pre-composited "shell with feet" frames make the shell blink away along with
its own feet, which is exactly the bug a first attempt here produced.

Two further things fall out of the ROM and are worth not re-deriving:

- **The feet are on a different palette.** The shell takes the object's own (`OA1_PAL1` / `OA1_PAL2`),
  but the feet are forced to `SPR_PAL3` by `ORA #$03` (`prg000.asm:2403`), overriding it. So there is no
  such thing as a green foot — both colour folders ship byte-identical foot art, in the orange/pink the
  head and feet of the walking sprite already use. Only `koopa_troopa_shell.png` and the two walk frames
  take the `$16` → `$2A` swap.
- **The feet carry no vertical offset of their own.** `ObjWakeUp_FeetYOff = {10, -10}`
  (`prg000.asm:2148`) is absorbed into where the pixels sit in the 8x16 canvas — the art is
  bottom-anchored, so sharing the shell's anchor puts it where `+10` puts the ROM's pattern. The pay-off
  is that the `-10` case needs nothing either: the same negative Y scale and one-quad-height lift that
  inverts the shell carries the feet to the other side of it, which is all the second table entry says.
  Horizontally they do take the ROM's offsets — `Sprite_RAM+$03 - 3` and `Sprite_RAM+$07 + 3`, i.e.
  three pixels past each side of the shell for a 22 px silhouette.

Depth: `Z_DEPTH_ENEMY_FOOT` aliases `Z_DEPTH_ENEMY_WING`, because the feet are in the same two OAM slots
behind the body that the wings are (`Sprite_RAM+$08` / `+$0C` against `+$00` / `+$04`) and a lower slot
wins on the NES.

Visibility is `CullHint.Always`/`Inherit` rather than attach/detach — the feet flicker several times a
second and the walk/shell swap happens on every tail strike, so the scene graph has no business churning
at that rate. (`BaseLevelScenePlayerAnimator` set that precedent for "drawn this tick or not".)

`KoopaTroopaAnimatorAssetsTest` pins all of it: the 16x16/8x16 footprints, the shell's horizontal
symmetry, the feet as a mirror pair, the bottom-anchored foot art, the feet being identical across
colours, and the `$16` → `$2A` swap being pixel-exact everywhere else.

### Deliberately not done

Carrying and kicking; the `SND_PLAYERKICK` cue (no sound engine — a marker comment, as `SwitchBlock`
set the precedent); left/right bouncer blocks; `Objects_KillTally`; giant troopas and Para-Troopas; the
Bob-omb and Ice Block branches of `Object_ShellDoWakeUp`.

### Testing

`KoopaTroopaShellTest` (29 tests): hurt gating in both states, broadphase membership while shelled,
stomp→shell with its single payout, the tail wham's velocities/flip/no-score/cooldown, direction away
from the player, the re-strike clock reset, the arc and settle, the halving slide across four landings,
the rebound, the wall about-face, the block bump from both states, both shake thresholds, the
accelerating flicker rate and the empty top band, the feet belonging to the shell alone, the 415-frame
clock, the upright wake-up resuming the ledge guard, the persistent flip, the shell draw offsets, and
the floor-detection depth limit. Plus `KoopaTroopaWalkTest` and `KoopaTroopaLedgeTurnTest` from SMB3-6.

`.\mvnw.cmd test` → 456 tests green.

---

## `Object_WorldDetect4`'s floor-depth limit — the guard that stops an upward teleport (fixed 2026-10)

**Symptom.** A tail-whammed Koopa Troopa shell would occasionally be found resting on a panel top it had
never cleared — snapped up onto it from the middle of its arc.

**Cause.** `GroundWalkingEnemy.isGroundBelow` tested only "is the tile under the feet solid", with no
regard for how far into it the object already was, and `Object_HitGround`'s align-to-tile (`Y & $F0`,
ported as `restOn`) then moved the object up to that tile's top line. For a walker that only ever
descends onto surfaces the two are the same thing. For a shell they are not.

**The ROM has an explicit guard, and the dasm names it:**

```
; The only difference amongst the Object_WorldDetect[x] entries is the input value, which
; specifies the limit that an object should acknowledge a floor tile. E.g., Object_WorldDetect4
; means the object will not detect a floor if it is more than 4 pixels vertically down in to it.
```
(`prg000.asm:905`), enforced at `PRG000_C649` (`prg000.asm:1149`):
```
LDA <Objects_Y,X
AND #$0f          ; tile-relative vertical position
CMP <Temp_Var1    ; the limit
BGE PRG000_C65C   ; too far down into the tile -> RTS, no floor detected
```

`Object_Move` passes **4** (`Object_WorldDetect4`, `prg000.asm:3391`), and 4 is not arbitrary: it is
`OBJECT_MAXFALL / 16`, one frame of terminal-velocity fall. So an object descending onto a surface is
always within the limit on the frame its feet enter it, and the limit only ever rejects a surface the
object is *already sunk into*. **It is a crossing test expressed as a depth** — which is exactly the
right mental model when porting it.

**Why only the shell could reach it.** Two things have to coincide. A panel top is solid underfoot but
not as a ceiling (`isGroundSolidTile` vs `isWallOrCeilingSolidTile` — the two-tier terrain the
Para-Goomba section records), so a rising object passes straight through one. And near the apex of the
`-$50` launch the vertical speed is a fraction of a pixel while the horizontal is still `$08`, so the
shell slides *sideways* into a panel's column while straddling its top line — ten pixels sunk into it,
having approached from neither above nor the side the wall probe watches. The next descending frame then
aligned it up onto the panel. A walking Goomba or troopa never gets into that position, and a
tail-struck Goomba uses `EnemyKnockMotion`, which ignores terrain entirely — so the bug was latent in
shared code until the shell arrived.

**The fix** is the limit, in the shared probe (`GroundWalkingEnemy.isGroundBelow`) where the ROM has it,
so every ground walker gets it. `KoopaTroopaShellTest` pins both halves: the limit itself at 0/3/4/10 px
of depth, and the behaviour — a shell at its apex over a one-way platform is left alone and falls past
it, while one descending onto the same platform from above still lands on it. Both fail without the
guard.

---

## Carrying (SMB3-9)

### `CarriableLevelObject` is a sibling of `EnemyLevelObject`, not a subtype

Same reasoning as `EnemyLevelObject` beside `RewardLevelObject`, one axis over: carriability and
enemyhood are orthogonal. An Ice Block and a thrown brick are carriable and are not enemies; a Goomba is
an enemy and never carriable. `KoopaTroopa` implements both, which is the only reason the two ever meet.

`isCarriable()` is **per state, not per type** — the ROM encodes it as
`Objects_State == OBJSTATE_SHELLED` inside `Object_HoldKickOrHurtPlayer` (`prg000.asm:4042`), so a
walking troopa hurts you and only a shelled one goes in your hands. A type-level marker would have let
the player grab a walking troopa, which is why there is no type-level marker.

The carry *positioning* lives on the interface as a default (`carryAnchorFor`) rather than on the player,
because that is where the ROM puts it: `ObjState_Held` is the **object's** state routine and it reads
`Player_X`, `Player_Y` and `Player_Suit` directly (`prg000.asm:3041-3069`). `getCarryAnchorOffset()` is
the per-object correction on top; the ROM's one user of it is a giant object (`-$02` in place of
`+$0D`), which is out of scope, so for now every implementation returns zero.

**The turn-around tuck crosses the body towards the new facing, not away from it**, and
`ObjectHoldXOff`'s index arithmetic hides that. `Y = (facing right ? 0 : 1) + 2 + (PipeFace >= 5 ? 1 : 0)`
against `{$0B, -$0B, $04, -$04, $04, ...}` gives a right-facing player `-$04` for the first two posed
ticks and `+$04` for the remaining four: the object starts on the side the player came **from** and
swings across. Getting this backwards is easy, reads plausibly, and is invisible without the table in
front of you — it was caught in review, not in testing, which is why both facings and both sides of
`CARRY_TUCK_SIDE_SWAP_TICKS` are now pinned.

### The `isHittable()` split, finally

The previous section's "`isHittable()` is too coarse" prediction came true exactly as written. It is now
two questions:

- **`isHittable()`** — reachable in the broad-phase. Its manager inserts it only while this holds, so
  `false` makes an object untouchable by every path at once.
- **`canHurtPlayer()`** — whether a body contact costs the player a suit. Defaults to `isHittable()`,
  because for every enemy up to the shell the two coincided.

A shelled troopa is the first object that is the former without the latter. The old Javadoc argued one
flag was enough because "a defeated object's state routine never calls `Player_HitEnemy`"; the shelled
state *does*, and from there `Object_HoldKickOrHurtPlayer` — so it has to stay reachable or it could be
neither picked up nor kicked nor tail-struck. `KoopaTroopa.canHurtPlayer()` replaced the private
`doesNotRespondToPlayerContact()` stopgap verbatim (`state == NORMAL && hitCooldownTicks == 0`), and is
also the stomp gate, deliberately the same expression: the ROM reaches both past one `Objects_Timer2`
test.

`Obj2Obj_EnByState` (`prg000.asm:6094`) is the authority on which states stay reachable, and it is worth
reading before adding one: `Normal`, `Shelled`, `Held` and `Kicked` are all **enabled**; `Killed`,
`Squashed` and `Dying` are not. So `KoopaTroopa.isHittable()` is `state != KILLED` and nothing more —
and a *held* shell being object-to-object enabled is not an oversight.

One open thread there. `Object_BumpOffOthers` itself (`prg000.asm:2423`) tests `OBJSTATE_NORMAL`, where
`KoopaTroopa.bouncesOffOtherObjects()` is a constant `true` that SMB3-7 pinned by test with the comment
"Object_BumpOffOthers in the shell". Either the comment or the ROM reading is wrong. Left exactly as
SMB3-7 left it rather than reversed here, because the only consequence is a facing flip on a shell whose
facing is not read again, and widening it into a state-dependent method would change shelled behaviour
this ticket has no business changing. Worth settling when the kick lands.

### Two player flags, and why the second is not redundant

`Player_IsHolding` ("animation effect only") and `Player_ISHolding_OLD` (smb3.asm:1904-1905). The
**second** is what gates a new pickup. Ported as `PlayerRuntimeState.carriedObject` (a reference) plus
`wasCarrying`, snapshotted at the top of `updateFrame` beside `tickTailAttackCountdown` and above every
early return.

The lag is load-bearing because of the engine's tick order, which matches the ROM's: players update,
then the object loop runs (`motionUpdate` → `ObjState_Held`, where the release is detected), then the
player collision pass runs (`onPlayerOverlap` → the pickup test). Release and pickup therefore land in
that order **within one tick**, and testing the live reference in the second would let the frame that
dropped one shell take the next.

`canPickUpCarriable()` ANDs the lag flag with `!isCarrying()`, and the second conjunct is **not** in the
ROM. The ROM gets away with the lag alone because it has eight object slots and a flag: two carriables
overlapping the player on the same pass both go to `OBJSTATE_HELD` and it carries whichever it draws. A
single reference cannot — the other object would be stranded in a state nothing ever leaves.

### A reference does not lapse the way a flag does

The ROM re-asserts `Player_IsHolding` every tick from inside `ObjState_Held`, so a carry ends for free
the moment that routine stops running — a wake-up, a tail strike, anything. A reference does not, and a
carry still pointing at a troopa that has gone back to walking is a troopa stapled to the player. Hence
`KoopaTroopa.endCarry()`, called from **every** exit from `HELD`: the release, the wake-up, and
`enterShell()` (which is how a tail strike on your own held shell takes it off you). Adding a new way
out of the held state means calling it.

What it deliberately does **not** clear is the turn-around pose. `PRG008_AF2F` reaches the
decrement-and-draw on `Player_PipeFace` alone, *before* it tests `Player_IsHolding`, so a player who
drops the shell two ticks in keeps facing the camera for the remaining five — which is why
`renderCarryPose` tests the pose ahead of the carry rather than inside it.

Nothing releases on the death or transition path, and that is the ROM too: `ObjState_Held` draws the
held object and returns while `Player_IsDying` is set, so a player who dies holding a shell dies holding
it. The pipeline is frozen for the duration either way and the restart builds a fresh player and state.

### Carrying, per tick — and per *rendered frame*

The logical position and the sprite are updated at different rates, and conflating them is what makes a
held object visibly shake.

- **Logical position**: assigned once per simulation tick, in the object's own held routine, from the
  player's live position. This is what collision reads.
- **Sprite**: placed once per **rendered frame**, by the player's own visual step
  (`LevelScenePlayer.interpolateVisualPosition`), from the player's *interpolated* position.

The second is not optional. `GameEngine.simpleUpdate` runs the simulation at a fixed 60 Hz and then
re-places every player's node from `interpolateBetweenPreviousAndCurrent(alpha)` on every rendered
frame, so above 60 Hz the player slides smoothly between ticks. An object placed from the simulation
position lags its holder by up to a tick, which reads exactly as the shell shaking in Mario's hands.

Hence `CarriableLevelObject.carryAnchorFrom(player, x, y)` takes the position rather than reading it off
the player: the two callers hand it different answers on purpose. And `drawCarriedAt` places the sprite
without touching the logical position, so the renderer can never feed interpolated values into
collision. The held routine deliberately does **not** place the sprite — one owner, and it is the
player's visual step, which is what "update both at once" means here.

The scene-graph alternative — parenting the carried object's quad under the player's node — does not
work: `rebuildWithTexture` calls `node.detachAllChildren()` on every frame change, so anything attached
there is thrown away the next time the player's pose changes.

### The wake-up clock keeps running while held

`Object_ShellDoWakeUp` is called from `ObjState_Held` exactly as it is from `ObjState_Shelled`
(`prg000.asm:3507-3555`), so **hold a shell long enough and it breaks free in your hands**. At zero:

- clear of the world → `OBJSTATE_NORMAL`, facing the player, and the player is now standing inside a
  live troopa;
- touching anything → 100 points, `OBJSTATE_KILLED`, `YVel = -$40`, `XVel = 0`.

The probe is `Object_WorldDetectN1`, and the `N1` matters: `A = $FF` is the "no floor-depth limit"
variant (see the `Object_WorldDetect4` section above for what the limit is and why it exists), so this
asks only whether the shell is *touching* a surface, not whether it is landing on one. Pressing a shell
into a wall as it wakes is the case it exists for. `KoopaTroopa.isPressedIntoTheWorld()` is deliberately
its own probe rather than a reuse of `isGroundBelow`, which has the limit.

Note the carry's own geometry keeps the shell clear of the floor a grounded player is standing on: the
player's feet are at `Player_Y + 32` either size, and the 16px shell hangs from `+13` (`+15` when
small), so its bottom edge is a few pixels *above* the feet. Without that it would read as crushed on
every wake-up.

### Release is a kick, and SMB3-10 made it one

`onReleased` is **gone**, along with the drop-in-place it did. There is no "put the shell down" in the
ROM: `ObjState_Held` falls straight through to `Player_KickObject` the moment B is released
(`BVC Player_KickObject`, `prg000.asm:2819`), and the B-not-held contact jumps to the same label
(`prg000.asm:4042`). `CarriableLevelObject.onKicked(player, toTheRight)` replaced it; see the Kicking
section below.

Two scaffolds went with it. The release no longer sets `Objects_Timer2 = 8` (the kick sets `$10`
instead), and the correction that pulled a shell released inside terrain back to the player's own
column is gone — a kick into an adjacent wall kills the shell outright and a kick that clears the wall
about-faces its way out, so a stationary shell inside a wall is no longer reachable.

**What survived** is `isCarriable()`'s `hitCooldownTicks == 0` conjunct, which is still not the ROM
(which branches on the state before it reaches the `Timer2` gate). It keeps the frame a *stomp* lands on,
with the player still overlapping the fresh shell, from reading as a pickup.

What a release must still **not** do is go through `enterShell()`, which reloads `Objects_Timer3` from
`$FF`. A kicked shell keeps whatever is left on its wake-up clock — the kick never touches it.

### What holding actually restricts — the audit, because the ticket's list was wrong

prg008 reads `Player_IsHolding` in exactly **six** places. All six:

| site | what it does | here? |
|---|---|---|
| `:1376` PRG008_A6F2 | forces ducking off | **yes**, `handleDucking` |
| `:1713` PRG008_A86C | blocks grabbing a vine | no vines yet |
| `:2023` `GndMov_Frog` | zeroes `Player_FrogHopCnt` | Frog suit, out of scope |
| `:2581` PRG008_AC41 | blocks the star-invincibility **backflip** | no star invincibility yet |
| `:3182` PRG008_AF0F | selects the hold frame | **yes**, the mode animators |
| `:3213` PRG008_AF2F | arms the turn-around pose | **yes**, `handleHorizontalMovement` |

So, contrary to the ticket's table:

- **The P-meter charges while carrying.** `Player_PowerUpdate` (@ PRG008_A4C7) has no holding test, and
  neither has the single write of `Player_RunFlag` that feeds it (@ PRG008_AB75 — airborne, sliding, B
  held, `>= PLAYER_TOPRUNSPEED`, and nothing else). `:2581` is the somersault, not the meter.
- **A raccoon can fly and tail-wag with a shell in hand.** `Player_JumpFlyFlutter` and
  `Player_AnimTailWag` have no holding test. `:2023` is the Frog suit's hop counter.
- **The tail attack is not blocked either.** `Player_TailAttackAnim` (@ PRG008_B0CE) gates the swing on
  `Pad_Down` and its own countdown. The swing connects while you carry; it is simply never *drawn*,
  because the hold frame overwrites the frame the swing chose two branches later. The only thing in the
  ROM that clears `Player_TailAttack` here is the **kick** (@ PRG008_AF52) — which is SMB3-10's, and
  which is also the one addition the `neutraliseMotionForTransition` Javadoc anticipates.

None of those gates are implemented. The audit is repeated at the head of
`LevelScenePlayerMoveCapable` so it is read by whoever next wonders.

### Frame selection is a chain of overwrites, so the carry branch goes first

`prg008` picks `Player_Frame` by writing it repeatedly: movement, then duck, then tail attack
(`Player_TailAttackAnim`), then **hold** (@ PRG008_AF0F), then **face-forward** (@ PRG008_AF2F), then
kick (@ PRG008_AF52). Later wins. So `renderCarryPose` is tested *first* in every mode animator —
ahead of ducking and ahead of the tail swing — and the turn-around pose is tested inside it, ahead of
the hold frames.

Three details that are easy to lose:

- **No new clock.** The beat is the shared `Player_WalkFrame`. It advances while the player moves, and a
  player who stops dead is **forced to beat 2**, not left on whichever beat the last stride ended on —
  `PRG008_AE03` writes `#$02` outright when no direction is held and `XVel == 0`, which is the same
  thing `renderStill` already does on the ordinary path. Beat 2 is `holding_3` for the large suits and
  `holding_1` for small.
- **Airborne pins the cycle** to entry 0, or entry 1 when small (@ PRG008_AF15, off
  `Player_InAir_OLD`). Small Mario's cycle therefore starts on its *second* pose in mid-air.
- **Carrying is not a `PlayerMovement`**, so `frameChanged(state, orientation, frame)` cannot see the
  pose change on its own. `wasCarryPosed` is the same device, for the same reason, as
  `RaccoonAnimator.wasTailAttacking`.
- **The hold cycle is indexed by its own length.** `walkFrameIndex` is bounded by the *walk* sequence's
  length, which is four in every mode today and has nothing to do with how many hold poses a bundle
  ships; `holdFrame` wraps rather than relying on the coincidence.

### The carry frames are not all their mode's size

`normal`'s three hold poses are **16x32**, and `facing_forward.png` is 16x32 in both `normal` and
`raccoon`; raccoon's hold poses are 24x32 and `shrunk` is 16x16 throughout. Both modes' `QUAD_WIDTH` is
a fixed 24px, so routing a 16px frame through the mode quad stretches it 1.5x *and* mis-places the
right-facing flip, since `rightPadding` assumes slack the frame does not have.

The fix is `LevelScenePlayerAnimator.rebuildWithTextureDerivedQuad`, which takes the quad from the image
and the padding from `width - 16`. That is correct for **every** hold and face-forward frame in all
three modes with no per-frame table — which is what the alternative,
`RaccoonAnimator.tailAttackFrameWidth`, is. It has to be a separate method because
`BaseLevelScenePlayerAnimator` overrides the three-argument `rebuildWithTexture` as `final` to bind it
to the mode's specifications.

The `kick` frame is **not** covered by this and will need its own handling: it is right-anchored with
the foot overhanging leftward, which neither `rightPadding` nor the texture-derived padding can express.
`NormalAnimatorAssetsTest.theKickFrameIsRightAnchored` says so already.

### Z depth, and the camera activation window

**The player is drawn in front of what it carries**, and that is the ROM's ordering rather than a
preference — the ticket asserted the opposite and was wrong. An object's OAM slot comes from
`SprRamOffsets` (`prg000.asm:590`), every entry of which is `$40` or higher, while the player is rooted
at `$28` (`prg008.asm:480`, commented as such). A lower index draws in front on the NES. The hands are
part of the player's sprite, so this is simply what makes them visible around the shell.

There is one exception, and it explains a line that otherwise looks arbitrary: `ObjState_Held` forces
`Object_SprRAM = $10` whenever `Player_PipeFace` is non-zero (@ PRG000_CF1A), and `$10` is *below* the
player's `$28`. So for the seven ticks of the camera-facing turn-around — and in pipes — the carried
object is held out **in front** of its holder, towards the viewer. Both sides are implemented and
pinned.

`Z_OFFSET_CARRIED_BEHIND_PLAYER` and `Z_OFFSET_CARRIED_IN_FRONT_OF_PLAYER` are **offsets applied to
`PlayerVisibility.getPlayerZ()`**, not absolute depths, because the player's layer is not fixed: an
absolute value would leave the shell in front of terrain the player had just gone behind. The behind
case still lands ahead of `Z_DEPTH_ENEMY`, which is also right — a carried shell passes in front of a
walking Goomba. `Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT` keeps a waking shell's feet behind the shell
on either side of the comparison, as `Sprite_RAM+$08`/`+$0C` against `+$00`/`+$04` does
(`prg000.asm:2398`). And the carry depth composes with the persistent `SPR_VFLIP` lift rather than
replacing it: **you carry an upside-down shell upside down.**

`EnemyMotionManager.isExemptFromActivationWindow` exists because the window's premise does not hold for
a carried object. The window is there so an object the camera has not reached is not *simulated*; a
carried object is not simulating, it is being **placed**, from a player who is on screen by definition.
Skipping its tick would not pause anything, it would desync it from its holder and drop it out of the
broad-phase. Once released it is an ordinary object again, so the exemption is scoped to the carry and
no wider — which is what the second half of `carriedEnemyIsExemptFromTheActivationWindow` pins. The ROM
reaches the same place differently: `Object_ShellDoWakeUp` pops straight past `Object_DeleteOffScreen`
for a held object (@ PRG000_D155).

### Deliberately not done

- Any carriable other than the shelled troopa — but nothing in the interface assumes otherwise.
- Carrying through pipes (`Level_PipeMove`), Kuribo's shoe, statue form, giant objects.
- Bob-omb and Ice Block specialisations, and two-player carry.
- `bouncesOffOtherObjects()` is left as SMB3-7 pinned it — see the `Obj2Obj_EnByState` note above for
  the open question it leaves.

---

## Kicking, and the rolling shell (SMB3-10, 2026-10)

### The headline: now the Koopa Troopa can die

SMB3-7 recorded that *nothing* in the game removed a troopa — every defeat path on `$6D` routed back into
`OBJSTATE_SHELLED`. Kicking is what changes that, and it brings three kill paths with it:

- a held shell kicked into an adjacent wall (`prg000.asm:2905-2932`) — 100 pts, `OBJSTATE_KILLED`,
  `YVel = -$40`
- a rolling shell striking another object, and two rolling shells meeting (`prg000.asm:2600-2637`)
- a kicked shell leaving the bottom of the level, via the pre-existing `hasFallenOffLevel`

### No `KickableLevelObject`, and the ROM is the reason

Three entry points, one routine. Releasing B falls through `ObjState_Held` into `Player_KickObject`
(`prg000.asm:2817-2819`); a body contact with B *not* held jumps to the same label from
`Object_HoldKickOrHurtPlayer` (`:4053-4059`); and a stomp on a shelled object calls it outright
(`:3923`). The ROM has no attribute distinguishing kickable from carriable, so kickability is a property
of `CarriableLevelObject` rather than a sibling interface. The moment a type needs one without the other
is the moment to split them.

### `Level_ObjCalcXDiffs` returns "is the player to the RIGHT", and three tables read it

`prg000.asm:6447` — `Y = 0` when `Player_X >= Objects_X`. Getting this backwards silently inverts three
things at once, so it is worth stating what each table does with it:

| table | `Y = 0` (player right of object) | reads as |
|---|---|---|
| `PlayerKickFlipBits = {$00, $40}` | `$00` = **face left** | the player turns *towards* the object |
| `ObjectKickXVelMoving = {-$30, $30}` | `-$30` = kick left | the object is sent *away* from the player |
| `ObjKickXvel = {$18, -$18}` | `+$18` = roll right | the stalled roll comes **back at** the player |

Note the third disagrees with the other two in direction, and that is not a transcription error: a kicked
object that lands with no horizontal speed rolls *toward* the player's side. It is why a shell you
stopped comes back at you.

`Player_FlipBits` is `$00` facing **left** and `SPR_HFLIP` facing right (`prg008.asm:492` says so in a
comment). The project's art is authored facing left too, so the two agree.

### The hurt exemption is "is the shell ahead of you", not "are you moving away"

`prg000.asm:4080-4103`, three conjuncts in order: the player must be moving at all, the two must share an
X-velocity sign, and `Y` from `Level_ObjCalcXDiffs` must **differ** from "is the player moving right".
Decoded, that last one means the shell is on the *leading* side of the player's motion. Chasing your own
shell is safe; having it run you down from behind is not.

`Objects_Timer2` is the same counter as the stomp cooldown — `hitCooldownTicks` here — seeded with `$10`
by the kick rather than the stomp's `$08`. That 16-tick window is the only thing stopping the shell you
just kicked from killing you while it is still inside your hitbox.

### The re-drop is why a rolling shell is not a glider

Every single time a kicked object touches the ground it is aligned to the floor and immediately pushed
back off it at `YVel = $0C` (`prg000.asm:2504-2507`). Two details matter:

- the write lands **after** `Object_Move` has already applied gravity, so it *replaces* that tick's
  gravity rather than stacking on it — hence `tickKicked()`'s odd-looking "move, gravity, then re-drop"
  order
- the `XVel == 0` guard around the re-launch is defensive here. No path in this port yet produces a
  kicked object with zero horizontal speed (every kick writes `±$30`, and the about-face negates it), so
  `rollLaunchXVelocity()` is tested directly rather than through a tick. The ROM's guard is for the Ice
  Block and the Bob-omb, both out of scope.

### `Object_BumpOffBlocks` *is* the tail strike, and `Player_BounceObj` has no counterpart

`prg008.asm:5483`'s own header says it works "like the Player tail-attacked whatever block got hit", and
that is literally the port: `StaticEnvironmentCollisionGrid.resolveTailAttack(player, x, y)`, the same
single-tile terrain probe. Bricks break and `?` blocks pop whatever the player's size, which is correct —
a rolling shell is not size-gated.

**`Player_BounceObj` needs nothing written.** It exists in the ROM because a bumped block spawns a bounce
*object* whose init pushes `Player_YVel` down, and the flag suppresses that when an object rather than the
player caused the bump (`ObjInit_BounceDU`, `prg001.asm:978-986`). Nothing on this project's block-bump
path touches player velocity at all — the downward push a player gets from bumping a block comes out of
their own ceiling collision — so a shell-triggered block cannot bounce the player, by construction rather
than by a flag. Pinned by a test so it stays that way.

### The kill chain: `Score_Get100PlusPts` is a ladder, not a doubling

`A = tally; A += 5`, indexing `ScoreAdd_L/H` (`prg000.asm:699`, `prg007.asm:2122`). Entry 5 is 100 points,
so the ladder is **100 / 200 / 400 / 800 / 1000 / 2000 / 4000 / 8000 / 1-up** — doubling *except* at the
1000 step, which is why `Reward.forKillTally` is a transcribed table and not `100 × 2^tally`. Two caps,
both in the ROM: `Kill_Tally` is held at 8 (`prg007.asm:2155-2161`) and the score index is clamped at
`$0D` (`:2174-2178`).

`Objects_KillTally: .ds 5` (`smb3.asm:1784`) is allocated for object slots 0-4 only. That is a RAM budget
with no analogue here, so the tally lives on the object and every object has one.

**Known gap, worth its own ticket:** an enemy kill's `Reward` only ever becomes a *caption*.
`EnemyMotionManager.postCollision` spawns a `ScorePopupAnimation` and never calls
`PlayerData.add(reward.getData())`, which predates this ticket — stomping a Goomba has always shown "100"
and added nothing. So the chain's escalation is visible and correct, and the 1-up at the top of a long
chain awards no life, for the same pre-existing reason. Fixing it touches every enemy, not just this one.

### Two kicked shells meeting, and the one place the port simplifies the ROM

The ROM awards **crossed**: our shell's popup gets the *other's* tally at *our* position, and then our
tally at the *other's* position (`prg000.asm:2616-2637`, via `Score_Get100PlusPtsY`'s `TAX` from a stale
`Y`). Since `pendingScoreReward` holds one value per object, each shell here pays out its own accumulated
tally at its own position — same two awards, same two values, uncrossed.

Also dropped deliberately: `(X + Counter_1) >> 1`'s carry gates the object-to-object hit test to roughly
every other tick per slot (`:2634`). It is a CPU-budget trick that only ever delays a kill by a frame, and
porting it would make the chain non-deterministic for nothing.

### The lethal pass is additive, and that is a design constraint not a preference

`ActiveObjectGrid.resolveKickedObjectKills()` sits **beside** `resolveObjectToObjectBumps()`. The ROM
keeps them apart too — the bump is `Object_BumpOffOthers`, called from the shared object loop for
everything, while the kill is inside the kicked state's own routine. A rolling shell is also not required
to `bouncesOffOtherObjects()`, so it would not even be in the bump pass's candidate set. The pass
re-reads `isKickedHazard()` per candidate, because two shells meeting kill each other and the striker can
stop being a hazard partway through its own list.

### Carrying something into an enemy kills both — and it is a different routine again

`ObjState_Held` runs its *own* `ObjectToObject_HitTest` (`prg000.asm:3094`) and the block after it
(`:3106-3137`) kills **both slots**: `OBJSTATE_KILLED` and `YVel = -$30` on the victim, the same on the
object in your hands, a flat 100 points, and `ObjectToObject_HitXVel = {-$08, $08}` on the victim. A
carried object is a one-use weapon. That is the whole difference from a kicked one, which survives
everything it kills and carries on, and it is why `resolveCarriedObjectKills()` is a third pass beside
the other two rather than a branch inside the kicked one: folding them would put "I live" and "I die"
behind one call.

Three details worth not re-deriving:

- **The knock direction comes from the player, not from the carried object.** `TYA / TAX / JSR
  Level_ObjCalcXDiffs` (`:3130-3132`) re-points the index at the *victim* before asking which side of it
  the player is on. The two answers diverge only while the victim's centre sits between the player and
  what they are holding, which `CARRY_SIDE_OFFSET_PIXELS` leaves eleven pixels of room for.
- **Flat 100, never the ladder.** `Score_Get100PlusPts` is called with `A = 0` (`:3123`) and
  `Objects_KillTally` is never touched here. Walking a shell into three enemies in a row pays 100 each
  time; only a *rolling* shell builds a chain, and this one never gets to roll. The striker banks it, at
  the striker's slot, so the victim awards nothing.
- **The corpse keeps your momentum.** `Objects_XVel = Player_XVel` every tick it is held (`:3087-3088`)
  and the kill block only rewrites Y. Run something over and the body flies forward; stand still and it
  drops straight up and down. This port does not copy the velocity per tick — a held object's position is
  assigned outright — so `onKilledByCarryContact` reads it off the carrier at the moment of death instead.

`Objects_Timer2,X = 16` at `:3127-3128` is **deliberately dropped**: the object it guards has just been
killed and nothing reads the timer again. The *read* of the same timer at `:3097` is kept and is
currently unreachable, because this project's `isCarriable()` already refuses a shell inside that window
— the ROM has no such guard on the pickup and relies on the held-state test instead.

### `expire()` is the removal every active object can perform

The ROM writes `OBJSTATE_KILLED` into both slots and is done, which only reads as "killed" for an object
whose handler understands that state. For an ice block or a P-switch it just means *gone*. So
`CarriableLevelObject.onCarriedInto` ends on `onKilledByCarryContact()`, whose default is
`ActiveLevelObject.expire()` — a no-op on the base interface, routed to `setExpired(true)` by both
`EnemyLevelObject` and `RewardLevelObject`. A Koopa Troopa overrides it with the real death: the state,
the throw, and the award.

That is the only reason `expire()` exists as a method rather than the two sub-interfaces each declaring
`setExpired` and callers reaching for `instanceof`: a generic pass needs one way to get rid of something
it does not recognise. `CarryContactTest` exercises the default against a carriable that is not an enemy,
since none exists yet.

### What a shell kill does to a Goomba, and why it is the tail strike's death

`ObjectKill_SetShellKillVars` is state-only and knows nothing about what it is killing
(`prg000.asm:2689-2706`), so every victim dies the same way — and the vertical flip comes from the
**shared dead-object move** (@ `PRG000_D045`), not from any kill site. Which means a shell kill *is* the
tail strike's death with two numbers changed: the throw is `-$30` against the tail's `-$50`, and the knock
arrives already resolved from the striker instead of from the player's side. `EnemyKnockMotion.shellKill`
is `strikeFrom` with those two substituted; `Goomba.onStruckByKickedObject` is three lines on top.

A **Para-Goomba** dies outright, wings and all: there is no de-winging branch for a state-only kill to
take. Routed through the ground-Goomba handover anyway, because that is where this family's dying motion
lives.

Gated on the dying state rather than on `isHittable()`, deliberately: `Obj2Obj_EnByState`
(`prg000.asm:6095-6103`) disables object-to-object detection for the killed, squashed and dying states and
says nothing about `Objects_Timer2`, so a Goomba inside its post-spawn cooldown is still shell-food. In
practice `EnemyMotionManager` only inserts `isHittable()` enemies into the grid at all, so that cooldown
keeps them out of every object-to-object pass regardless — a pre-existing divergence, noted here rather
than widened.

### The roll ignores the facing, and its fourth beat ships pre-mirrored

`AND #~SPR_HFLIP` / `ORA ObjShell_AnimFlipBits,Y` (`prg000.asm:2657-2660`) **clears** the facing's
horizontal flip and rewrites it from the beat table, so a shell rolling left and one rolling right are
drawn identically — the facing never reaches the quad. The *vertical* flip survives that write untouched,
which is why an upside-down shell rolls upside down for the rest of its life.

What the beat table then asks for, `{$00, $00, $00, $40}` (`prg000.asm:2709`) — beat 3 is beat 1
mirrored — is shipped **as art**: `koopa_troopa_shell_spin_3.png` is `koopa_troopa_shell_spin_1.png`
already flipped. So `shellSpin` is four files for four beats, `applyShellFrame` is a plain index into it,
and a shell carries no horizontal flip on any beat. `isSpriteMirrored()` reduces to
`!isShellShowing() && facingRight`.

That lands on the same side of the line as the waking feet and the Para-Goomba wings, which also ship
their mirrors as art. The line is not facing-versus-beat — it is whether anything downstream has to be
*told* which way to flip. A facing the renderer already knows; a beat index it does not. An earlier pass
here kept a `SHELL_SPIN_FLIPS` table on the animator and a three-file `shellSpin` whose fourth entry
repeated its second, on the reasoning that a per-beat mirror is not worth a PNG. It cost a draw-time
X-scale that had to stay in lockstep with `mirrorCompensation(quadWidth)`, and two frames of the cycle
that no longer had their own file. Four files, no flip.

`KoopaTroopaFamilyEnemy.isSpriteMirrored()` stays as the generalisation that let the shell answer
differently from the walk at all, and `mirrorCompensation(quadWidth)` still follows it rather than the
facing. For a shell it now answers "no mirror", so the correction is zero — but the pairing is what
stops the 16 px misplacement that method has always warned about, so it is kept and pinned by a test.

One divergence: the ROM indexes off `Level_NoStopCnt`, a scene-wide counter, so every rolling shell is in
phase and a fresh kick does not restart the cycle. There is nothing equivalent to read here, so the
counter is per object and resets on each kick. The 2-tick rate is the same.

> Build note: swapping sprite bytes without changing a filename can leave a stale copy in
> `target/classes` — Maven's resource copy is timestamp-driven. A roll that plays the right *files* in
> the wrong *order* is this, not a code bug. `mvnw clean` is the check.

### The kick pose, and the one frame whose slack is on the wrong side

`Player_Kick = $0C` → 12 ticks of a single frame, spent by the frame-selection block at
`PRG008_AF52`. Three things ride on that one block, which is why they are implemented together:

1. it is the **last** frame write in `prg008`, so the kick beats the carry pose, the camera-facing turn
   and the tail swing alike — hence `renderKickPose` goes first in every animator
2. it opens by zeroing `Player_TailAttack` (`prg008.asm:3239-3240`), so the kick cancels a swing in
   progress. It has to land immediately rather than next tick: the object pass resolves body contact and
   *then* the tail, so a contact kick decided in the first half would otherwise still be swinging in the
   second
3. a **stomp** zeroes `Player_Kick` the instant the kick returns (`prg000.asm:3930-3932`), so this block
   never runs for it — which costs a stomp the kick frame *and* the tail cancel. Expressed here as
   simply not arming the pose, which has both consequences without undoing anything. Funnelling every
   entry point through one pose-arming helper is the mistake to avoid.

`kicking.png` is the only art in the project whose body is not flush to the canvas's left edge: the
kicking foot extends in the direction of travel, so the overhang falls on the **leading** side (3
transparent columns for `normal`, 2 for `raccoon`, and none on the right). `rightPadding` models trailing
slack only, so there is nothing for it to consume and the *unmirrored* draw is the one that has to move —
by `canvasWidth - bodyWidth`, which is the kick's body box inset. Hence
`LevelScenePlayerAnimatorSpecifications.kickLeadingOffset`, the mirror of `rightPadding`, applied for
this pose alone.

Measure that offset from the **body box**, not from the leftmost opaque pixel. The torso does not fill
its own box edge to edge (it starts 2 px in for `normal`, 1 for `raccoon`) and the foot reaches outside
it, so neither silhouette edge is the alignment reference. The right edge having *no* slack is what fixes
the box: it can only be the rightmost 16 columns.

### Deliberately not done

- Bob-omb (`prg000.asm:2844-2878`) and Ice Block kick specialisations.
- `Level_PipeMove`'s branch: a player moving through a pipe keeps holding and does not kick. Noted at the
  one site it belongs on; there are no pipes yet.
- `ShellKillFlash` — `ObjectKill_SetShellKillVars` also seeds a 10-tick flash at the victim's position
  (`prg000.asm:2698-2704`). The kill and the knock are ported; the flash is not.
- The player's global stomp chain. `PRG000_D2A2` scores a shell stomp from `Kill_Tally`, which is the
  *player's* running chain and not the per-object one a rolling shell accumulates. There is no global
  counter in the project, so a shell stomp pays a flat 100 — `forKillTally(0)` is exactly that, which
  makes it both right for a chain of one and the place to thread a real counter through.
- Two-player kick interactions.

---

## Scoring: the player's kill chain, and where points actually land (2026-10)

### A kill's award is given in a *later* pass than the kill

The ROM splits it. The kill calls `Score_PopUp`, which only **queues** the value into `Scores_Value`;
`Scores_GiveAndDraw` (`prg007.asm:2154-2189`) later walks that queue, *giving the points and drawing the
sprite* in one sweep. `EnemyMotionManager.postCollision()` is that sweep, which is why it does both:

```java
instance.setPendingScoreReward(null);
instance.getGameEngine().getPlayerData().add(reward.getData());
spawnScorePopupFor(instance, reward);
```

For a long time it only did the second half, so **every enemy kill was purely cosmetic** — a caption rose
and the scoreline never moved. The reward objects (coin, mushroom, leaf, brick) were never affected,
because they credit from their own `onCollisionWith` and only use the managers for the caption.

Two things to keep in mind if this is ever touched:

- **`add(Reward.Data)`, not `addPoints(...getPoints())`.** The top of the kill ladder is `ONE_UP`, which
  carries a life and no points (`prg007.asm:2135` — the 1-up's own `ScoreAdd` entry is `$00`), so
  crediting points alone silently swallows the reward a long chain exists to earn. `SuperLeaf` had the
  same latent bug and was switched over at the same time.
- **The score comes off `GameEngine`, not off a player.** A kill need not involve one: a kicked shell goes
  on scoring after the player has walked away, and has no player in hand to ask. There is a single shared
  `PlayerData`, which is also the instance the HUD renders from — so nothing has to notify the HUD. Its
  `PlayerData.State` snapshot already diffs score and lives every frame.

### `Kill_Tally` is per **jump**, not per level — and it is the player's, not the shell's

The ROM's own symbol table says it: "a counter that increases with each successful hit of an object
**without touching the ground**" (`smb3.asm:1782`). Both ground-contact sites zero it in the same breath
as `Player_InAir` and `Player_YVel` (`prg008.asm:4648-4651` and `:5937-5940`), and that triple is
`PlayerRuntimeState.stop()` plus the `position.setDY(0)` beside its single call site in
`StaticEnvironmentCollisionGrid` — so the reset lives in `stop()`.

Consequence, and the thing to actually remember: **two Goombas stomped on one bounce pay 100 then 200;
land between them and the second pays 100 again.** It is not a running total.

This is the counterpart of the per-shell `Objects_KillTally` that SMB3-10 gave a rolling shell. Both now
exist and are deliberately separate: one chain per player ended by landing, one chain per shell ended by
the shell dying. They share only `Reward.forKillTally(int)`, the ladder (whose 800 → **1000** step is why
it is a transcribed table and not `100 × 2^n`).

### Stomps advance the chain; a tail strike does not

| Event | Reads | Advances | dasm |
|---|---|---|---|
| Stomp an ordinary enemy | yes | yes | `LDA Kill_Tally / INC Kill_Tally` @ `PRG000_D2E4` |
| Stomp a resting shell | yes | yes | same pair, `prg000.asm:3927-3928` |
| Tail strike / bouncer | yes | **no** | `Enemy_Kill`, `prg000.asm:6018-6019` — no `INC` |
| Rolling shell's victims | — | the *shell's* own tally | `Objects_KillTally` |
| Wall crush, carry contact | flat 100 | no | `A = 0` into `Score_Get100PlusPts` |

Hence the two helpers on `LevelScenePlayerRuntimeStateAware`: `nextKillChainReward()` for a stomp and
`killChainRewardWithoutAdvancing()` for a tail strike. Whack three Goombas with the tail in one jump and
all three pay the same.

Both caps are the ROM's, and both are already in `Reward`: `Kill_Tally` is held at `8`
(`prg007.asm:2155-2161`) and the resulting score index is clamped at `$0D`, the 1-up
(`prg007.asm:2174-2178`). `nextKillTally()` saturates for the same reason rather than letting an `int`
climb.

Two ROM resets have **no counterpart**, both for want of the feature: entering water, and flying on a
P-Wing — "P-Wing does not net you 1-ups" (`prg007.asm:1540` and `:1550`).

### A note on which enemies pay at all

A kicked or carried shell's victim awards nothing: the ROM pays the **striker's** slot, so the shell banks
it and the Goomba dies for free. And a tail strike on a Koopa Troopa pays nothing either — `OA3_DIESHELLED`
diverts it past the scoring branch at `prg000.asm:6010-6019`, which is the same attribute that makes a
troopa shell rather than die. Neither is an omission.
