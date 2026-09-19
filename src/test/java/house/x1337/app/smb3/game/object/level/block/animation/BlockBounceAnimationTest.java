package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_BRICK_BLOCK_BOUNCE;
import static house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockBounceAnimationTest {

    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final Offset BLOCK_OFFSET = Offset.of(2, 3);
    private static final float BLOCK_WORLD_Y = ROWS - 1 - BLOCK_OFFSET.y();

    /** Sprite-pixel height above the block's own cell after each tick, tick 1 first. */
    private static final int[] TRACED_BOUNCE_HEIGHTS = {0, 4, 7, 9, 10, 10, 9, 7, 4, 0};

    /** Colour written into the block's cell before the bounce borrows it. */
    private static final byte TILE_MARKER = (byte) 0x7B;

    private Node rootNode;
    private Image bakedLayerImage;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        rootNode = new Node("root");
        bakedLayerImage = bakedLayerImage();
        gameEngine = gameEngineMock(bakedLayerImage);
    }

    @Test
    @DisplayName("The borrowed tile traces the bump curve and expires back on its own cell")
    void bounceTracesTheBumpCurve() {
        // Prepare
        final EmptyBlockBounceAnimation animation = emptyBlockBounce();
        final Geometry sprite = animation.getSpriteGeometry();

        // Execute & Verify
        assertThat(animation.isExpired()).as("a freshly spawned bounce has not run yet").isFalse();

        for (int tick = 0; tick < TRACED_BOUNCE_HEIGHTS.length; tick++) {
            animation.tick();

            final float expectedY = BLOCK_WORLD_Y + (float) TRACED_BOUNCE_HEIGHTS[tick] / TILE_SPRITE_SIZE;
            assertThat(sprite.getLocalTranslation().getY())
                .as("height above the block's cell after tick %d", tick + 1)
                .isEqualTo(expectedY);
            assertThat(sprite.getLocalTranslation().getX()).isEqualTo((float) BLOCK_OFFSET.x());
            assertThat(sprite.getLocalTranslation().getZ()).isEqualTo(Z_DEPTH_BRICK_BLOCK_BOUNCE);

            final boolean lastTick = tick == TRACED_BOUNCE_HEIGHTS.length - 1;
            assertThat(animation.isExpired())
                .as("the bounce runs for exactly %d ticks", TRACED_BOUNCE_HEIGHTS.length)
                .isEqualTo(lastTick);
        }
    }

    @Test
    @DisplayName("The bounce sprite is a single tile, not a tile's worth of pixels")
    void bounceSpriteIsOneTileWide() {
        // Prepare
        final EmptyBlockBounceAnimation animation = emptyBlockBounce();

        // Execute
        final Quad quad = (Quad) animation.getSpriteGeometry().getMesh();

        // Verify — sizing the quad in sprite pixels instead of game units blows it up 16-fold.
        assertThat(quad.getWidth()).isEqualTo(TILE_SIZE_GAME_UNITS);
        assertThat(quad.getHeight()).isEqualTo(TILE_SIZE_GAME_UNITS);
    }

    @Test
    @DisplayName("The bounced cell is erased from the baked layer and restored on detach")
    void bounceBorrowsAndReturnsTheTile() {
        // Prepare
        final EmptyBlockBounceAnimation animation = emptyBlockBounce();

        // Execute & Verify
        assertThat(bakedTileBytes()).as("the borrowed cell renders as the moving sprite only").containsOnly((byte) 0);

        animation.detach();
        assertThat(bakedTileBytes()).as("the cell is handed back untouched").containsOnly(TILE_MARKER);
        assertThat(rootNode.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("A bouncing brick has its shimmer paused for the bounce and resumed after")
    void brickBouncePausesItsAnimator() {
        // Prepare
        final GameObjectAnimatorSingleTiled<?> animator = mock(GameObjectAnimatorSingleTiled.class);

        // Execute
        final BrickBlockBounceAnimation animation = new BrickBlockBounceAnimation(
            gameEngine,
            animator,
            BLOCK_OFFSET
        );

        // Verify
        verify(animator).pauseAt(BLOCK_OFFSET);
        assertThat(rootNode.getChildren()).hasSize(1);

        animation.detach();
        verify(animator).resumeAt(BLOCK_OFFSET);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private EmptyBlockBounceAnimation emptyBlockBounce() {
        final ImageResource imageResource = mock(ImageResource.class);
        when(imageResource.asTexture()).thenReturn(new Texture2D(tileImage()));
        return new EmptyBlockBounceAnimation(gameEngine, BLOCK_OFFSET, imageResource);
    }

    /** The four RGBA bytes of every pixel the block's cell occupies in the baked layer. */
    private byte[] bakedTileBytes() {
        final ByteBuffer buffer = bakedLayerImage.getData(0);
        final int imageWidth = COLUMNS * TILE_SPRITE_SIZE;
        final byte[] bytes = new byte[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4];

        int index = 0;
        for (int row = 0; row < TILE_SPRITE_SIZE; row++) {
            final int imageRow = (ROWS - 1 - BLOCK_OFFSET.y()) * TILE_SPRITE_SIZE + row;
            for (int column = 0; column < TILE_SPRITE_SIZE; column++) {
                final int imageColumn = BLOCK_OFFSET.x() * TILE_SPRITE_SIZE + column;
                final int bufferIndex = (imageRow * imageWidth + imageColumn) * 4;
                for (int channel = 0; channel < 4; channel++) {
                    bytes[index++] = buffer.get(bufferIndex + channel);
                }
            }
        }
        return bytes;
    }

    private static Image tileImage() {
        return new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4),
            sRGB
        );
    }

    /**
     * A level-sized baked layer with the bounced block's cell marked, so borrowing and returning the
     * tile can be observed on a real buffer.
     */
    private static Image bakedLayerImage() {
        final int width = COLUMNS * TILE_SPRITE_SIZE;
        final int height = ROWS * TILE_SPRITE_SIZE;
        final ByteBuffer buffer = createByteBuffer(width * height * 4);

        for (int row = 0; row < TILE_SPRITE_SIZE; row++) {
            final int imageRow = (ROWS - 1 - BLOCK_OFFSET.y()) * TILE_SPRITE_SIZE + row;
            for (int column = 0; column < TILE_SPRITE_SIZE; column++) {
                final int imageColumn = BLOCK_OFFSET.x() * TILE_SPRITE_SIZE + column;
                final int bufferIndex = (imageRow * width + imageColumn) * 4;
                for (int channel = 0; channel < 4; channel++) {
                    buffer.put(bufferIndex + channel, TILE_MARKER);
                }
            }
        }
        return new Image(RGBA8, width, height, buffer, sRGB);
    }

    private GameEngine gameEngineMock(final Image bakedLayer) {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // Built up front: stubbing one mock inside another's thenReturn(...) argument leaves Mockito
        // with an unfinished stubbing.
        final Geometry bakedLayerGeometry = bakedLayerGeometry(bakedLayer);
        final AssetManager assetManager = new DesktopAssetManager(true);

        final GameEngine engine = mock(GameEngine.class);
        when(engine.getRootNode()).thenReturn(rootNode);
        when(engine.getAssetManager()).thenReturn(assetManager);
        when(engine.getLevelScene()).thenReturn(levelScene);
        when(engine.getLayerGeometry(INTERACTIVE_OBJECTS)).thenReturn(bakedLayerGeometry);
        return engine;
    }

    /**
     * A jme3 geometry whose material exposes a "ColorMap" texture backed by a real, level-sized byte
     * buffer, so the save/erase/restore of the borrowed tile happens for real instead of being
     * stubbed out. Everything below the material is a genuine object because {@code Image.getData}
     * and {@code MatParamTexture.getTextureValue} cannot be intercepted.
     */
    private static Geometry bakedLayerGeometry(final Image bakedLayer) {
        final MatParamTexture textureParam = new MatParamTexture(
            VarType.Texture2D,
            "ColorMap",
            new Texture2D(bakedLayer),
            sRGB
        );

        final Material material = mock(Material.class);
        when(material.getTextureParam(anyString())).thenReturn(textureParam);

        final Geometry geometry = mock(Geometry.class);
        when(geometry.getMaterial()).thenReturn(material);
        return geometry;
    }
}
