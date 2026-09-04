package house.x1337.app.smb3.game.object.level.reward;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface RewardLevelObjectMotionManager<C extends RewardLevelObject> extends MotionManager<C> {
    float DEFAULT_SCORE_CAPTION_HEIGHT = TILE_SIZE_GAME_UNITS / 2f;
    float DEFAULT_SCORE_CAPTION_INITIAL_DIP = 4f / TILE_SPRITE_SIZE;
    float DEFAULT_SCORE_CAPTION_ABOVE_INSTANCE_LIFT = DEFAULT_SCORE_CAPTION_HEIGHT + DEFAULT_SCORE_CAPTION_INITIAL_DIP;

    List<C> getActiveInstances();
    List<ScorePopupAnimation> getActiveScorePopups();
    Class<C> getType();

    @Override
    default void postCollision() {
        // The engine's collision pass has just run; a leaf collected this tick is flagged but not
        // yet removed (update() removes it next tick). Spawn its caption now so both render together
        // for one frame. Each collected leaf is seen exactly once here — it is gone by the next tick.
        for (final C instance : getActiveInstances()) {
            if (instance.isCollected()) {
                spawnScorePopupFor(instance);
            }
        }
    }

    private void spawnScorePopupFor(final C instance) {
        getActiveScorePopups().add(getBean(
            ScorePopupAnimation.class,
            instance.getGameEngine(),
            instance.getRewardScore().getData(),
            instance.getOffset(),
            instance.getCurrentWorldOffset().plus(0f, getScoreCaptionYOffsetAboveInstance(), 0f)
        ));
    }

    default float getScoreCaptionYOffsetAboveInstance() {
        return DEFAULT_SCORE_CAPTION_ABOVE_INSTANCE_LIFT;
    }

    @Override
    default void update() {
        final List<C> activeInstances = getActiveInstances();
        // Advance existing score popups first. A caption spawned later this tick (in postCollision)
        // is therefore left un-ticked until the next tick, so it renders once at its spawn position
        // — the single frame where the leaf and its "1000" caption are both visible.
        tickScorePopups();

        if (activeInstances.isEmpty()) {
            return;
        }

        // Every active leaf belongs to the running scene, so any of them yields its GameEngine.
        final GameEngine gameEngine = activeInstances.getFirst().getGameEngine();
        final AxisAlignedBoundingBox activeRegion = gameEngine
            .getCameraState()
            .getActiveObjectRegion(getActivationMarginPixels());
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = gameEngine.getActiveObjectGrid();

        final Iterator<C> iterator = activeInstances.iterator();
        while (iterator.hasNext()) {
            final C instance = iterator.next();

            // Collected last tick: the leaf has now shared its one rendered frame with the caption.
            // Remove it; the caption carries on rising by itself.
            if (instance.isCollected()) {
                instance.detach();
                iterator.remove();
                continue;
            }

            final boolean active = activeRegion.intersects(instance.getBounds());
            if (active) {
                instance.tick();
            }

            if (instance.isExpired()) {
                // Fell off the level — no reward, no caption.
                instance.detach();
                iterator.remove();
                continue;
            }

            if (active && instance.isCollectable()) {
                // Feed the shared broadphase; the engine's collision pass queries it after every
                // manager has inserted. Off-screen leaves stay out — they cannot hit an on-screen
                // player.
                broadPhase.insert(instance);
            }
        }
    }

    default int getActivationMarginPixels() {
        return TILE_SPRITE_SIZE * 2;
    }

    private void tickScorePopups() {
        final Iterator<ScorePopupAnimation> scorePopupIterator = getActiveScorePopups().iterator();
        while (scorePopupIterator.hasNext()) {
            final ScorePopupAnimation scorePopup = scorePopupIterator.next();
            scorePopup.tick();
            if (scorePopup.isExpired()) {
                scorePopup.detach();
                scorePopupIterator.remove();
            }
        }
    }

    default void spawn(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        getActiveInstances().add(getBean(getType(), gameEngine, offset));
    }
}
