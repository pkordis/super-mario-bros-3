package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.block.motion.BrickBlockBreakMotionManager;
import house.x1337.app.smb3.game.object.level.reward.RewardLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_10;
import static house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

public interface BreakableBrickCapabilities extends AnimatableLevelObject, GameEngineAware {
    GameObjectAnimatorSingleTiled<?> getAnimator();

    default void hitBrickFromBelow(final LevelScenePlayer levelScenePlayer) {
        if (levelScenePlayer.isLarge()) {
            smashBrick(levelScenePlayer);
        } else {
            bumpBrick();
        }
    }

    default void smashBrick(final LevelScenePlayer levelScenePlayer) {
        if (this instanceof RewardLevelObject rewardLevelObject) {
            rewardLevelObject.setExpired(true);
        }
        levelScenePlayer
            .getCollisionGrid()
            .removeLevelObjectAt(getOffset());
        getAnimator().unregisterAt(getOffset());
        eraseFromBakedTexture(
            getGameEngine().getLayerGeometry(INTERACTIVE_OBJECTS),
            getGameEngine()
                .getLevelScene()
                .getDimensions()
        );
        getBean(BrickBlockBreakMotionManager.class).spawnBreak(getGameEngine(), getOffset());
        levelScenePlayer
            .getPlayerData()
            .addPoints(SCORE_10.getData().getPoints());
    }

    default void bumpBrick() {
        getBean(BrickBlockBreakMotionManager.class)
            .spawnBounce(getGameEngine(), getOffset(), getAnimator());
    }
}
