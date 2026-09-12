package house.x1337.app.smb3.game.object.level.block;

import com.jme3.scene.Geometry;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.BlockType;
import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.RewardDispensingLevelObject;
import house.x1337.app.smb3.game.object.level.block.animation.BrickBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.animation.QuestionBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.animation.SwitchBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.effect.PoofMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
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
import static house.x1337.app.smb3.enumeration.BlockType.BRICK_SWITCH_BLOCK_SPAWNER;
import static house.x1337.app.smb3.enumeration.BlockType.QUESTION_BLOCK;
import static house.x1337.app.smb3.enumeration.BlockType.QUESTION_SWITCH_BLOCK_SPAWNER;
import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

/**
 * A single-tile block, parameterized by its {@link LevelObjectType} into the flavours the ROM
 * distinguishes. They share the vast majority of their behaviour, so they live in one class:
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
 *   <li><b>{@code BRICK_SWITCH_BLOCK_SPAWNER} / {@code QUESTION_SWITCH_BLOCK_SPAWNER}</b> — reward
 *       blocks whose contents are a {@link SwitchBlock} (P-Switch). They behave exactly like a
 *       question block, except the "reward" is placed into the cell directly above instead of being
 *       launched (see {@link #onSwitchBlockDispensed}). The two differ only in the look they wear.</li>
 * </ul>
 *
 * <p>The look/shimmer is selected by {@link #getAnimator()}, which resolves whichever animator declares
 * support for this block's {@link #type}; whether a hit dispenses a reward or breaks the block is
 * selected purely by {@link #hasReward()}. Both derive from {@link #blockType}, which is injected at
 * instantiation (see {@code LevelObjectRecordCapabilities#toLevelObject}).
 */
@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class Block implements AnimatableLevelObject, BreakableBrickCapabilities, RewardDispensingLevelObject {
    private final QuestionBlockAnimator questionBlockAnimator = getBean(QuestionBlockAnimator.class);
    private final BrickBlockAnimator brickBlockAnimator = getBean(BrickBlockAnimator.class);
    private final CoinRewardMotionManager coinRewardMotionManager = getBean(CoinRewardMotionManager.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
    private final LevelObjectService levelObjectService = getBean(LevelObjectService.class);

    private final GameEngine gameEngine;
    private final ImageResource imageResource;
    private final Offset offset;

    private LevelObjectType type;
    private ItemType reward;
    private BlockType blockType;

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
        } else {
            hitBrickFromBelow(levelScenePlayer);
        }
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (isCoinSubstituted()) {
            return;
        }
        if (hasReward()) {
            hit(levelScenePlayer);
        } else {
            smashBrick(levelScenePlayer);
        }
    }

    /**
     * While a P-Switch is active a breakable brick reads as {@code TILEA_COIN}, so touching it collects
     * it — the ROM's own consequence of the substitution, not a special case: the coin branch of
     * {@code Player_DoSpecialTiles} (dasm {@code prg008.asm PRG008_B604}) compares the tile it just
     * fetched, and that fetch has already been through {@code PSwitch_SubstTileAndAttr}
     * ({@code prg008.asm:4331}, {@code :4368}).
     *
     * <p>The ROM queues {@code CHNGTILE_DELETECOIN} — leaving {@code TILEA_COINREMOVED} ($41), a
     * background tile — and calls {@code Level_RecordBlockHit} "so it does not come back", so the brick
     * is gone permanently, not just for the window. No score is awarded, only the coin counter; the
     * handler adds nothing to the score, unlike a coin dispensed from a block.
     */
    @Override
    public void onPlayerOverlap(final LevelScenePlayer levelScenePlayer) {
        if (!isCoinSubstituted()) {
            return;
        }
        final LevelSceneDimensions dimensions = gameEngine.getLevelScene().getDimensions();
        final Geometry interactiveObjectsLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);

        // Retire the tile from the animator before erasing it, or the next frame flip repaints the coin
        // over the hole.
        getAnimator().unregisterAt(offset);
        eraseFromBakedTexture(interactiveObjectsLayerGeometry, dimensions);
        levelScenePlayer
            .getCollisionGrid()
            .removeLevelObjectAt(offset);
        levelScenePlayer
            .getPlayerData()
            .addCoin();

        log.debug("Brick collected as coin at {}x{}", offset.x(), offset.y());
    }

    /**
     * While a P-Switch is active a breakable brick is a coin, and a coin is not solid: the player walks
     * and falls straight through it until the window closes.
     *
     * <p>This is the read-time half of the ROM's {@code PSwitch_SubstTileAndAttr} substitution
     * (dasm {@code prg000.asm:1599}): the level grid is never rewritten, the tile lookup simply answers
     * {@code TILEA_COIN} — with the coin's attribute — for as long as {@code Level_PSwitchCnt} is
     * non-zero, so nothing needs restoring when it expires. {@code BrickBlockAnimator} is the other
     * half, painting the coin.
     *
     * @return {@code false} only for a breakable brick inside an active P-Switch window
     */
    @Override
    public boolean isCollidable() {
        return !isCoinSubstituted();
    }

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
            offset,
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
     * tile, spawn the four flying fragments, and award 10 points. Ported from dasm
     * {@code prg008.asm LATP_Brick} / {@code prg007.asm BrickBusts_DrawAndUpdate}, and shared with the
     * P-Switch-substituted coin via {@link BreakableBrickCapabilities}.
     */
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

    /**
     * Places the P-Switch in the cell directly above this spawner.
     *
     * <p>Ported from dasm {@code prg008.asm LATP_PSwitch}: the handler takes the hit tile's Y,
     * aligns it to the tile grid ({@code AND #$F0}) and subtracts 16 — exactly one row up — then
     * queues {@code CHNGTILE_PSWITCHAPPEAR} ($12 → {@code TILEA_PSWITCH}) at that spot. It returns
     * {@code Y = 1} into {@code Bouncer_PUp}, i.e. "spawn nothing": unlike a mushroom or a leaf, the
     * switch is <em>placed</em> as a tile rather than launched as a moving object. The handler never
     * reads {@code Player_Suit}, so the spawn is independent of the player's size.
     *
     * <p>The write is unconditional in the ROM, so whatever occupied the cell above is replaced. Valid
     * level data always leaves that cell empty, since otherwise the switch would appear embedded in
     * terrain.
     *
     * <p>Not yet ported: {@code LATP_PSwitch} also grabs a free special-object slot and puts an
     * {@code SOBJ_POOF} there with a counter of $20, so the switch appears in a puff of smoke.
     */
    @Override
    public void onSwitchBlockDispensed(final LevelScenePlayer levelScenePlayer) {
        if (offset.y() <= 0) {
            log.warn(
                "Switch block spawner at {}x{} sits on the top row; there is no cell above to spawn into",
                offset.x(),
                offset.y()
            );
            return;
        }
        final Offset switchOffset = Offset.of(offset.x(), offset.y() - 1);
        final SwitchBlock switchBlock = getBean(SwitchBlock.class, gameEngine, switchOffset);
        final SwitchBlockAnimator switchBlockAnimator = switchBlock.getSwitchBlockAnimator();

        levelScenePlayer
            .getCollisionGrid()
            .placeLevelObjectAt(switchOffset, switchBlock);
        switchBlockAnimator.add(switchBlock);

        // Paint frame 0 straight away: the animator only repaints on its own frame flip, so without
        // this the switch would stay invisible for up to a full frame interval after appearing.
        bakeTileToTexture(
            gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS),
            gameEngine.getLevelScene().getDimensions(),
            switchOffset,
            switchBlockAnimator
                .getAnimationFrames()
                .getFrameRgbData(0)
        );

        // The ROM claims a SOBJ_POOF slot at the very same cell in the same routine, so the puff starts
        // on the tick the switch appears (dasm prg008 LATP_PSwitch @ PRG008_B8C9: SpecialObj_Data = $20,
        // and SpecialObj_YLo/XLo take the same coordinates as the tile change).
        getBean(PoofMotionManager.class).spawn(gameEngine, switchOffset);

        log.debug("Switch block spawned at {}x{}", switchOffset.x(), switchOffset.y());
    }

    private boolean hasReward() {
        return blockType.isAnyOf(
            QUESTION_BLOCK,
            BRICK_BLOCK_WITH_REWARD,
            BRICK_SWITCH_BLOCK_SPAWNER,
            QUESTION_SWITCH_BLOCK_SPAWNER
        );
    }

    private boolean isCoinSubstituted() {
        return !hasReward() && powerSwitchTimeWindow.isActive();
    }

    /**
     * The animator wearing this flavour's look: the shimmering "?" for the question-block family, the
     * plain brick for everything else. Exposed because {@link BreakableBrickCapabilities} has to retire this tile
     * from whichever animator paints it.
     *
     * @return the animator this block is registered with
     */
    @Override
    public GameObjectAnimatorSingleTiled<Block> getAnimator() {
        return blockType.isAnyOf(QUESTION_BLOCK, QUESTION_SWITCH_BLOCK_SPAWNER)
            ? questionBlockAnimator
            : brickBlockAnimator;
    }

    private void bakeTileToTexture(
        final Geometry interactiveObjectsLayerGeometry,
        final LevelSceneDimensions dimensions,
        final Offset targetOffset,
        final int[] pixels
    ) {
        final Texture2D texture = (Texture2D) interactiveObjectsLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
        final Image image = texture.getImage();
        final ByteBuffer buffer = image.getData(0);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        getAnimator().writeTile(dimensions, buffer, pixels, targetOffset, imageWidth);
        image.setUpdateNeeded();
    }
}
