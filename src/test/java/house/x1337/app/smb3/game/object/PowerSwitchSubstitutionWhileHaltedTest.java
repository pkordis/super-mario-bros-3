package house.x1337.app.smb3.game.object;

import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.game.object.level.block.animator.BrickBlockAnimator;
import house.x1337.app.smb3.game.object.level.reward.Coin;
import house.x1337.app.smb3.game.object.level.reward.animation.CoinAnimator;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.BRICK_BLOCK_NO_REWARD;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.COIN_FLIPPING;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * While gameplay is halted the brick and coin tiles stop cycling their frames, but the P-switch window keeps
 * counting, so the tiles still swap their look the tick the window opens or closes. A scene built while the window is
 * open, as after a pipe, shows the swapped look from its first frame.
 */
class PowerSwitchSubstitutionWhileHaltedTest {
    private static final int FIRST_FRAME_RED = 10;
    private static final int SUBSTITUTE_RED = 200;

    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final ByteBuffer layer = createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PowerSwitchTimeWindow.class))
            .thenReturn(powerSwitchTimeWindow);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A brick swaps to a coin and back on halted ticks, without cycling its frames")
    void aBrickSwapsItsLookWhileHalted() {
        // Prepare
        final BrickBlockAnimator animator = new BrickBlockAnimator();
        ReflectionTestUtils.setField(animator, "animationFrames", animationFrames());
        ReflectionTestUtils.setField(animator, "powerSwitchedCoinImageResource", substitute());
        final Block block = mock(Block.class);

        // Execute & Verify
        register(animator, block, BRICK_BLOCK_NO_REWARD);
        assertTheLookSwapsWhileHalted(animator);
    }

    @Test
    @DisplayName("A coin swaps to a brick and back on halted ticks, without cycling its frames")
    void aCoinSwapsItsLookWhileHalted() {
        // Prepare
        final CoinAnimator animator = new CoinAnimator();
        ReflectionTestUtils.setField(animator, "animationFrames", animationFrames());
        ReflectionTestUtils.setField(animator, "powerSwitchedBrickImageResource", substitute());
        final Coin coin = mock(Coin.class);

        // Execute & Verify
        register(animator, coin, COIN_FLIPPING);
        assertTheLookSwapsWhileHalted(animator);
    }

    @Test
    @DisplayName("A scene built while the window is open paints its bricks as coins from the first frame")
    void aSceneBuiltWithTheWindowOpenStartsSubstituted() {
        // Prepare
        powerSwitchTimeWindow.activate();
        final BrickBlockAnimator animator = new BrickBlockAnimator();
        ReflectionTestUtils.setField(animator, "animationFrames", animationFrames());
        ReflectionTestUtils.setField(animator, "powerSwitchedCoinImageResource", substitute());

        // Execute
        register(animator, mock(Block.class), BRICK_BLOCK_NO_REWARD);

        // Verify
        assertThat(paintedRed()).isEqualTo(SUBSTITUTE_RED);
    }

    private void assertTheLookSwapsWhileHalted(final GameObjectAnimator<?> animator) {
        assertThat(paintedRed()).as("First frame once registered").isEqualTo(FIRST_FRAME_RED);

        for (int tick = 0; tick < 16; tick++) {
            animator.updateWhileHalted();
        }
        assertThat(paintedRed()).as("The frames stand still while halted").isEqualTo(FIRST_FRAME_RED);

        powerSwitchTimeWindow.activate();
        animator.updateWhileHalted();
        assertThat(paintedRed()).as("The window opened on a halted tick").isEqualTo(SUBSTITUTE_RED);

        powerSwitchTimeWindow.reset();
        animator.updateWhileHalted();
        assertThat(paintedRed()).as("The window closed on a halted tick").isEqualTo(FIRST_FRAME_RED);
    }

    private <A extends AnimatableLevelObject> void register(
        final GameObjectAnimatorSingleTiled<A> animator,
        final A object,
        final LevelObjectType type
    ) {
        when(object.getType()).thenReturn(type);
        when(object.getOffset()).thenReturn(of(0, 0));
        animator.add(object);
        animator.registerLevel(layerGeometry(), new LevelSceneDimensions(1, 1));
    }

    /** Four frames, each one solid colour whose red channel is its index above {@value #FIRST_FRAME_RED}. */
    private AnimationImageResource animationFrames() {
        final AnimationImageResource frames = mock(AnimationImageResource.class);
        when(frames.number()).thenReturn(4);
        when(frames.getFrameRgbData(anyInt())).thenAnswer(invocation ->
            solid(FIRST_FRAME_RED + (int) invocation.getArgument(0)));
        return frames;
    }

    private ImageResource substitute() {
        final ImageResource image = mock(ImageResource.class);
        when(image.getRgbData()).thenReturn(solid(SUBSTITUTE_RED));
        return image;
    }

    private int[] solid(final int red) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, 0xFF000000 | red << 16);
        return pixels;
    }

    private int paintedRed() {
        return layer.get(0) & 0xFF;
    }

    private Geometry layerGeometry() {
        final Image image = new Image(RGBA8, TILE_SPRITE_SIZE, TILE_SPRITE_SIZE, layer, sRGB);
        final MatParamTexture textureParam = new MatParamTexture(
            VarType.Texture2D,
            "ColorMap",
            new Texture2D(image),
            sRGB
        );
        final Material material = mock(Material.class);
        when(material.getTextureParam(anyString())).thenReturn(textureParam);
        final Geometry geometry = mock(Geometry.class);
        when(geometry.getMaterial()).thenReturn(material);
        return geometry;
    }
}
