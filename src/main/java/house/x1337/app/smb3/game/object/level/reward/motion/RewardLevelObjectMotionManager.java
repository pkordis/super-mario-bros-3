package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface RewardLevelObjectMotionManager<C extends RewardLevelObject> extends ActiveLevelObjectMotionManager<C> {
    @Override
    default void postCollision() {
        // The engine's collision pass has just run. Spawn a caption for every reward collected this
        // tick, then remove any that vanish on contact (detachesOnCollect). The mushroom and the
        // leaf both do, so they are gone this tick, before the growth freeze a small player triggers
        // begins next tick. Any reward that keeps the default lingers one more rendered frame beside
        // its caption and is removed by update() next tick.
        final Iterator<C> iterator = getActiveInstances().iterator();
        while (iterator.hasNext()) {
            final C instance = iterator.next();
            if (!instance.isCollected()) {
                continue;
            }
            spawnScorePopupFor(instance);
            if (instance.detachesOnCollect()) {
                instance.detach();
                iterator.remove();
            }
        }
    }

    @Override
    default void updateWhileHalted() {
        tickScorePopups();
    }

    private void spawnScorePopupFor(final C instance) {
        final ScorePopupAnimation animation = getBean(
            ScorePopupAnimation.class,
            instance.getGameEngine(),
            instance.getRewardType().getData(),
            instance.getOffset()
        );
        animation.placeOver(instance);
        animation.start();
        getActiveScorePopups().add(animation);
    }

    @Override
    default void update() {
        final List<C> activeInstances = getActiveInstances();
        // Advance existing score popups first: one spawned later this tick (in postCollision) gets its first
        // update as it starts, so it is not advanced twice.
        tickScorePopups();

        if (activeInstances.isEmpty()) {
            return;
        }

        // Every active reward belongs to the running scene, so any of them yields its GameEngine.
        final GameEngine gameEngine = activeInstances.getFirst().getGameEngine();
        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = gameEngine.getActiveObjectGrid();
        final Iterator<C> iterator = activeInstances.iterator();
        while (iterator.hasNext()) {
            final C instance = iterator.next();

            // Fallback removal for a collected reward that did not vanish on contact
            // (detachesOnCollect == false): drop it a tick later; its caption keeps rising.
            if (instance.isCollected()) {
                instance.detach();
                iterator.remove();
                continue;
            }

            // Like the power-ups, a reward runs wherever it is: there is no activation window.
            instance.motionUpdate();

            if (instance.isExpired() || isDeletedOffScreen(instance, screen)) {
                instance.expire();
                instance.detach();
                iterator.remove();
                continue;
            }

            if (instance.isCollectable()) {
                // Feed the shared broad-phase; the engine's collision pass queries it after every
                // manager has inserted.
                broadPhase.insert(instance);
            }
        }
    }

    private boolean isDeletedOffScreen(
        final C instance,
        final AxisAlignedBoundingBox screen
    ) {
        return !instance.isExemptFromDeletionOffScreen()
            && (instance.hasFallenBelowTheLevel() || isPastTheDeletionEdge(instance, screen));
    }

    default void spawn(final C newLevelObject) {
        getActiveInstances().add(newLevelObject);
    }

    /**
     * Drops this manager's live population when the level restarts (a death re-enters the level
     * fresh). Reward instances and their rising score captions are spawned during play and normally
     * clear themselves as they are collected or expire, but a restart can fire mid-flight — a reward
     * still rolling, a caption still rising — so their nodes must be detached here or they would linger
     * in the rebuilt scene.
     */
    @Override
    default void reset() {
        getActiveInstances().forEach(ActiveLevelObject::detach);
        getActiveInstances().clear();
        getActiveScorePopups().forEach(ScorePopupAnimation::detach);
        getActiveScorePopups().clear();
    }
}
