package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.EnemyAnimator;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.util.CastCapable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.TileType.Category.ENEMY;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class EnemySpawner implements GameEngineAware, CastCapable {
    private final Animator.Registry animatorRegistry = getBean(Animator.Registry.class);
    private final EnemyStampService enemyStampService = getBean(EnemyStampService.class);
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
     * Hands a single enemy that came into being <em>during</em> play to the manager that owns its type.
     *
     * <p>Deliberately separate from {@link #spawn()}: that is the level-setup pass and opens by
     * resetting every enemy manager and animator, which is exactly wrong mid-level - it would wipe the
     * population the newcomer is joining. Nothing else is needed to bring it to life, because the
     * manager's per-tick pass builds its sprite the first time the camera reaches it and inserts it into
     * the broadphase from then on.
     *
     * <p>The one caller today is the Para-Goomba's de-winging stomp, which is the ROM rewriting
     * {@code Level_ObjectID,X} in place rather than spawning anything - so the replacement must slot
     * into the live population without disturbing it.
     *
     * @param enemy the enemy to register
     * @throws IllegalStateException if no manager claims the enemy's type
     */
    public void spawnOne(final EnemyLevelObject enemy) {
        spawnEnemy(enemy);
    }

    /**
     * Spawns the enemy an author placed on this cell, if any. A level places an enemy by painting its
     * {@code ENEMY_PART} tiles, anchored by the enemy's <em>rendering-starter</em> tile
     * ({@code EnemyStamper}); this is the runtime half of that placement, so it keys on the same anchor.
     *
     * <p>The anchor tile identifies the {@link EnemyStamp} through
     * {@link EnemyStampService#findByRenderingStarterTileId(int)}, and the record's {@link EnemyType} names
     * both the runtime {@link LevelObjectType} to instantiate ({@code EnemyType.GOOMBA} →
     * {@code LevelObjectTypeMultiTiled.GOOMBA} → {@code Goomba}) and the {@code EnemyMode} the author
     * picked, which is handed to the instance as its one placement attribute. The instance itself is
     * built exactly as {@code LevelObjectRecordCapabilities#toLevelObject} builds a multi-tiled object —
     * {@code gameEngine} plus its placement {@link Offset} — and handed to the manager that drives its
     * type.
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
        final Optional<EnemyStamp> enemy = enemyStampService.findByRenderingStarterTileId(tile.getId());
        if (enemy.isEmpty()) {
            warnAboutUnclassifiedEnemyTile(enemyStampService, tile, row, column);
            return;
        }

        final EnemyType enemyType = enemy.orElseThrow().getEnemyType();
        final LevelObjectType type = enemyType.getLevelObjectType();
        final LevelObject levelObject = getBean(type.getInstanceType(), gameEngine, Offset.of(column, row));
        levelObject.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, enemyType.getMode().name())));
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
