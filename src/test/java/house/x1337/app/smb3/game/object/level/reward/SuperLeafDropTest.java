package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A leaf dropped out of the bottom of its block: it starts in the lower half of the block and falls, speeding up, for
 * seven ticks before it flutters as a leaf that came out of the top does.
 */
class SuperLeafDropTest {
    private static final double TOLERANCE = 1e-9;
    private static final Coordinates BLOCK = of(3, 4);
    private static final double BLOCK_X = BLOCK.x() * (double) TILE_SPRITE_SIZE;
    private static final double BLOCK_TOP_Y = BLOCK.y() * (double) TILE_SPRITE_SIZE;
    private static final int DROP_TICKS = 7;

    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(8, 8));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));
    }

    @Test
    @DisplayName("It starts 12 px into the block and does not rise")
    void itStartsBelowTheTopOfTheBlock() {
        // Prepare & Execute
        final SuperLeaf leaf = dropped();

        // Verify
        assertThat(leaf.isRising()).isFalse();
        assertThat(leaf.getPosition().x()).isCloseTo(BLOCK_X, within(TOLERANCE));
        assertThat(leaf.getPosition().y()).isCloseTo(BLOCK_TOP_Y + 12, within(TOLERANCE));
    }

    @Test
    @DisplayName("It falls for seven ticks, a sixteenth of a pixel faster each, without moving sideways")
    void itFallsAndSpeedsUp() {
        // Prepare
        final SuperLeaf leaf = dropped();
        double expectedY = BLOCK_TOP_Y + 12;

        // Execute & Verify
        for (int tick = 0; tick < DROP_TICKS; tick++) {
            leaf.motionUpdate();
            expectedY += (4 + tick) / 16.0;
            assertThat(leaf.getPosition().y()).as("Tick %d", tick).isCloseTo(expectedY, within(TOLERANCE));
            assertThat(leaf.getPosition().x()).as("Tick %d", tick).isCloseTo(BLOCK_X, within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("After the fall it flutters, swinging sideways")
    void itFluttersAfterTheFall() {
        // Prepare
        final SuperLeaf leaf = dropped();
        for (int tick = 0; tick < DROP_TICKS; tick++) {
            leaf.motionUpdate();
        }

        // Execute
        leaf.motionUpdate();

        // Verify
        assertThat(leaf.getVelocity().x()).as("Swinging").isNotZero();
        assertThat(leaf.getPosition().x()).isNotCloseTo(BLOCK_X, within(TOLERANCE));
    }

    private SuperLeaf dropped() {
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
        leaf.dropOutOfTheBlockBelow();
        return leaf;
    }
}