package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

public interface RewardDispensingLevelObject extends LevelObject {
    ItemType getReward();

    void onCoinDispensed(LevelScenePlayer levelScenePlayer);

    default void dispenseReward(final LevelScenePlayer levelScenePlayer) {
        switch (getReward()) {
            case COIN_SINGLE:
                onCoinDispensed(levelScenePlayer);
                break;
            default:
                break;
        }
    }
}
