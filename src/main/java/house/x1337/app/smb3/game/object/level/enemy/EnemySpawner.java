package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.EnemyAnimator;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.enemy.Enemy;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyService;
import house.x1337.app.smb3.util.CastCapable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.stream.Stream;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.TileType.Category.ENEMY;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class EnemySpawner implements GameEngineAware, CastCapable {
    private final Animator.Registry animatorRegistry = getBean(Animator.Registry.class);
    private final EnemyService enemyService = getBean(EnemyService.class);
    private final GameEngine gameEngine;

    public void spawn() {
        resetEnemies();

        final LevelScene.LevelSceneLayer layer = gameEngine.getLevelScene().getNonPlayableCharactersLayer();
        if (layer == null || layer.getTiles() == null) {
            return;
        }

        final Tile[][] tiles = layer.getTiles();
        for (int row = 0; row < tiles.length; row++) {
            for (int column = 0; column < tiles[row].length; column++) {
                spawnAt(tiles[row][column], row, column);
            }
        }
    }

    /**
     * Spawns the enemy an author placed on this cell, if any. A level places an enemy by painting its
     * {@code ENEMY_PART} tiles, anchored by the enemy's <em>rendering-starter</em> tile
     * ({@code EnemyStamper}); this is the runtime half of that placement, so it keys on the same anchor.
     *
     * <p>The anchor tile identifies the {@link Enemy} through
     * {@link EnemyService#findByRenderingStarterTileId(int)}, and the record's {@code EnemyType} names
     * the runtime {@link LevelObjectType} to instantiate ({@code EnemyType.GOOMBA} →
     * {@code LevelObjectTypeMultiTiled.GOOMBA} → {@code Goomba}). The instance is built exactly as
     * {@code LevelObjectRecordCapabilities#toLevelObject} builds a multi-tiled object — {@code gameEngine}
     * plus its placement {@link Offset} — and handed to the manager that drives its type.
     *
     * <p>A non-anchor part of a multi-tile enemy resolves to no record here and spawns nothing: it is
     * drawn by the layer but the whole actor is spawned once, from its anchor.
     */
    private void spawnAt(
        final Tile tile,
        final int row,
        final int column
    ) {
        if (!tile.isRenderable()) {
            return;
        }
        final Optional<Enemy> enemy = enemyService.findByRenderingStarterTileId(tile.getId());
        if (enemy.isEmpty()) {
            warnAboutUnclassifiedEnemyTile(enemyService, tile, row, column);
            return;
        }

        final LevelObjectType type = enemy.orElseThrow().getEnemyType().getLevelObjectType();
        final LevelObject levelObject = getBean(type.getInstanceType(), gameEngine, Offset.of(column, row));
        if (levelObject instanceof final EnemyLevelObject enemyLevelObject) {
            spawnEnemy(enemyLevelObject);
        }
    }

    /**
     * A tile the editor classified as an enemy tile, but which anchors no enemy, silently would not
     * spawn. Say so instead — unless it is a non-anchor part of a known enemy, which is drawn by the
     * layer and spawned from its anchor, so it is expected to resolve to nothing here.
     */
    private void warnAboutUnclassifiedEnemyTile(
        final EnemyService enemyService,
        final Tile tile,
        final int row,
        final int column
    ) {
        if (tile.getType() == null || tile.getType().getCategory() != ENEMY) {
            return;
        }
        if (enemyService.isEnemyPartTile(tile.getId())) {
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
     * {@code Animator.Registry#findSuitableAnimator}. Called once per placed enemy while
     * the level is being set up, so a linear scan over the handful of managers is ample.
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
