package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperLeafMotionManager;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;

public interface RewardDispensingLevelObject extends LevelObject {
    ItemType getReward();

    void onCoinDispensed(LevelScenePlayer levelScenePlayer);

    default void dispenseReward(final LevelScenePlayer levelScenePlayer) {
        // dasm GBCtl_LeafBlock (prg002.asm @ PRG002_A39C): a leaf block dispenses the Super Leaf
        // ($1E) only when the player is NOT small; a small (SHRUNK) player is upgraded to a Super
        // Mushroom ($0D) instead. Check this first and override the configured reward.
        if (levelScenePlayer.isSmall() && getReward() != COIN_SINGLE) {
            onSuperMushroomDispensed(levelScenePlayer);
            return;
        }

        switch (getReward()) {
            case COIN_SINGLE:
                onCoinDispensed(levelScenePlayer);
                break;
            case SUPER_LEAF:
                onSuperLeafDispensed(levelScenePlayer);
                break;
            case SUPER_MUSHROOM:
                onSuperMushroomDispensed(levelScenePlayer);
                break;
            default:
                break;
        }
    }

    default void onSuperLeafDispensed(final LevelScenePlayer levelScenePlayer) {
        getBean(SuperLeafMotionManager.class).spawn(levelScenePlayer.getGameEngine(), getOffset());
    }

    default void onSuperMushroomDispensed(final LevelScenePlayer levelScenePlayer) {
        getBean(SuperMushroomMotionManager.class).spawn(levelScenePlayer.getGameEngine(), getOffset());
    }
}
