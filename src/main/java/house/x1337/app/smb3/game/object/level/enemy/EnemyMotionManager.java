package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public interface EnemyMotionManager<E extends EnemyLevelObject> extends MotionManager<E> {
    List<E> getActiveInstances();
    Class<E> getType();

    @Override
    default void update() {
        final List<E> activeInstances = getActiveInstances();
        if (activeInstances.isEmpty()) {
            return;
        }

        // Every instance belongs to the running scene, so any of them yields its GameEngine.
        final GameEngine gameEngine = activeInstances.getFirst().getGameEngine();
        final AxisAlignedBoundingBox activeRegion = gameEngine
            .getCameraState()
            .getActiveObjectRegion(getActivationMarginPixels());
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = gameEngine.getActiveObjectGrid();

        final Iterator<E> iterator = activeInstances.iterator();
        while (iterator.hasNext()) {
            final E instance = iterator.next();
            if (!activeRegion.intersects(instance.getBounds())) {
                continue;
            }
            if (!instance.isSpawnedIntoScene()) {
                // First time the camera has reached it: the ROM's Level_LoadObjects + ObjInit_* moment.
                instance.spawnIntoScene();
            }
            instance.motionUpdate();
            if (instance.isExpired()) {
                instance.detach();
                iterator.remove();
                continue;
            }
            // Feed the shared broad-phase so the engine's collision pass can test this enemy against the
            // players after every manager has inserted (the ROM's object loop reaching Player_HitEnemy).
            // Only live, on-screen enemies go in — an off-screen one cannot hit an on-screen player.
            broadPhase.insert(instance);
        }
    }

    @Override
    default void reset() {
        getActiveInstances().clear();
    }

    default int getActivationMarginPixels() {
        return TILE_SPRITE_SIZE * 2;
    }

    default void spawn(final E enemy) {
        getActiveInstances().add(enemy);
    }
}
