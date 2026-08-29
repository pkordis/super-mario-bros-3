package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperLeafMotionManager;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface RewardDispensingLevelObject extends LevelObject {
    ItemType getReward();

    void onCoinDispensed(LevelScenePlayer levelScenePlayer);

    default void dispenseReward(final LevelScenePlayer levelScenePlayer) {
        switch (getReward()) {
            case COIN_SINGLE:
                onCoinDispensed(levelScenePlayer);
                break;
            case SUPER_LEAF:
                onSuperLeafDispensed(levelScenePlayer);
                break;
            default:
                break;
        }
    }

    default void onSuperLeafDispensed(final LevelScenePlayer levelScenePlayer) {
        getBean(SuperLeafMotionManager.class).spawnLeaf(levelScenePlayer.getGameEngine(), getOffset());
    }
}
