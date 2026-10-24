package house.x1337.app.smb3.game.player.level.animator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions.QUAD_WIDTH;
import static house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions.RIGHT_PADDING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Why the carry frames have to be sized from their own textures rather than from their mode.
 *
 * <p>The frames SMB3-8 shipped are not all the nominal 24x32: big Mario's three hold poses are 16x32,
 * and {@code facing_forward.png} is 16x32 in both the big and raccoon bundles, while raccoon's hold
 * poses fill the canvas and small Mario is 16x16 throughout. Both large modes' {@code QUAD_WIDTH} is a
 * fixed 24px, so binding one of the narrow frames to it stretches it by half again <em>and</em>
 * mis-places the right-facing flip, because a 16px frame has no transparent right margin for
 * {@code rightPadding} to consume.
 *
 * <p>Every expectation below is written out rather than derived from the file, so that re-cutting any of
 * the art fails here instead of in the running game. The sizes are stated independently of
 * {@link LevelScenePlayerAnimator#rightPaddingFor}, which is the thing under test.
 */
class CarryFrameQuadSizingTest {
    private static final String FRAMES_ROOT = "sprites/player/mario/level/%s/";
    private final LevelScenePlayerAnimator<?> animator = new EmptyAnimator(null, null);

    @ParameterizedTest
    @CsvSource({
        // mode,    frame,                 width, height, expectedPaddingPixels
        "normal,    holding_1.png,         16,    32,     0",
        "normal,    holding_2.png,         16,    32,     0",
        "normal,    holding_3.png,         16,    32,     0",
        "normal,    facing_forward.png,    16,    32,     0",
        "raccoon,   holding_1.png,         24,    32,     8",
        "raccoon,   holding_2.png,         24,    32,     8",
        "raccoon,   holding_3.png,         24,    32,     8",
        "raccoon,   facing_forward.png,    16,    32,     0",
        "shrunk,    holding_1.png,         16,    16,     0",
        "shrunk,    holding_2.png,         16,    16,     0",
        "shrunk,    facing_forward.png,    16,    16,     0"
    })
    @DisplayName("Each carry frame is its stated size, and its flip shift is whatever it has past the body")
    void eachCarryFrameIsDrawnAtItsOwnSize(
        final String mode,
        final String filename,
        final int expectedWidth,
        final int expectedHeight,
        final int expectedPaddingPixels
    ) throws IOException {
        // Prepare
        final BufferedImage frame = readFrame(mode, filename);

        // Execute
        final float rightPadding = animator.rightPaddingFor(frame.getWidth());

        // Verify
        assertThat(frame.getWidth()).as("%s/%s width", mode, filename).isEqualTo(expectedWidth);
        assertThat(frame.getHeight()).as("%s/%s height", mode, filename).isEqualTo(expectedHeight);
        assertThat(rightPadding)
            .as("%s/%s shifts by %d px when flipped to face right", mode, filename, expectedPaddingPixels)
            .isEqualTo(expectedPaddingPixels * PIXELS_TO_GAME_UNITS);
    }

    @ParameterizedTest
    @CsvSource({"normal, holding_1.png", "normal, facing_forward.png", "raccoon, facing_forward.png"})
    @DisplayName("The narrow frames really would be stretched and mis-flipped by their mode's quad")
    void theModeQuadWouldStretchTheNarrowFrames(
        final String mode,
        final String filename
    ) throws IOException {
        // The failure mode this guards is silent: a 16px frame on a 24px quad is still drawn, just 1.5x
        // too wide and offset when flipped. Stated as the comparison rather than as two sizes, so it
        // fails if the art is ever re-cut to fill the canvas and the texture-derived path becomes moot.
        // Prepare & Execute
        final float textureQuadWidth = readFrame(mode, filename).getWidth() * PIXELS_TO_GAME_UNITS;

        // Verify
        assertThat(QUAD_WIDTH / textureQuadWidth)
            .as("%s/%s would be stretched by half again", mode, filename)
            .isEqualTo(1.5f);
        assertThat(animator.rightPaddingFor(readFrame(mode, filename).getWidth()))
            .as("and the mode's own padding is not the one it needs")
            .isNotEqualTo(RIGHT_PADDING);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private BufferedImage readFrame(final String mode, final String filename) throws IOException {
        final String path = FRAMES_ROOT.formatted(mode) + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
