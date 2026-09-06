package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.game.object.level.reward.RewardLevelObject;

public interface RewardConsumingPlayer {
    void onRewardConsumption(final RewardLevelObject reward);
}
