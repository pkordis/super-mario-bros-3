package house.x1337.app.smb3.game.object.level.block;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.animation.management.CoinRewardAnimationManager;
import house.x1337.app.smb3.game.object.level.block.animation.management.QuestionBlockAnimator;
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
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class QuestionBlock implements LevelObject, AnimatableLevelObject {
    private final QuestionBlockAnimator questionBlockAnimator = getBean(QuestionBlockAnimator.class);
    private final CoinRewardAnimationManager coinRewardAnimationManager = getBean(CoinRewardAnimationManager.class);
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
        dispenseReward(gameEngine);
    }

    /**
     * Dispenses the configured reward when the block is hit.
     *
     * <p>Currently supports:
     * <ul>
     *   <li>{@code COIN_SINGLE} — spawns a coin pop animation and "100" score popup</li>
     * </ul>
     *
     * @param gameEngine the game engine
     */
    private void dispenseReward(final GameEngine gameEngine) {
        if (reward == COIN_SINGLE) {
            coinRewardAnimationManager.spawnCoinReward(gameEngine, offset);
        }
        // TODO: Handle other reward types (POWER_UP, STAR, etc.)
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

        for (int spriteRow = 0; spriteRow < TILE_SPRITE_SIZE; spriteRow++) {
            // jme3 ByteBuffer row 0 = bottom of level image (highest tileRow index).
            // Y-flip: imgRow = (totalRows - 1 - tileRow) * 16 + (15 - sprRow)
            final int imgRow = (dimensions.rows() - 1 - offset.y()) * TILE_SPRITE_SIZE
                + (TILE_SPRITE_SIZE - 1 - spriteRow);
            for (int spriteCol = 0; spriteCol < TILE_SPRITE_SIZE; spriteCol++) {
                final int imgCol = offset.x() * TILE_SPRITE_SIZE + spriteCol;
                final int argb = pixels[spriteRow * TILE_SPRITE_SIZE + spriteCol];
                final int bufferIdx = (imgRow * imageWidth + imgCol) * 4;
                buffer.put(bufferIdx, (byte) ((argb >> 16) & 0xFF));     // R
                buffer.put(bufferIdx + 1, (byte) ((argb >> 8) & 0xFF));  // G
                buffer.put(bufferIdx + 2, (byte) (argb & 0xFF));         // B
                buffer.put(bufferIdx + 3, (byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        image.setUpdateNeeded();
    }
}
