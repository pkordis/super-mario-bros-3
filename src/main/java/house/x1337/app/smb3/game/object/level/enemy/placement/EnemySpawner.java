package house.x1337.app.smb3.game.object.level.enemy.placement;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.EnemyAnimator;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyMotionManager;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.util.CastCapable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.TileType.Category.ENEMY;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;
import static java.util.Collections.unmodifiableMap;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class EnemySpawner implements GameEngineAware, CastCapable {
    private static final int LOOK_AHEAD_LEFT_PIXELS = 32;
    private static final int LOOK_AHEAD_RIGHT_PIXELS = 16;

    private final Animator.Registry animatorRegistry = getBean(Animator.Registry.class);
    private final EnemyStampService enemyStampService = getBean(EnemyStampService.class);
    private final List<EnemyPlacement> placements = new ArrayList<>();
    private final Map<EnemyLevelObject, EnemyPlacement> placementsByEnemy = new IdentityHashMap<>();
    private final GameEngine gameEngine;

    private boolean openingScreenSpawned;
    private boolean scrolledLeft;
    private int lastScreenLeft;

    /**
     * Reads the level's enemy placements, after dropping the previous level's enemies. Nothing is
     * instantiated here: the first {@link #spawnPlacementsComingIntoView()} spawns the opening screen's
     * enemies, and later ones spawn as the scroll reaches their column.
     */
    public void spawn() {
        resetEnemies();
        placements.clear();
        placementsByEnemy.clear();
        openingScreenSpawned = false;

        final LevelScene.LevelSceneLayer layer = gameEngine.getLevelScene().getNonPlayableCharactersLayer();
        if (layer == null || layer.getTiles() == null) {
            return;
        }

        final Tile[][] tiles = layer.getTiles();
        for (int row = 0; row < tiles.length; row++) {
            for (int column = 0; column < tiles[row].length; column++) {
                recordPlacementAt(tiles[row][column], row, column);
            }
        }
    }

    public void spawnPlacementsComingIntoView() {
        if (placements.isEmpty()) {
            return;
        }
        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
        final int screenLeft = (int) floor(screen.left());
        final int screenRight = (int) floor(screen.right());
        if (!openingScreenSpawned) {
            openingScreenSpawned = true;
            lastScreenLeft = screenLeft;
            scrolledLeft = true;
            final int firstColumn = floorDiv(screenLeft - LOOK_AHEAD_LEFT_PIXELS, TILE_SPRITE_SIZE);
            final int lastColumn = floorDiv(screenRight + LOOK_AHEAD_RIGHT_PIXELS, TILE_SPRITE_SIZE);
            for (int column = firstColumn; column <= lastColumn; column++) {
                spawnColumn(column);
            }
            return;
        }
        if (screenLeft != lastScreenLeft) {
            scrolledLeft = screenLeft < lastScreenLeft;
            lastScreenLeft = screenLeft;
        }
        final int lookAhead = scrolledLeft
            ? screenLeft - LOOK_AHEAD_LEFT_PIXELS
            : screenRight + LOOK_AHEAD_RIGHT_PIXELS;
        spawnColumn(floorDiv(lookAhead, TILE_SPRITE_SIZE));
    }

    /** Spawns every placement in the column that has no instance out in the level. */
    private void spawnColumn(final int column) {
        for (final EnemyPlacement placement : placements) {
            if (placement.column() == column && !placement.isSpawned()) {
                spawnFrom(placement);
            }
        }
    }

    /**
     * Gives the enemy's placement back, so it spawns again the next time the look-ahead reaches its
     * column. This is {@code Object_Delete}: a live enemy that left the screen or the level.
     *
     * @param enemy the instance being thrown away; one without a placement is ignored
     */
    public void releasePlacementOf(final EnemyLevelObject enemy) {
        final EnemyPlacement placement = placementsByEnemy.remove(enemy);
        if (placement != null) {
            placement.release();
        }
    }

    /**
     * Forgets the enemy but keeps its placement spent, so it never spawns again this level.
     *
     * @param enemy the instance being thrown away; one without a placement is ignored
     */
    public void retirePlacementOf(final EnemyLevelObject enemy) {
        placementsByEnemy.remove(enemy);
    }

    /**
     * Moves a placement from one instance to the instance taking over its slot.
     *
     * @param predecessor the instance giving up its slot
     * @param successor   the instance taking it over
     */
    public void handOverPlacement(
        final EnemyLevelObject predecessor,
        final EnemyLevelObject successor
    ) {
        final EnemyPlacement placement = placementsByEnemy.remove(predecessor);
        if (placement != null) {
            placementsByEnemy.put(successor, placement);
        }
    }

    /**
     * Hands a single enemy that came into being <em>during</em> play to the manager that owns its type.
     *
     * @param enemy the enemy to register
     * @throws IllegalStateException if no manager claims the enemy's type
     */
    public void spawnOne(final EnemyLevelObject enemy) {
        spawnEnemy(enemy);
    }

    /**
     * Records the enemy an author placed on this cell, if any. A level places an enemy by painting its
     * {@code ENEMY_PART} tiles, anchored by the enemy's <em>rendering-starter</em> tile
     * ({@code EnemyStamper}); this is the runtime half of that placement, so it keys on the same anchor.
     *
     * <p>The anchor tile identifies the {@link EnemyStamp} through
     * {@link EnemyStampService#findByRenderingStarterTileId(int)}, and the record's {@link EnemyType} names
     * both the runtime {@link LevelObjectType} to instantiate and the {@code EnemyMode} the author picked.
     *
     * <p>A non-anchor part of a multi-tile enemy resolves to no record here and places nothing: it is
     * drawn by the layer but the whole actor is spawned once, from its anchor.
     */
    private void recordPlacementAt(
        final Tile tile,
        final int row,
        final int column
    ) {
        if (!tile.isRenderable()) {
            return;
        }
        final Optional<EnemyStamp> enemy = enemyStampService.findByRenderingStarterTileId(tile.getId());
        if (enemy.isEmpty()) {
            warnAboutUnclassifiedEnemyTile(enemyStampService, tile, row, column);
            return;
        }
        final EnemyStamp stamp = enemy.orElseThrow();
        placements.add(new EnemyPlacement(stamp.getEnemyType(), Offset.of(column, row), dataOf(stamp)));
    }

    private static Map<String, Object> dataOf(final EnemyStamp stamp) {
        return stamp.getData() == null ? Map.of() : unmodifiableMap(new HashMap<>(stamp.getData()));
    }

    /**
     * Builds the placement's enemy and hands it to the manager that drives its type. The instance is
     * built exactly as {@code LevelObjectRecordCapabilities#toLevelObject} builds a multi-tiled object
     * ({@code gameEngine} plus its placement {@link Offset}, then its attributes):
     * {@code EnemyType.GOOMBA} → {@code LevelObjectTypeMultiTiled.GOOMBA} → {@code Goomba}.
     *
     * <p>The attributes are the enemy's stored custom data with the {@link EnemyType}'s mode written
     * <em>last</em> under {@code MODE_ATTRIBUTE}, so the type is the single source of truth for the mode:
     * a stored {@code mode} entry, which the editor refuses to save anyway, never overrides it.
     */
    private void spawnFrom(final EnemyPlacement placement) {
        final EnemyType enemyType = placement.getEnemyType();
        final LevelObjectType type = enemyType.getLevelObjectType();
        final LevelObject levelObject = getBean(type.getInstanceType(), gameEngine, placement.getAnchor());
        final Map<String, Object> data = new HashMap<>(placement.getData());
        data.put(MODE_ATTRIBUTE, enemyType.getMode().name());
        levelObject.configure(new LevelObjectData(data));
        if (levelObject instanceof final EnemyLevelObject enemyLevelObject) {
            spawnEnemy(enemyLevelObject);
            placement.markSpawned();
            placementsByEnemy.put(enemyLevelObject, placement);
        }
    }

    /**
     * A tile the editor classified as an enemy tile, but which anchors no enemy, silently would not
     * spawn. Say so instead — unless it is a non-anchor part of a known enemy, which is drawn by the
     * layer and spawned from its anchor, so it is expected to resolve to nothing here.
     */
    private void warnAboutUnclassifiedEnemyTile(
        final EnemyStampService enemyStampService,
        final Tile tile,
        final int row,
        final int column
    ) {
        if (tile.getType() == null || tile.getType().getCategory() != ENEMY) {
            return;
        }
        if (enemyStampService.isEnemyPartTile(tile.getId())) {
            return;
        }
        log.warn(
            "Enemy tile (id={}) at row={}, column={} anchors no enemy; nothing spawned.",
            tile.getId(),
            row,
            column
        );
    }

    /**
     * Drops every enemy manager's population, so no level's enemies survive into the next one. Scoped to
     * the enemy managers on purpose: the tile-bound {@code GameObjectAnimator}s are motion managers too,
     * and resetting those here would undo the registration the collision-grid build has just done.
     */
    private void resetEnemies() {
        enemyManagers().forEach(EnemyMotionManager::reset);
        animatorRegistry.resetAll(EnemyAnimator.class);
    }

    /**
     * Hands a freshly placed enemy to the manager that owns its type — the enemy equivalent of
     * {@code Animator.Registry#findSuitableAnimator}. Called each time a placement spawns or an enemy
     * comes into being mid-level, so a linear scan over the handful of managers is ample.
     *
     * @param enemy the enemy to hand over
     * @throws IllegalStateException if no manager claims the enemy's type
     */
    private void spawnEnemy(final EnemyLevelObject enemy) {
        enemyManagers()
            .filter(manager -> manager.getType().isInstance(enemy))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No suitable EnemyLevelObjectMotionManager found for " + enemy.getClass().getSimpleName()
            ))
            .spawn(enemy);
    }

    private Stream<EnemyMotionManager<EnemyLevelObject>> enemyManagers() {
        return checkedCast(getMotionManagers(EnemyMotionManager.class).stream());
    }
}
