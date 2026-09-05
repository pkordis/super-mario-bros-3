package house.x1337.app.smb3.game.object.level.reward;

import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.util.GameRenderer;

public interface RewardLevelObject extends ActiveLevelObject, GameRenderer {
    Score getRewardScore();
    boolean isCollected();
    boolean isExpired();
    void tick();
    default boolean isCollectable() {
        return true;
    }

    /**
     * Whether this reward is removed the instant it is collected — the same tick
     * its score caption spawns — instead of lingering one frame to share a
     * rendered frame with that caption. The mushroom vanishes on contact, before
     * the small→Super grow freeze begins the next tick, so it overrides this to
     * {@code true}; the leaf keeps the default shared frame.
     */
    default boolean detachesOnCollect() {
        return false;
    }
}
