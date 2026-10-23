package house.x1337.app.smb3.model.game.player.level.asset;

import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions.BODY_WIDTH_PIXELS;
import static house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions.SPRITE_HEIGHT_PIXELS;
import static house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions.SPRITE_WIDTH_PIXELS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins big Mario's asset bundle: the filenames its descriptor resolves, in order, and the pixel
 * geometry the animator has to respect when it builds a quad for one of them.
 *
 * <p>The descriptor test next door proves the JSON keys and the record components agree. It cannot
 * prove that {@code hold} still names three distinct poses with the second repeated, or that
 * {@code kicking.png} is still the odd one out on width and anchoring — which is what this covers.
 */
class NormalAnimatorAssetsTest {
    private static final String FRAMES_CONTEXT = "sprites/player/mario/level/normal/";

    /**
     * {@code Player_HoldingFrames} for the not-small sizes (dasm prg008.asm:109):
     * {@code $29, $2A, $2B, $2A}. Three poses on a four-beat cycle, so the second file appears twice
     * and the animator can index it straight off the walk-frame counter with no special case.
     */
    private static final List<String> HOLD_FILENAMES = List.of(
        "holding_1.png",
        "holding_2.png",
        "holding_3.png",
        "holding_2.png"
    );

    private static final String KICK_FILENAME = "kicking.png";
    private static final String FACE_FORWARD_FILENAME = "facing_forward.png";

    /** Every name the descriptor resolves, in the order the loader reads them. */
    private static final List<String> EXPECTED_FILENAMES = List.of(
        "still.png",
        "rapid_turn.png",
        "ducking.png",
        FACE_FORWARD_FILENAME,
        "holding_1.png",
        "holding_2.png",
        "holding_3.png",
        "holding_2.png",
        "jumping.png",
        KICK_FILENAME,
        "flying.png",
        "walking_1.png",
        "walking_2.png",
        "still.png",
        "running_1.png",
        "running_2.png",
        "running_3.png"
    );

    private static final int CANVAS_WIDTH = (int) SPRITE_WIDTH_PIXELS;
    private static final int CANVAS_HEIGHT = (int) SPRITE_HEIGHT_PIXELS;
    private static final int BODY_WIDTH = (int) BODY_WIDTH_PIXELS;

    @Test
    @DisplayName("The descriptor resolves every frame of every action, in order")
    void theDescriptorResolvesEveryFrame() {
        // Prepare
        final List<String> requestedFilenames = new ArrayList<>();

        // Execute
        final NormalAnimatorAssets assets = AnimatorAssetsLoader.load(
            NormalAnimatorAssets.class,
            FRAMES_CONTEXT,
            filename -> {
                requestedFilenames.add(filename);
                return null;
            }
        );

        // Verify
        assertThat(requestedFilenames)
            .as("every name in the descriptor goes through the sprite loader")
            .containsExactlyElementsOf(EXPECTED_FILENAMES);
        assertThat(assets.hold()).as("hold cycle").hasSize(HOLD_FILENAMES.size());
        assertThat(assets.walk()).as("walk cycle").hasSize(3);
        assertThat(assets.run()).as("run cycle").hasSize(3);
    }

    @Test
    @DisplayName("The hold cycle is four beats over three poses, with the second pose repeated")
    void theHoldCycleRepeatsItsSecondPose() {
        // The repeat is shipped as a real asset reference rather than left to the animator, so a plain
        // linear walk of the array reproduces the ROM's asymmetric $29 -> $2A -> $2B -> $2A. Collapsing
        // it to three entries would silently turn the cycle symmetric.
        assertThat(HOLD_FILENAMES).as("a four-beat cycle").hasSize(4);
        assertThat(HOLD_FILENAMES.get(3))
            .as("the fourth beat replays the second")
            .isEqualTo(HOLD_FILENAMES.get(1));
        assertThat(HOLD_FILENAMES.stream().distinct().toList())
            .as("over three distinct poses")
            .hasSize(3);
    }

    @Test
    @DisplayName("The hold and turn-around frames are body-width, not the bundle's 24 px canvas")
    void theHoldAndTurnAroundFramesAreBodyWidth() throws IOException {
        // These poses have nothing overhanging the body, so they are authored at the body's own 16 px
        // rather than padded out to the 24 px the walk and run frames use. Binding one to
        // NormalDimensions.QUAD_WIDTH would stretch it by half again, which is why the animator has to
        // take its quad width from the texture (or from a per-frame constant) and not from the mode.
        final List<String> bodyWidthFilenames = new ArrayList<>(HOLD_FILENAMES.stream().distinct().toList());
        bodyWidthFilenames.add(FACE_FORWARD_FILENAME);

        for (final String filename : bodyWidthFilenames) {
            final BufferedImage frame = readFrame(filename);

            assertThat(frame.getWidth()).as("%s is body-width", filename).isEqualTo(BODY_WIDTH);
            assertThat(frame.getHeight()).as("%s is two tiles tall", filename).isEqualTo(CANVAS_HEIGHT);
        }
    }

    @Test
    @DisplayName("The kick frame needs the full canvas, because the foot reaches past the body")
    void theKickFrameUsesTheFullCanvas() throws IOException {
        // PF_KICK_BIG is the one big-Mario pose wider than the body: prg029.asm:688 pulls a third
        // top-row sprite down into the bottom row to become the extended foot.
        final BufferedImage frame = readFrame(KICK_FILENAME);

        assertThat(frame.getWidth()).as("%s fills the canvas", KICK_FILENAME).isEqualTo(CANVAS_WIDTH);
        assertThat(frame.getHeight()).as("%s is two tiles tall", KICK_FILENAME).isEqualTo(CANVAS_HEIGHT);
    }

    @Test
    @DisplayName("Every frame but the kick is left-anchored, as the right-padding flip assumes")
    void everyFrameButTheKickIsLeftAnchored() throws IOException {
        // LevelScenePlayerAnimatorSpecifications#rightPadding is documented as "empty space to the
        // RIGHT of the body within the sprite canvas", and the flip that keeps the body over the
        // collision box when facing right is derived from it. That only holds while the body is flush
        // to the left edge, so the invariant is worth stating for the frames this ticket added.
        final List<String> leftAnchored = new ArrayList<>(HOLD_FILENAMES.stream().distinct().toList());
        leftAnchored.add(FACE_FORWARD_FILENAME);

        for (final String filename : leftAnchored) {
            assertThat(emptyColumnsOnTheLeft(readFrame(filename)))
                .as("%s puts the body flush against the left edge", filename)
                .isZero();
        }
    }

    @Test
    @DisplayName("The kick frame inverts that: the body is right-flush and the foot overhangs leftward")
    void theKickFrameIsRightAnchored() throws IOException {
        // Deliberately pinned rather than corrected. The art is authored facing left and the kicking
        // foot extends in the direction of travel, so the overhang falls on the LEADING side - the only
        // frame in the bundle where it does. Nothing in the renderer models a leading overhang yet:
        // rightPadding assumes the slack is on the trailing side, so drawing this frame through the
        // ordinary path offsets the body by the overhang. Whoever wires the kick pose up has to shift
        // the quad by this margin; this test fails if the art is ever re-anchored and the shift becomes
        // wrong in the other direction.
        final BufferedImage frame = readFrame(KICK_FILENAME);

        assertThat(emptyColumnsOnTheRight(frame))
            .as("%s has no trailing slack for rightPadding to consume", KICK_FILENAME)
            .isZero();
        assertThat(leftmostOpaqueColumnOf(frame, 0, CANVAS_HEIGHT / 2 - 1))
            .as("%s draws the torso in the canvas's right half", KICK_FILENAME)
            .isGreaterThanOrEqualTo(CANVAS_WIDTH - BODY_WIDTH);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static int emptyColumnsOnTheLeft(final BufferedImage image) {
        for (int column = 0; column < image.getWidth(); column++) {
            if (columnHasOpaquePixels(image, column)) {
                return column;
            }
        }
        throw new AssertionError("The frame is entirely transparent");
    }

    private static int emptyColumnsOnTheRight(final BufferedImage image) {
        for (int column = image.getWidth() - 1; column >= 0; column--) {
            if (columnHasOpaquePixels(image, column)) {
                return image.getWidth() - 1 - column;
            }
        }
        throw new AssertionError("The frame is entirely transparent");
    }

    /** The leftmost opaque column within a band of rows, so the torso can be measured without the foot. */
    private static int leftmostOpaqueColumnOf(final BufferedImage image, final int fromRow, final int toRow) {
        for (int column = 0; column < image.getWidth(); column++) {
            for (int row = fromRow; row <= toRow; row++) {
                if ((image.getRGB(column, row) >>> 24) != 0) {
                    return column;
                }
            }
        }
        throw new AssertionError("The row band is entirely transparent");
    }

    private static boolean columnHasOpaquePixels(final BufferedImage image, final int column) {
        for (int row = 0; row < image.getHeight(); row++) {
            if ((image.getRGB(column, row) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private BufferedImage readFrame(final String filename) throws IOException {
        final String path = FRAMES_CONTEXT + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
