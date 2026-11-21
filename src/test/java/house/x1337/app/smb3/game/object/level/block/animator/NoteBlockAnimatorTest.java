package house.x1337.app.smb3.game.object.level.block.animator;

import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.shader.VarType;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.converter.AnimationImageResourceConverter;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;

import static com.jme3.texture.Image.Format.RGBA8;
import static com.jme3.texture.image.ColorSpace.sRGB;
import static com.jme3.util.BufferUtils.createByteBuffer;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.NOTE_BLOCK_NO_REWARD;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The note block animates through three distinct images, A-B-C-B at 8 ticks each, and stands still on the first while
 * a P-Switch is active.
 */
class NoteBlockAnimatorTest {
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final ByteBuffer layer = createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private NoteBlockAnimator animator;
    private AnimationImageResource frames;

    @BeforeEach
    void prepare() throws NoSuchFieldException {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PowerSwitchTimeWindow.class))
            .thenReturn(powerSwitchTimeWindow);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ImageResource.class))
            .thenAnswer(invocation -> new ImageResource());
        final String framesPath = NoteBlockAnimator.class
            .getDeclaredField("animationFrames")
            .getAnnotation(Value.class)
            .value();
        frames = new AnimationImageResourceConverter().convert(framesPath);
        animator = new NoteBlockAnimator();
        ReflectionTestUtils.setField(animator, "animationFrames", frames);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Four frame files hold three distinct images, the last a copy of the second")
    void theFramesPlayTheMiddleImageTwice() {
        // Execute
        final int number = frames.number();

        // Verify
        assertThat(number).isEqualTo(4);
        assertThat(frames.getFrameRgbData(3)).as("Fourth frame").isEqualTo(frames.getFrameRgbData(1));
        assertThat(frames.getFrameRgbData(0)).isNotEqualTo(frames.getFrameRgbData(1));
        assertThat(frames.getFrameRgbData(1)).isNotEqualTo(frames.getFrameRgbData(2));
        assertThat(frames.getFrameRgbData(0)).isNotEqualTo(frames.getFrameRgbData(2));
        assertThat(frames.getDimensionsPixels().width()).isEqualTo(TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("The frames advance every 8 ticks and loop")
    void theFramesAdvanceEveryEightTicks() {
        // Prepare
        register();

        // Execute & Verify
        assertThat(painted()).as("Frame 0 once registered").isEqualTo(frames.getFrameRgbData(0));
        for (int frame = 1; frame <= 4; frame++) {
            tick(7);
            assertThat(painted()).as("Unchanged a tick before frame %d", frame % 4)
                .isEqualTo(frames.getFrameRgbData(frame - 1));
            tick(1);
            assertThat(painted()).as("Frame %d", frame % 4).isEqualTo(frames.getFrameRgbData(frame % 4));
        }
    }

    @Test
    @DisplayName("While a P-Switch is active it stands still on the first frame, and animates again after")
    void aPowerSwitchFreezesItOnTheFirstFrame() {
        // Prepare
        register();
        tick(8);

        // Execute & Verify
        powerSwitchTimeWindow.activate();
        tick(1);
        assertThat(painted()).as("At once").isEqualTo(frames.getFrameRgbData(0));
        tick(32);
        assertThat(painted()).as("A whole cycle later").isEqualTo(frames.getFrameRgbData(0));

        powerSwitchTimeWindow.reset();
        tick(1);
        assertThat(painted()).as("The window closed").isNotEqualTo(frames.getFrameRgbData(0));
    }

    private void register() {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(NOTE_BLOCK_NO_REWARD);
        when(block.getOffset()).thenReturn(of(0, 0));
        animator.add(block);
        animator.registerLevel(layerGeometry(), new LevelSceneDimensions(1, 1));
    }

    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            animator.update();
        }
    }

    /** The tile as painted, as ARGB pixels from the top row down. */
    private int[] painted() {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        for (int row = 0; row < TILE_SPRITE_SIZE; row++) {
            final int imageRow = TILE_SPRITE_SIZE - 1 - row;
            for (int column = 0; column < TILE_SPRITE_SIZE; column++) {
                final int index = (imageRow * TILE_SPRITE_SIZE + column) * 4;
                pixels[row * TILE_SPRITE_SIZE + column] = (layer.get(index + 3) & 0xFF) << 24
                    | (layer.get(index) & 0xFF) << 16
                    | (layer.get(index + 1) & 0xFF) << 8
                    | layer.get(index + 2) & 0xFF;
            }
        }
        return pixels;
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
