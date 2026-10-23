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

import static house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions.BODY_WIDTH_PIXELS;
import static house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions.SPRITE_HEIGHT_PIXELS;
import static house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions.SPRITE_WIDTH_PIXELS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins raccoon Mario's asset bundle. Same shape as {@link NormalAnimatorAssetsTest}, and the two differ
 * in one way worth having both: raccoon's hold frames do fill the 24 px canvas, because the tail is in
 * them, while big Mario's do not. Only {@code facing_forward.png} is body-width here.
 */
class RaccoonAnimatorAssetsTest {
    private static final String FRAMES_CONTEXT = "sprites/player/mario/level/raccoon/";

    /**
     * {@code Player_HoldingFrames} for the not-small sizes (dasm prg008.asm:109):
     * {@code $29, $2A, $2B, $2A} — the same four-beat, three-pose cycle big Mario uses, under a
     * different CHR page.
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
        "tail_wagging_control_fall_1.png",
        "walking_1.png",
        "tail_wagging_control_fall_2.png",
        "tail_wagging_control_fly_1.png",
        "tail_wagging_control_fly_2.png",
        "tail_wagging_control_fly_3.png",
        "tail_wagging_attack_1.png",
        "tail_wagging_attack_2.png",
        "tail_wagging_attack_3.png",
        "walking_1.png",
        "tail_wagging_attack_2.png",
        "tail_wagging_attack_3.png",
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
        final RaccoonAnimatorAssets assets = AnimatorAssetsLoader.load(
            RaccoonAnimatorAssets.class,
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
        assertThat(assets.tailFall()).as("tail-wag fall cycle").hasSize(3);
        assertThat(assets.tailFly()).as("tail-wag fly cycle").hasSize(3);
        assertThat(assets.tailAttack()).as("grounded tail attack").hasSize(3);
        assertThat(assets.tailAttackInAir()).as("airborne tail attack").hasSize(3);
        assertThat(assets.walk()).as("walk cycle").hasSize(3);
        assertThat(assets.run()).as("run cycle").hasSize(3);
    }

    @Test
    @DisplayName("The hold cycle is four beats over three poses, with the second pose repeated")
    void theHoldCycleRepeatsItsSecondPose() {
        // Shipped as a real asset reference rather than left to the animator, so a plain linear walk of
        // the array reproduces the ROM's asymmetric $29 -> $2A -> $2B -> $2A.
        assertThat(HOLD_FILENAMES).as("a four-beat cycle").hasSize(4);
        assertThat(HOLD_FILENAMES.get(3))
            .as("the fourth beat replays the second")
            .isEqualTo(HOLD_FILENAMES.get(1));
        assertThat(HOLD_FILENAMES.stream().distinct().toList())
            .as("over three distinct poses")
            .hasSize(3);
    }

    @Test
    @DisplayName("The hold and kick frames fill the canvas; only the turn-around pose is body-width")
    void onlyTheTurnAroundPoseIsBodyWidth() throws IOException {
        // The tail is part of the hold poses, so they need the same 24 px the walk frames do, and the
        // kick needs it for the extended foot. The camera-facing turn-around pose has neither, which is
        // why it is 16 px and must not be bound to RaccoonDimensions.QUAD_WIDTH.
        final List<String> fullCanvasFilenames = new ArrayList<>(HOLD_FILENAMES.stream().distinct().toList());
        fullCanvasFilenames.add(KICK_FILENAME);

        for (final String filename : fullCanvasFilenames) {
            final BufferedImage frame = readFrame(filename);

            assertThat(frame.getWidth()).as("%s fills the canvas", filename).isEqualTo(CANVAS_WIDTH);
            assertThat(frame.getHeight()).as("%s is two tiles tall", filename).isEqualTo(CANVAS_HEIGHT);
        }

        final BufferedImage faceForward = readFrame(FACE_FORWARD_FILENAME);

        assertThat(faceForward.getWidth())
            .as("%s is body-width", FACE_FORWARD_FILENAME)
            .isEqualTo(BODY_WIDTH);
        assertThat(faceForward.getHeight())
            .as("%s is two tiles tall", FACE_FORWARD_FILENAME)
            .isEqualTo(CANVAS_HEIGHT);
    }

    @Test
    @DisplayName("Every frame but the kick is left-anchored, as the tail-offset flip assumes")
    void everyFrameButTheKickIsLeftAnchored() throws IOException {
        // RaccoonDimensions.TAIL_OFFSET is the trailing slack - the tail overflow - and the flip that
        // keeps the body over the collision box when facing right is derived from it. That only holds
        // while the body is flush to the left edge.
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
        // Pinned, not corrected - see NormalAnimatorAssetsTest for the reasoning. The art faces left and
        // the kicking foot extends in the direction of travel, so the overhang falls on the leading side,
        // which neither TAIL_OFFSET nor the texture-derived right padding can express.
        final BufferedImage frame = readFrame(KICK_FILENAME);

        assertThat(emptyColumnsOnTheRight(frame))
            .as("%s has no trailing slack for the tail offset to consume", KICK_FILENAME)
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
