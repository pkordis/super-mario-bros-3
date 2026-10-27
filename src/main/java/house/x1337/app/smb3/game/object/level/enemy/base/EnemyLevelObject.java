package house.x1337.app.smb3.game.object.level.enemy.base;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.BakedLayerPainter;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;

public interface EnemyLevelObject extends ActiveLevelObject, BakedLayerPainter {
    EnemyMode getMode();
    void motionUpdate();
    boolean isExpired();
    void setExpired(boolean expired);
    void spawnIntoScene();
    boolean isSpawnedIntoScene();
    DimensionsPixels getBoundsPixels();
    void hurt(LevelScenePlayer levelScenePlayer);

    default boolean isHittable() {
        return true;
    }

    default boolean canHurtPlayer() {
        return isHittable();
    }

    default Reward getPendingScoreReward() {
        return null;
    }

    default void setPendingScoreReward(final Reward reward) {
    }

    @Override
    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(getPosition(), getBoundsPixels());
    }

    /**
     * Takes the editor placement this enemy was spawned from out of the baked NPC layer, so the terrain
     * stops drawing an enemy that is now a live sprite.
     *
     * <p>Erasing {@link #getOffset()} alone is not enough. An author places an enemy by stamping a whole
     * {@link EnemyStamp} — a grid of {@code ENEMY_PART} cells anchored by its rendering-starter tile —
     * and only that anchor cell becomes {@link #getOffset()}. Every other cell of the stamp stays baked
     * where it was painted, which is invisible for an enemy drawn inside one tile but not for anything
     * larger: a Para-Goomba's wings sit a tile above its body and spill into the column either side, so
     * they would stay pinned to the terrain while the Para-Goomba flies away from them.
     *
     * <p>Call from {@code spawnIntoScene}, once the live sprite exists to take the placement's place.
     */
    default void erasePlacementFromBakedTexture() {
        final GameEngine gameEngine = getGameEngine();
        final LevelScene levelScene = gameEngine.getLevelScene();
        final Geometry bakedLayerGeometry = gameEngine.getLayerGeometry(NON_PLAYABLE_CHARACTERS);
        final LevelSceneDimensions dimensions = levelScene.getDimensions();
        placementCells(levelScene)
            .forEach(cell -> eraseFromBakedTexture(bakedLayerGeometry, dimensions, cell));
    }

    /**
     * The NPC-layer cells this enemy's placement occupies. Resolved from the layer rather than from the
     * enemy's sprite size, because the footprint is whatever an author stamped — and falls back to the
     * anchor cell alone when the layer no longer matches any stamp. This keeps an enemy spawned by hand
     * (a test scene, or a Goomba adopting a Para-Goomba's slot) working.
     */
    private List<Offset> placementCells(final LevelScene levelScene) {
        final Offset anchor = getOffset();
        final LevelScene.LevelSceneLayer layer = levelScene.getNonPlayableCharactersLayer();
        final Tile[][] tiles = layer == null ? null : layer.getTiles();
        if (tiles == null) {
            return List.of(anchor);
        }
        if (anchor.y() < 0 || anchor.y() >= tiles.length
            || anchor.x() < 0 || anchor.x() >= tiles[anchor.y()].length) {
            return List.of();
        }
        return getBean(EnemyStampService.class)
            .findByRenderingStarterTileId(tiles[anchor.y()][anchor.x()].getId())
            .map(stamp -> stampCells(stamp, anchor, tiles))
            .orElseGet(() -> List.of(anchor));
    }

    /**
     * Maps a stamp's own grid onto the level, around the cell its anchor was painted in. A cell is
     * included only when the layer still holds that exact part, so a stamp that was painted over, or
     * that reaches off the edge of the level, erases nothing it does not own.
     */
    private static List<Offset> stampCells(
        final EnemyStamp stamp,
        final Offset anchor,
        final Tile[][] tiles
    ) {
        final List<Offset> cells = new ArrayList<>();
        for (int row = 0; row < stamp.getRows(); row++) {
            for (int column = 0; column < stamp.getColumns(); column++) {
                final Tile part = stamp.tileAt(row, column);
                if (part == null || !part.isRenderable()) {
                    continue;
                }
                final int levelRow = anchor.y() + row - stamp.getRenderingStarterRow();
                final int levelColumn = anchor.x() + column - stamp.getRenderingStarterColumn();
                if (levelRow < 0 || levelRow >= tiles.length
                    || levelColumn < 0 || levelColumn >= tiles[levelRow].length) {
                    continue;
                }
                if (tiles[levelRow][levelColumn].getId() != part.getId()) {
                    continue;
                }
                cells.add(Offset.of(levelColumn, levelRow));
            }
        }
        return cells;
    }

    /**
     * An enemy's stomp-depth reference is the Y it tracks ({@code Objects_Y}), not its collision box top
     * and not its sprite top - the ROM measures the stomp from {@code Objects_Y} regardless of how
     * either is offset from it (dasm prg000 {@code Player_HitEnemy} @ PRG000_D218). An enemy that insets
     * its box (a Goomba's box top sits {@code +2} below) must keep this at the tracked Y or it would
     * silently stomp from the wrong depth.
     *
     * <p>Do not read "tracked Y" as "sprite top". For a Goomba the two coincide; for a <b>Koopa
     * Troopa</b> they do not - {@code Objects_Y} is the top of its shell and its head is drawn above
     * that at negative offsets, which prg000.asm:3782 calls out by name. The return value below is still
     * right for it, because the ROM's reference is {@code Objects_Y} either way.
     *
     * @return the enemy's tracked Y
     */
    @Override
    default double getStompReferenceTop() {
        return getPosition().y();
    }

    @Override
    default void expire() {
        setExpired(true);
    }

    @Override
    default boolean isCollidable() {
        return false;
    }

    @Override
    default boolean resolvesDirectionalPlayerCollision() {
        return true;
    }

    @Override
    default void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        // Deliberately empty - directional response arrives via onCollisionFrom* (see interface Javadoc).
    }
}
