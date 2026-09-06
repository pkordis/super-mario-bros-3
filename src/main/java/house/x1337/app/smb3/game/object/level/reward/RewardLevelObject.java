package house.x1337.app.smb3.game.object.level.reward;

import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.util.GameRenderer;

public interface RewardLevelObject extends ActiveLevelObject, GameRenderer {
    Score getRewardScore();
    boolean isCollected();
    boolean isExpired();

    default void motionUpdate() {
        // No motion by default
    }

    default boolean isCollectable() {
        return true;
    }

    /**
     * Whether this reward is removed the instant it is collected — the same tick
     * its score caption spawns — instead of lingering one frame to share a
     * rendered frame with that caption. Both the mushroom and the leaf vanish on
     * contact, before the suit-change freeze begins the next tick, so they
     * override this to {@code true}; the default keeps the shared frame.
     */
    default boolean detachesOnCollect() {
        return false;
    }
}
