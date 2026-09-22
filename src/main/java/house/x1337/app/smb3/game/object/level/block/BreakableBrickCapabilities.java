package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.block.motion.BrickBlockMotionManager;
import house.x1337.app.smb3.game.object.level.reward.RewardLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_10;

public interface BreakableBrickCapabilities extends AnimatableLevelObject, GameEngineAware {
    GameObjectAnimatorSingleTiled<?> getAnimator();

    default void hitBrickFromBelow(final LevelScenePlayer levelScenePlayer) {
        if (levelScenePlayer.isLarge()) {
            smashBrick(levelScenePlayer);
        } else {
            bumpBrick();
        }
    }

    /**
     * Retires this brick: out of collision, off its animator, and out of the baked texture.
     *
     * <p>The texture has to be erased from the layer that actually paints the tile, which is not always
     * the interactive-objects one — a brick authored as terrain lives in the static environment layer, and
     * erasing the wrong layer leaves it on screen while it is gone from collision. The grid tracks that
     * provenance per cell, so it is read before the cell is retired.
     */
    default void smashBrick(final LevelScenePlayer levelScenePlayer) {
        if (this instanceof RewardLevelObject rewardLevelObject) {
            rewardLevelObject.setExpired(true);
        }
        final StaticEnvironmentCollisionGrid collisionGrid = levelScenePlayer.getCollisionGrid();
        final LevelSceneLayerType sourceLayer = collisionGrid.getSourceLayerAt(getOffset());
        collisionGrid.removeLevelObjectAt(getOffset());
        getAnimator().unregisterAt(getOffset());
        eraseFromBakedTexture(
            getGameEngine().getLayerGeometry(sourceLayer),
            getGameEngine()
                .getLevelScene()
                .getDimensions()
        );
        getBean(BrickBlockMotionManager.class).spawnBreak(getGameEngine(), getOffset());
        levelScenePlayer
            .getPlayerData()
            .addPoints(SCORE_10.getData().getPoints());
    }

    default void bumpBrick() {
        getBean(BrickBlockMotionManager.class)
            .spawnBounce(getGameEngine(), getOffset(), getAnimator());
    }
}
