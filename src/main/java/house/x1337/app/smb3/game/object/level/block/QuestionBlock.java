package house.x1337.app.smb3.game.object.level.block;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.RewardDispensingLevelObject;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.block.animation.QuestionBlockAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.service.LevelObjectService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;
import static house.x1337.app.smb3.enumeration.Score.SCORE_100;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class QuestionBlock implements AnimatableLevelObject, RewardDispensingLevelObject {
    private final QuestionBlockAnimator questionBlockAnimator = getBean(QuestionBlockAnimator.class);
    private final CoinRewardMotionManager coinRewardAnimationManager = getBean(CoinRewardMotionManager.class);
    private final LevelObjectService levelObjectService = getBean(LevelObjectService.class);
    private final LevelObjectType type = QUESTION_BLOCK;
    private final ImageResource imageResource;
    private final Offset offset;
    private ItemType reward;

    @Override
    public void configure(final LevelObjectData data) {
        reward = data
            .getEnum(ItemType.class, "reward")
            .orElse(COIN_SINGLE);
        log.debug(
            "Question block at {}x{}, configured with reward {}",
            offset.x(),
            offset.y(),
            reward
        );
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        final CollisionGrid collisionGrid = levelScenePlayer.getCollisionGrid();
        final GameEngine gameEngine = levelScenePlayer.getGameEngine();
        final LevelSceneDimensions dimensions = gameEngine.getLevelScene().getDimensions();
        final Geometry interactiveObjectsLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);

        // Step 1: Stop shimmer animation and erase this block's tile from the baked texture.
        questionBlockAnimator.unregisterAt(offset);
        eraseFromBakedTexture(interactiveObjectsLayerGeometry, dimensions);

        // Step 2: Place a fresh EmptyBlock at the same position in the collision grid.
        final EmptyBlock emptyBlock = levelObjectService.createEmptyBlock(offset);
        collisionGrid.placeLevelObjectAt(offset, emptyBlock);

        // Step 3: Bake the empty block tile directly into the texture.
        bakeTileToTexture(
            interactiveObjectsLayerGeometry,
            dimensions,
            emptyBlock
                .getImageResource()
                .getRgbData()
        );

        // Step 4: Immediately fire the one-shot bounce on the replacement block.
        emptyBlock.triggerBounce(gameEngine);

        // Step 5: Dispense the reward based on configured type
        dispenseReward(levelScenePlayer);
    }

    @Override
    public void onCoinDispensed(final LevelScenePlayer levelScenePlayer) {
        coinRewardAnimationManager
            .spawnCoinReward(
                levelScenePlayer.getGameEngine(),
                SCORE_100,
                offset
            )
            .whenCompleteAsync((score, error) -> {
                // The score is awarded after the coin flipping is complete and before the score's integer is rendered
                // popping in the air
                levelScenePlayer.getPlayerData().addToScore(score);
            });
    }

    private void bakeTileToTexture(
        final Geometry interactiveObjectsLayerGeometry,
        final LevelSceneDimensions dimensions,
        final int[] pixels
    ) {
        final Texture2D texture = (Texture2D) interactiveObjectsLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
        final Image image = texture.getImage();
        final ByteBuffer buffer = image.getData(0);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        questionBlockAnimator.writeTile(dimensions, buffer, pixels, offset, imageWidth);
        image.setUpdateNeeded();
    }
}
