package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.enumeration.RewardDispenseSide;
import house.x1337.app.smb3.enumeration.SuperMushroomType;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperLeafMotionManager;
import house.x1337.app.smb3.game.object.level.reward.motion.SuperMushroomMotionManager;
import house.x1337.app.smb3.game.object.level.variant.SuperMushroomVariantData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;
import static house.x1337.app.smb3.enumeration.ItemType.ONE_UP_MUSHROOM;
import static house.x1337.app.smb3.enumeration.ItemType.SWITCH_BLOCK;
import static house.x1337.app.smb3.enumeration.RewardDispenseSide.BELOW;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_UP_BLOCK;
import static house.x1337.app.smb3.enumeration.SuperMushroomType.GROWING_CAPABLE;
import static house.x1337.app.smb3.enumeration.SuperMushroomType.LIFE_AWARDING_GREEN;
import static org.slf4j.LoggerFactory.getLogger;

public interface RewardDispensingLevelObject extends LevelObject {
    ItemType getReward();

    void onCoinDispensed(LevelScenePlayer levelScenePlayer);

    /**
     * Dispenses a P-Switch. Implemented by the block that holds it, because the switch is placed
     * into a neighbouring grid cell rather than spawned as a moving object.
     */
    void onSwitchBlockDispensed(LevelScenePlayer levelScenePlayer);

    default void dispenseReward(
        final LevelScenePlayer levelScenePlayer,
        final RewardDispenseSide side
    ) {
        if (levelScenePlayer.isSmall() && getReward().isNoneOf(COIN_SINGLE, ONE_UP_MUSHROOM, SWITCH_BLOCK)) {
            onSuperMushroomDispensed(levelScenePlayer, GROWING_CAPABLE, side);
            return;
        }

        switch (getReward()) {
            case COIN_SINGLE:
                onCoinDispensed(levelScenePlayer);
                break;
            case SUPER_LEAF:
                onSuperLeafDispensed(levelScenePlayer, side);
                break;
            case SUPER_MUSHROOM:
                onSuperMushroomDispensed(levelScenePlayer, GROWING_CAPABLE, side);
                break;
            case ONE_UP_MUSHROOM:
                onSuperMushroomDispensed(levelScenePlayer, LIFE_AWARDING_GREEN, side);
                break;
            case SWITCH_BLOCK:
                onSwitchBlockDispensed(levelScenePlayer);
                break;
            default:
                getLogger(getClass()).warn("Unhandled Reward Type: {}", getReward());
                break;
        }
    }

    /**
     * Dispenses a super leaf. It makes no sound of its own: what struck the block (the player's head, a kicked
     * shell) plays the bump, and that bump is the leaf's sound.
     */
    default void onSuperLeafDispensed(
        final LevelScenePlayer levelScenePlayer,
        final RewardDispenseSide side
    ) {
        final SuperLeaf superLeaf = getBean(
            SuperLeaf.class,
            levelScenePlayer.getGameEngine(),
            getOffset()
        );
        if (side == BELOW) {
            superLeaf.dropOutOfTheBlockBelow();
        }
        getBean(SuperLeafMotionManager.class).spawn(superLeaf);
    }

    default void onSuperMushroomDispensed(
        final LevelScenePlayer levelScenePlayer,
        final SuperMushroomType superMushroomType,
        final RewardDispenseSide side
    ) {
        final LevelObjectType levelObjectType = superMushroomType.asLevelObjectType();
        final LevelObject levelObject = getBean(
            levelObjectType.getInstanceType(),
            levelScenePlayer.getGameEngine(),
            getOffset()
        );
        if (levelObject instanceof SuperMushroom superMushroom) {
            superMushroom.configure((SuperMushroomVariantData) levelObjectType.getVariantData());
            if (side == BELOW) {
                superMushroom.dropOutOfTheBlockBelow();
            }
            getBean(SuperMushroomMotionManager.class).spawn(superMushroom);
            getSoundPlayer().play(POWER_UP_BLOCK);
        } else {
            throw new IllegalStateException("The mushroom dispensed must be an instance of SuperMushroom");
        }
    }
}
