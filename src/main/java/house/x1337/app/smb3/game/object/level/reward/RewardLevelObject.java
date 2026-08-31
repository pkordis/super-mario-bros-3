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
}
