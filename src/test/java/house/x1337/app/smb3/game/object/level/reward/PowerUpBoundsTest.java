package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.reward.core.PowerUpCollectProtection;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUND_BOX_HEIGHT;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUND_BOX_LEFT;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUND_BOX_TOP;
import static house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject.POWER_UP_BOUND_BOX_WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The block power-ups' shared hitbox, {@code OAT_BOUNDBOX01} (dasm prg000 {@code Object_BoundBox} entry
 * 1), and the Super Leaf's use of it.
 *
 * <p>The box is pinned against a direct transcription of {@code ObjectObject_Intersect}, which is
 * inclusive, rather than against hand-picked numbers: the engine's test is half-open and the player's box
 * keeps the ROM's literal extents, so it is the agreement of the two tests that matters.
 */
class PowerUpBoundsTest {
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Offset BLOCK = Offset.of(3, 4);
    private static final double BLOCK_X = BLOCK.x() * (double) TILE_SPRITE_SIZE;

    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));
    }

    @Test
    @DisplayName("The box is OAT_BOUNDBOX01: left 1, width 13, top 2, height 8")
    void theBoxIsBoundBoxOne() {
        // Execute & Verify - prg000.asm:280, ".byte 1, 13, 2, 8"
        assertThat(POWER_UP_BOUND_BOX_LEFT).isEqualTo(1);
        assertThat(POWER_UP_BOUND_BOX_WIDTH).isEqualTo(13);
        assertThat(POWER_UP_BOUND_BOX_TOP).isEqualTo(2);
        assertThat(POWER_UP_BOUND_BOX_HEIGHT).isEqualTo(8);
    }

    @Test
    @DisplayName("In engine space the inclusive box spans X [x, x+15) and Y [y+1, y+11)")
    void theEngineBoxAbsorbsTheInclusiveEdges() {
        // Prepare
        final RewardLevelObject reward = rewardAt(100, 50);

        // Execute
        final AxisAlignedBoundingBox box = reward.getBounds();

        // Verify
        assertThat(box).isEqualTo(new AxisAlignedBoundingBox(100, 51, 115, 61));
    }

    @ParameterizedTest
    @CsvSource({
        // player box: left offset, width, top offset, height (Player_BoundBox)
        "3, 10, 5, 25",
        "4, 8, 17, 13"
    })
    @DisplayName("Against the player's box it agrees with ObjectObject_Intersect at every whole-pixel offset")
    void itAgreesWithTheRomIntersectionEverywhere(
        final int playerLeftOffset,
        final int playerWidth,
        final int playerTopOffset,
        final int playerHeight
    ) {
        // Prepare
        final int objectX = 64;
        final int objectY = 64;
        final AxisAlignedBoundingBox objectBox = rewardAt(objectX, objectY).getBounds();

        // Execute & Verify - every player position from well clear on one side to well clear on the other
        for (int playerX = objectX - 32; playerX <= objectX + 32; playerX++) {
            for (int playerY = objectY - 48; playerY <= objectY + 32; playerY++) {
                final int playerLeft = playerX + playerLeftOffset;
                final int playerTop = playerY + playerTopOffset;
                // As LevelScenePlayer.getObjectCollisionBounds builds it
                final AxisAlignedBoundingBox playerBox = new AxisAlignedBoundingBox(
                    playerLeft,
                    playerTop,
                    playerLeft + playerWidth,
                    playerTop + playerHeight
                );
                assertThat(objectBox.intersects(playerBox))
                    .as("Player sprite at (%d, %d)", playerX, playerY)
                    .isEqualTo(romIntersects(objectX, objectY, playerLeft, playerWidth, playerTop, playerHeight));
            }
        }
    }

    // -------------------------------------------------------------------------
    // The Super Leaf
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Back over its spawn column, the leaf is out of reach of a player standing flush beside the block")
    void theLeafClearsAPlayerFlushAgainstTheBlock() {
        // Prepare - a large player held against each side of the block by the wall nudge: its right body
        // edge (X + 14) on the block's left face, or its left body edge (X + 2) on the right face
        final double flushLeftX = BLOCK_X - 14;
        final double flushRightX = BLOCK_X + TILE_SPRITE_SIZE - 2;

        // Execute & Verify - the leaf over its spawn column at every height the player's box spans
        for (double leafY = -64; leafY <= 128; leafY++) {
            final AxisAlignedBoundingBox leafBox = rewardAt(BLOCK_X, leafY).getBounds();
            for (double playerY = -16; playerY <= 96; playerY++) {
                assertThat(leafBox.intersects(largePlayerBox(flushLeftX, playerY)))
                    .as("Flush left, leaf Y %s, player Y %s", leafY, playerY)
                    .isFalse();
                assertThat(leafBox.intersects(largePlayerBox(flushRightX, playerY)))
                    .as("Flush right, leaf Y %s, player Y %s", leafY, playerY)
                    .isFalse();
            }
        }
    }

    @Test
    @DisplayName("A freshly dispensed leaf cannot be collected for its first 15 frames, and can on the 16th")
    void theLeafKeepsItsCollectProtection() {
        // Prepare
        final SuperLeaf leaf = dispenseLeaf();

        // Execute & Verify
        for (int frame = 1; frame < PowerUpCollectProtection.FRAMES; frame++) {
            leaf.motionUpdate();
            assertThat(leaf.isCollectable()).as("Frame %d", frame).isFalse();
        }
        leaf.motionUpdate();
        assertThat(leaf.isCollectable()).as("Objects_Timer2 = $10 has run out").isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * {@code ObjectObject_Intersect} (prg000 {@code $D97B}) on {@code Object_CalcBoundBox}'s output: on
     * each axis, the difference between the two boxes' near edges must not exceed the extent of whichever
     * box lies first. Inclusive, so touching counts.
     */
    private static boolean romIntersects(
        final int objectX,
        final int objectY,
        final int playerLeft,
        final int playerWidth,
        final int playerTop,
        final int playerHeight
    ) {
        final int objectLeft = objectX + POWER_UP_BOUND_BOX_LEFT;
        final int objectTop = objectY + POWER_UP_BOUND_BOX_TOP;
        final int dx = playerLeft - objectLeft;
        final boolean overlapX = dx >= 0 ? dx <= POWER_UP_BOUND_BOX_WIDTH : -dx <= playerWidth;
        final int dy = playerTop - objectTop;
        final boolean overlapY = dy >= 0 ? dy <= POWER_UP_BOUND_BOX_HEIGHT : -dy <= playerHeight;
        return overlapX && overlapY;
    }

    /** {@code Player_BoundBox} entry 1, as {@code LevelScenePlayer.getObjectCollisionBounds} builds it. */
    private static AxisAlignedBoundingBox largePlayerBox(final double x, final double y) {
        return new AxisAlignedBoundingBox(x + 3, y + 5, x + 13, y + 30);
    }

    /**
     * A reward standing at a sprite-pixel position, answering {@link RewardLevelObject#getBounds()} with
     * the real shared default, so the box under test is the one every power-up inherits.
     */
    private static RewardLevelObject rewardAt(final double x, final double y) {
        final RewardLevelObject reward = mock(RewardLevelObject.class);
        when(reward.getPosition()).thenReturn(new PixelPosition(x, y));
        doCallRealMethod().when(reward).getBounds();
        return reward;
    }

    private SuperLeaf dispenseLeaf() {
        final ImageResource image = mock(ImageResource.class);
        when(image.getDimensions()).thenReturn(new DimensionsPixels(TILE_SPRITE_SIZE, 14));
        final Texture2D texture = new Texture2D(new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            14,
            createByteBuffer(TILE_SPRITE_SIZE * 14 * 4),
            sRGB
        ));
        when(image.asTexture()).thenReturn(texture);

        final SuperLeaf leaf = new SuperLeaf(gameEngine, BLOCK);
        leaf.setImageResource(image);
        leaf.init();
        return leaf;
    }
}
