package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface EnemyMotionManager<E extends EnemyLevelObject> extends MotionManager {
    float SCORE_CAPTION_HEIGHT = TILE_SIZE_GAME_UNITS / 2f;
    float SCORE_CAPTION_INITIAL_DIP = 4f / TILE_SPRITE_SIZE;
    float SCORE_CAPTION_ABOVE_INSTANCE_LIFT = SCORE_CAPTION_HEIGHT + SCORE_CAPTION_INITIAL_DIP;

    List<E> getActiveInstances();
    List<ScorePopupAnimation> getActiveScorePopups();
    Class<E> getType();

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
            // Only live, on-screen enemies go in — an off-screen one cannot hit an on-screen player, and
            // a defeated one is intangible for the rest of its death animation.
            if (instance.isHittable()) {
                broadPhase.insert(instance);
            }
        }
    }

    /**
     * Awards the kill captions for this tick's collisions, now that the engine's collision pass has run.
     *
     * <p>The ROM pops the score from inside the stomp response itself ({@code Score_Get100PlusPts}, dasm
     * prg000 @ PRG000_D2E4); this is the corresponding moment. Driving it from here rather than from the
     * enemy's own collision handler keeps the caption's lifecycle with the manager that owns every other
     * per-tick list, exactly as the reward managers do — and the value stays with the enemy, since the
     * ROM's award varies by object and kill tally.
     */
    @Override
    default void postCollision() {
        for (final E instance : getActiveInstances()) {
            final Reward reward = instance.getPendingScoreReward();
            if (reward == null) {
                continue;
            }
            // Taken exactly once: the enemy stays in the list for the rest of its death animation.
            instance.setPendingScoreReward(null);
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
        getActiveInstances().clear();
        getActiveScorePopups().forEach(ScorePopupAnimation::detach);
        getActiveScorePopups().clear();
    }

    default int getActivationMarginPixels() {
        return TILE_SPRITE_SIZE * 2;
    }

    default void spawn(final E enemy) {
        getActiveInstances().add(enemy);
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

    private void tickScorePopups() {
        final Iterator<ScorePopupAnimation> iterator = getActiveScorePopups().iterator();
        while (iterator.hasNext()) {
            final ScorePopupAnimation scorePopup = iterator.next();
            scorePopup.tick();
            if (scorePopup.isExpired()) {
                scorePopup.detach();
                iterator.remove();
            }
        }
    }
}
