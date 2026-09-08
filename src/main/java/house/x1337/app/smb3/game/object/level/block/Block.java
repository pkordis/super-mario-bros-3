package house.x1337.app.smb3.game.object.level.block;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.*;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.RewardDispensingLevelObject;
import house.x1337.app.smb3.game.object.level.block.animation.QuestionBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.block.animation.BrickBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.motion.BrickBlockBreakMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
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
import static house.x1337.app.smb3.enumeration.BlockType.BRICK_BLOCK_WITH_REWARD;
import static house.x1337.app.smb3.enumeration.BlockType.QUESTION_BLOCK;
import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_10;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

/**
 * A single-tile block, parameterized by its {@link LevelObjectType} into the three flavours the ROM
 * distinguishes. All three share the vast majority of their behaviour, so they live in one class:
 *
 * <ul>
 *   <li><b>{@code QUESTION_BLOCK}</b> — reward block with the shimmering "?" look
 *       ({@link QuestionBlockAnimator}).</li>
 *   <li><b>{@code BRICK_BLOCK_WITH_REWARD}</b> — <em>identical</em> reward behaviour to the question
 *       block (dispense → become an {@link EmptyBlock} → bounce), but wearing the plain brick look
 *       and shimmer of the breakable brick ({@link BrickBlockAnimator}). In the ROM a brick that
 *       contains an item never breaks — it spends its contents and turns solid, exactly like a "?"
 *       block.</li>
 *   <li><b>{@code BRICK_BLOCK_NO_REWARD}</b> — breakable brick: large Mario shatters it into four
 *       fragments; small Mario bounces it in place (dasm {@code prg008.asm LATP_Brick} /
 *       {@code prg001.asm ObjNorm_BounceDU}).</li>
 * </ul>
 *
 * <p>The look/shimmer is selected purely by {@link #getAnimator()}; whether a hit dispenses a reward
 * or breaks the block is selected purely by {@link #hasReward()}. Both derive from {@link #blockType},
 * which is injected at instantiation (see {@code LevelObjectRecordCapabilities#toLevelObject}).
 */
@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class Block implements AnimatableLevelObject, RewardDispensingLevelObject {
    private final QuestionBlockAnimator questionBlockAnimator = getBean(QuestionBlockAnimator.class);
    private final BrickBlockAnimator brickBlockAnimator = getBean(BrickBlockAnimator.class);
    private final CoinRewardMotionManager coinRewardMotionManager = getBean(CoinRewardMotionManager.class);
    private final BrickBlockBreakMotionManager brickBlockBreakMotionManager =
        getBean(BrickBlockBreakMotionManager.class);
    private final LevelObjectService levelObjectService = getBean(LevelObjectService.class);
    private final Reward breakReward = SCORE_10;

    private final GameEngine gameEngine;
    private final ImageResource imageResource;
    private final Offset offset;

    private LevelObjectType type;
    private ItemType reward;
    private BlockType blockType;

    /**
     * Reward blocks ({@code QUESTION_BLOCK}, {@code BRICK_BLOCK_WITH_REWARD}) dispense an item and turn
     * into an {@link EmptyBlock}; {@code BRICK_BLOCK_NO_REWARD} breaks/bounces instead.
     */
    private boolean hasReward() {
        return blockType == QUESTION_BLOCK || blockType == BRICK_BLOCK_WITH_REWARD;
    }

    /**
     * The shimmer animator that also paints this block's look: the "?" animator for a question block,
     * the plain-brick animator for either brick flavour. This is the <em>only</em> thing that differs
     * between a question block and a brick-with-reward.
     */
    private GameObjectAnimatorSingleTiled<Block> getAnimator() {
        return blockType == QUESTION_BLOCK ? questionBlockAnimator : brickBlockAnimator;
    }

    @Override
    public void configure(final LevelObjectData data) {
        blockType = data
            .getEnum(BlockType.class, "blockType")
            .orElseThrow();
        type = Enum.valueOf(LevelObjectTypeSingleTiled.class, blockType.name());
        if (!hasReward()) {
            return;
        }
        reward = data
            .getEnum(ItemType.class, "reward")
            .orElse(COIN_SINGLE);
        log.debug(
            "Reward block {} at {}x{}, configured with reward {}",
            blockType,
            offset.x(),
            offset.y(),
            reward
        );
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        if (hasReward()) {
            hit(levelScenePlayer);
        } else if (levelScenePlayer.isLarge()) {
            // Large Mario shatters the brick into four flying fragments.
            triggerBreak(levelScenePlayer);
        } else {
            // Small Mario cannot break it — the brick bounces in place and stays solid.
            brickBlockBreakMotionManager.spawnBounce(gameEngine, offset);
        }
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (hasReward()) {
            hit(levelScenePlayer);
        } else {
            triggerBreak(levelScenePlayer);
        }
    }

    /**
     * Reward-block hit: stop the shimmer, erase the tile, drop a fresh {@link EmptyBlock} in the
     * collision grid, bake the empty tile, fire the one-shot bounce on the replacement, and dispense
     * the configured reward. Shared verbatim by {@code QUESTION_BLOCK} and {@code BRICK_BLOCK_WITH_REWARD}
     * — only {@link #getAnimator()} differs.
     */
    private void hit(final LevelScenePlayer levelScenePlayer) {
        final StaticEnvironmentCollisionGrid collisionGrid = levelScenePlayer.getCollisionGrid();
        final GameEngine gameEngine = levelScenePlayer.getGameEngine();
        final LevelSceneDimensions dimensions = gameEngine.getLevelScene().getDimensions();
        final Geometry interactiveObjectsLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);

        // Step 1: Stop shimmer animation and erase this block's tile from the baked texture.
        getAnimator().unregisterAt(offset);
        eraseFromBakedTexture(interactiveObjectsLayerGeometry, dimensions);

        // Step 2: Place a fresh EmptyBlock at the same position in the collision grid.
        final EmptyBlock emptyBlock = levelObjectService.createEmptyBlock(gameEngine, offset);
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

        // Step 5: Dispense the reward based on configured type.
        dispenseReward(levelScenePlayer);
    }

    /**
     * Breakable-brick hit by large Mario: remove from the collision grid, stop the shimmer, erase the
     * tile, spawn the four flying fragments, and award {@link #breakReward}. Ported from dasm
     * {@code prg008.asm LATP_Brick} / {@code prg007.asm BrickBusts_DrawAndUpdate}.
     */
    private void triggerBreak(final LevelScenePlayer levelScenePlayer) {
        final LevelSceneDimensions dimensions = gameEngine.getLevelScene().getDimensions();
        final Geometry interactiveObjectsLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);
        final StaticEnvironmentCollisionGrid collisionGrid = levelScenePlayer.getCollisionGrid();

        // Remove from collision grid so further probes treat it as empty.
        collisionGrid.removeLevelObjectAt(offset);

        getAnimator().unregisterAt(offset);
        eraseFromBakedTexture(interactiveObjectsLayerGeometry, dimensions);
        brickBlockBreakMotionManager.spawnBreak(gameEngine, offset);
        levelScenePlayer
            .getPlayerData()
            .addPoints(breakReward.getData().getPoints());
    }

    @Override
    public void onCoinDispensed(final LevelScenePlayer levelScenePlayer) {
        final PlayerData playerData = levelScenePlayer.getPlayerData();
        coinRewardMotionManager
            .spawnCoinReward(
                levelScenePlayer.getGameEngine(),
                SCORE_100,
                offset
            )
            .whenCompleteAsync((score, error) -> {
                // The score is awarded after the coin flipping is complete and before the score's integer is rendered
                // popping in the air
                levelScenePlayer.getPlayerData().addPoints(score);
            });
        playerData.addCoin();
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

        getAnimator().writeTile(dimensions, buffer, pixels, offset, imageWidth);
        image.setUpdateNeeded();
    }
}
