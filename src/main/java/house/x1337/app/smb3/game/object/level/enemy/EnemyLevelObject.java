package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.BakedLayerPainter;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

public interface EnemyLevelObject extends ActiveLevelObject, BakedLayerPainter {
    EnemyMode getMode();
    void motionUpdate();
    boolean isExpired();
    void setExpired(boolean expired);
    void spawnIntoScene();
    boolean isSpawnedIntoScene();
    DimensionsPixels getBoundsPixels();

    @Override
    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(getPixelX(), getPixelY(), getBoundsPixels());
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
