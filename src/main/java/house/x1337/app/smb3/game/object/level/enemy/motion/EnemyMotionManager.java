package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface EnemyMotionManager<E extends EnemyLevelObject> extends ActiveLevelObjectMotionManager<E> {
    float SCORE_CAPTION_HEIGHT = TILE_SIZE_GAME_UNITS / 2f;
    float SCORE_CAPTION_INITIAL_DIP = 4f / TILE_SPRITE_SIZE;
    float SCORE_CAPTION_ABOVE_INSTANCE_LIFT = SCORE_CAPTION_HEIGHT + SCORE_CAPTION_INITIAL_DIP;

    @Override
    default void update() {
        final List<E> activeInstances = getActiveInstances();
        // Advance captions already in flight first, so one spawned later this tick (in postCollision)
        // renders once at its spawn position before it starts rising — the same ordering the reward
        // managers rely on.
        tickScorePopups();

        if (activeInstances.isEmpty()) {
            return;
        }

        // Every instance belongs to the running scene, so any of them yields its GameEngine.
        final GameEngine gameEngine = activeInstances.getFirst().getGameEngine();
        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = gameEngine.getActiveObjectGrid();

        final Iterator<E> iterator = activeInstances.iterator();
        while (iterator.hasNext()) {
            final E instance = iterator.next();
            if (!instance.isSpawnedIntoScene()) {
                instance.spawnIntoScene();
                if (!instance.hasAdoptedASlot()) {
                    continue;
                }
            }
            if (instance.hasFallenBelowTheLevel()) {
                remove(instance, iterator);
                if (instance.isDefeated()) {
                    broadPhase.retirePlacementOf(instance);
                } else {
                    broadPhase.releasePlacementOf(instance);
                }
                continue;
            }
            if (!isExemptFromDeletionOffScreen(instance) && isPastTheDeletionEdge(instance, screen)) {
                remove(instance, iterator);
                broadPhase.releasePlacementOf(instance);
                continue;
            }
            instance.motionUpdate();
            if (instance.isExpired()) {
                remove(instance, iterator);
                broadPhase.retirePlacementOf(instance);
                continue;
            }
            // Feed the shared broad-phase so the engine's collision pass can test this enemy against the
            // players after every manager has inserted. A defeated one is left out: it is intangible for
            // the rest of its death animation.
            if (instance.isHittable()) {
                broadPhase.insert(instance);
            }
        }
    }

    @Override
    default void postCollision() {
        for (final E instance : getActiveInstances()) {
            final Reward reward = instance.getPendingScoreReward();
            if (reward == null) {
                continue;
            }
            // Taken exactly once: the enemy stays in the list for the rest of its death animation.
            instance.setPendingScoreReward(null);
            instance.getGameEngine().getPlayerData().add(reward.getData());
            spawnScorePopupFor(instance, reward);
        }
    }

    /**
     * While gameplay is halted (dasm {@code Player_HaltGame}) the enemies and the broadphase stand still,
     * but a caption already in flight keeps rising — the one object animation the ROM advances through
     * the freeze.
     */
    @Override
    default void updateWhileHalted() {
        tickScorePopups();
    }

    @Override
    default void reset() {
        // Detach each enemy's sprite geometry before dropping it — not just clear the list. An enemy
        // alive at reset time (the one that just killed the player is always alive) still has its sprite
        // attached to the root node; clearing the list alone would orphan that geometry, leaving the
        // enemy visibly frozen in the scene after the restart. detach() removes it, exactly as the
        // per-tick update does when an enemy expires.
        getActiveInstances().forEach(ActiveLevelObject::detach);
        getActiveInstances().clear();
        getActiveScorePopups().forEach(ScorePopupAnimation::detach);
        getActiveScorePopups().clear();
    }

    default boolean isExemptFromDeletionOffScreen(final E instance) {
        return instance.isDefeated()
            || (instance instanceof final CarriableLevelObject carriable && carriable.isCarried());
    }

    default void spawn(final E enemy) {
        getActiveInstances().add(enemy);
    }

    private void remove(
        final E instance,
        final Iterator<E> iterator
    ) {
        instance.expire();
        instance.detach();
        iterator.remove();
    }

    /** Spawns the caption above the enemy that earned it. */
    private void spawnScorePopupFor(final E instance, final Reward reward) {
        final ScorePopupAnimation animation = getBean(
            ScorePopupAnimation.class,
            instance.getGameEngine(),
            reward.getData(),
            instance.getOffset()
        );
        animation.setWorldOffset(instance.getCurrentWorldOffset().plus(0f, SCORE_CAPTION_ABOVE_INSTANCE_LIFT, 0f));
        animation.start();
        getActiveScorePopups().add(animation);
    }
}
