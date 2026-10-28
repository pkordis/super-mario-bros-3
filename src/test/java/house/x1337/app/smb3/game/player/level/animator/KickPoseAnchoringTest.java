package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions;
import house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions;
import house.x1337.app.smb3.model.game.player.level.dimension.ShrunkDimensions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Why the kick pose needs a <b>leading</b> offset, and that the one it is given lands the body over the
 * collision box in both facings.
 *
 * <p>Every other frame in the bundle is authored with the body flush to the canvas's left edge and
 * whatever slack there is on the right, which is the assumption
 * {@link LevelScenePlayerAnimatorSpecifications#getRightPadding()} encodes. {@code kicking.png} inverts
 * it: the kicking foot extends in the direction of travel, so the overhang falls on the <em>leading</em>
 * side — 3 transparent columns for {@code normal}, 2 for {@code raccoon}, and none on the right
 * ({@code PF_KICK_BIG} pulls a third top-row sprite down to be the foot, dasm prg029.asm:688).
 *
 * <p>So there is nothing for {@code rightPadding} to consume and the <em>unmirrored</em> draw is the one
 * that has to move. The arithmetic below is {@code LevelScenePlayerAnimator#rebuildWithTexture}'s, stated
 * independently of it: a jME quad cannot be built in a unit test, but the two translations it chooses can
 * be, and the thing worth pinning is that both of them put the torso in the same place.
 */
class KickPoseAnchoringTest {
    private static final String FRAMES_ROOT = "sprites/player/mario/level/%s/";
    private static final String KICK_FILENAME = "kicking.png";
    private static final float BODY_WIDTH = TILE_SPRITE_SIZE;

    @ParameterizedTest
    @CsvSource({
        // mode,  canvasWidth, canvasHeight, leadingTransparentColumns
        "normal,  24,          32,           3",
        "raccoon, 24,          32,           2",
        "shrunk,  16,          16,           0"
    })
    @DisplayName("The kick frame fills its mode's canvas and keeps its overhang on the leading side")
    void theKickFrameOverhangsOnTheLeadingSide(
        final String mode,
        final int canvasWidth,
        final int canvasHeight,
        final int leadingTransparentColumns
    ) throws IOException {
        // Written out rather than measured-and-compared, so re-cutting the art fails here rather than in
        // the running game. The right edge having NO slack is the half that matters most: it is what
        // makes rightPadding the wrong tool and a leading offset the right one.
        // Prepare
        final BufferedImage frame = readKickFrame(mode);

        // Execute & Verify
        assertThat(frame.getWidth()).as("%s kick width", mode).isEqualTo(canvasWidth);
        assertThat(frame.getHeight()).as("%s kick height", mode).isEqualTo(canvasHeight);
        assertThat(emptyColumnsOnTheLeft(frame))
            .as("%s kick leaves %d columns clear on the leading side", mode, leadingTransparentColumns)
            .isEqualTo(leadingTransparentColumns);
        assertThat(emptyColumnsOnTheRight(frame))
            .as("%s kick has no trailing slack for rightPadding to consume", mode)
            .isZero();
    }

    @ParameterizedTest
    @CsvSource({"normal, 8", "raccoon, 8", "shrunk, 0"})
    @DisplayName("The leading offset is the body box's own inset, which the right-flush art fixes")
    void theLeadingOffsetMatchesTheBodyInset(
        final String mode,
        final int expectedOffsetPixels
    ) throws IOException {
        // The frame has no trailing slack at all, so its 16 px body box can only be the RIGHT-hand
        // {@code width - 16} columns of the canvas; the offset that lands that box over the collision box
        // is therefore the canvas less one body width. Derived from the box rather than from the leftmost
        // opaque pixel on purpose: the torso does not fill its own box edge to edge (it starts 2 px in
        // for normal, 1 px for raccoon), and the foot reaches outside it, so neither silhouette edge is
        // the alignment reference.
        // Prepare
        final BufferedImage frame = readKickFrame(mode);
        final int bodyBoxLeft = (int) (frame.getWidth() - BODY_WIDTH);

        // Execute & Verify
        assertThat(bodyBoxLeft)
            .as("%s body box starts %d px in", mode, expectedOffsetPixels)
            .isEqualTo(expectedOffsetPixels);
        assertThat(kickLeadingOffsetFor(mode))
            .as("%s declares exactly that as its kick leading offset", mode)
            .isEqualTo(expectedOffsetPixels * PIXELS_TO_GAME_UNITS);
        assertThat(leftmostOpaqueColumnOf(frame, 0, frame.getHeight() / 2 - 1))
            .as("%s draws the torso inside that box", mode)
            .isGreaterThanOrEqualTo(bodyBoxLeft);
    }

    @ParameterizedTest
    @CsvSource({"normal, 3", "raccoon, 2"})
    @DisplayName("The foot reaches outside the body box, which is what the overhang is")
    void theFootOverhangsTheBodyBox(
        final String mode,
        final int expectedFootColumn
    ) throws IOException {
        // Measured in the lower half, where the kicking leg is. If this ever stopped overhanging, the
        // leading offset would be unnecessary rather than merely wrong - so it is worth stating that the
        // overhang is real and how far it goes.
        // Prepare
        final BufferedImage frame = readKickFrame(mode);

        // Execute
        final int footColumn = leftmostOpaqueColumnOf(frame, frame.getHeight() / 2, frame.getHeight() - 1);

        // Verify
        assertThat(footColumn)
            .as("%s foot reaches column %d", mode, expectedFootColumn)
            .isEqualTo(expectedFootColumn);
        assertThat(footColumn)
            .as("%s foot hangs outside the body box", mode)
            .isLessThan((int) (frame.getWidth() - BODY_WIDTH));
    }

    @Test
    @DisplayName("Both facings draw the kick's body box over the collision box, to the pixel")
    void bothFacingsPutTheBodyOverTheCollisionBox() throws IOException {
        // rebuildWithTexture's two branches, as arithmetic. Unmirrored it translates by -leadingOffset;
        // mirrored it scales by -1 about the quad's bottom-left origin and translates by
        // quadWidth - rightPadding, where the kick's rightPadding is zero.
        //
        // This is the off-by-one-quad-width the method's Javadoc warns about, in the one place the two
        // paddings are not the same number. Driving the mirrored case off the mode's ordinary
        // RIGHT_PADDING would subtract 8 px it has no slack for and land the body short.
        for (final String mode : new String[]{"normal", "raccoon", "shrunk"}) {
            // Prepare
            final BufferedImage frame = readKickFrame(mode);
            final float quadWidth = frame.getWidth() * PIXELS_TO_GAME_UNITS;
            final float leadingOffset = kickLeadingOffsetFor(mode);
            final float bodyBoxLeft = (frame.getWidth() - BODY_WIDTH) * PIXELS_TO_GAME_UNITS;
            final float bodyWidth = BODY_WIDTH * PIXELS_TO_GAME_UNITS;

            // Execute - where the body box's left edge ends up, relative to the player's own X
            final float facingLeft = bodyBoxLeft - leadingOffset;
            final float facingRight = quadWidth - (bodyBoxLeft + bodyWidth);

            // Verify
            assertThat(facingLeft)
                .as("%s facing left puts the body flush against the collision box", mode)
                .isEqualTo(0f);
            assertThat(facingRight)
                .as("%s facing right lands it in the same place", mode)
                .isEqualTo(0f);
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * The offset each mode declares, read from its dimension constants rather than from an animator, so
     * the test needs no jME and no asset manager.
     */
    private static float kickLeadingOffsetFor(final String mode) {
        return switch (mode) {
            case "normal" -> NormalDimensions.KICK_LEADING_OFFSET;
            case "raccoon" -> RaccoonDimensions.KICK_LEADING_OFFSET;
            case "shrunk" -> ShrunkDimensions.KICK_LEADING_OFFSET;
            default -> throw new AssertionError("Unknown mode " + mode);
        };
    }

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
    private static int leftmostOpaqueColumnOf(
        final BufferedImage image,
        final int fromRow,
        final int toRow
    ) {
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

    private BufferedImage readKickFrame(final String mode) throws IOException {
        final String path = FRAMES_ROOT.formatted(mode) + KICK_FILENAME;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
