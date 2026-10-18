package house.x1337.app.smb3.model.game.enemy.asset;

import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static org.assertj.core.api.Assertions.assertThat;

class KoopaTroopaAnimatorAssetsTest {
    private static final String FRAMES_CONTEXT_TEMPLATE = "sprites/enemy/koopa/troopa/%s/";
    private static final int WALK_FRAME_COUNT = 2;

    /** Tile wide, two tiles tall - the 16x32 block of hardware sprites Troopa_Draw fills. */
    private static final int FRAME_WIDTH = TILE_SPRITE_SIZE;
    private static final int FRAME_HEIGHT = TILE_SPRITE_SIZE * 2;

    /** 5 px of head tip + 16 px of body + 6 px of feet, measured out of chr079. */
    private static final int ART_HEIGHT = 27;

    /**
     * Colour index 3 of the sprite palette the object attribute table picks: {@code $16} for the red
     * troopa's {@code OA1_PAL1}, {@code $2A} for the green one's {@code OA1_PAL2} (dasm
     * prg004.asm:154-155, {@code PalSet_Plains}). RGB read off the project's own emulator capture.
     */
    private static final int SHELL_RED = 0xFFB72913;
    private static final int SHELL_GREEN = 0xFF5CE430;

    @Test
    @DisplayName("Every mode's assets.json names the walk cycle in Objects_Frame order")
    void everyModeDescribesItsWalkCycle() {
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            // Prepare
            final List<String> requestedFilenames = new ArrayList<>();

            // Execute
            final KoopaTroopaAnimatorAssets assets = AnimatorAssetsLoader.load(
                KoopaTroopaAnimatorAssets.class,
                framesContext(mode),
                filename -> {
                    requestedFilenames.add(filename);
                    return null;
                }
            );

            // Verify
            assertThat(requestedFilenames)
                .as("%s resolves every name in its descriptor through the sprite loader", mode)
                .containsExactly("koopa_troopa_walk_1.png", "koopa_troopa_walk_2.png");
            assertThat(assets.walk())
                .as("%s populates the record's component from the matching JSON key", mode)
                .hasSize(WALK_FRAME_COUNT);
        }
    }

    @Test
    @DisplayName("Only the left-facing frames are shipped, because the mirror is a draw-time transform")
    void shipsLeftFacingArtOnly() {
        // The ROM keeps one set of patterns and flips them through SPR_HFLIP (Troopa_Draw, dasm
        // prg004.asm:4349). Mirrored art files would be a second copy of something the renderer can
        // produce with a scale sign, so their absence is the point rather than an omission.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            assertThat(walkFrameFilenames(mode))
                .as("%s ships no _left/_right pairs", mode)
                .noneMatch(filename -> filename.contains("_left") || filename.contains("_right"));
        }
    }

    @Test
    @DisplayName("Every walking frame is a 16x32 canvas, so the head can reach above the tracked shell")
    void framesAreOneTileWideAndTwoTall() throws IOException {
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (final String filename : walkFrameFilenames(mode)) {
                final BufferedImage frame = readFrame(mode, filename);

                assertThat(frame.getWidth())
                    .as("%s/%s is one tile wide", mode, filename)
                    .isEqualTo(FRAME_WIDTH);
                assertThat(frame.getHeight())
                    .as("%s/%s is two tiles tall", mode, filename)
                    .isEqualTo(FRAME_HEIGHT);
            }
        }
    }

    @Test
    @DisplayName("The art is bottom-anchored 27 px tall, so the feet land on the quad's bottom edge")
    void artIsBottomAnchored() throws IOException {
        // The quad's bottom edge is the feet line at Objects_Y + 16, and nothing repositions it per
        // frame beyond the one-pixel walk bob. The silhouette therefore has to sit against the bottom
        // of the canvas; art floating in the middle would leave the troopa hovering.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (final String filename : walkFrameFilenames(mode)) {
                final BufferedImage frame = readFrame(mode, filename);

                assertThat(opaquePixelsInRow(frame, FRAME_HEIGHT - 1))
                    .as("%s/%s carries art on its bottom row", mode, filename)
                    .isPositive();
                assertThat(opaquePixelsInRow(frame, FRAME_HEIGHT - ART_HEIGHT))
                    .as("%s/%s starts its 27 px of art exactly where the head tip begins", mode, filename)
                    .isPositive();
                assertThat(opaquePixelsInRow(frame, FRAME_HEIGHT - ART_HEIGHT - 1))
                    .as("%s/%s has nothing above that", mode, filename)
                    .isZero();
                assertThat(frame.getRGB(0, 0) >>> 24)
                    .as("%s/%s kept its transparency", mode, filename)
                    .isZero();
            }
        }
    }

    @Test
    @DisplayName("The art faces left: the shell sits on the trailing half and the head on the leading one")
    void artFacesLeft() throws IOException {
        // Objects_Y tracks the shell, and the head is drawn from the leading body half (Troopa_Draw
        // places the head tip over the sprite the facing bit selects). Left-facing therefore puts the
        // taller silhouette - head and neck - in the left half of the canvas.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (final String filename : walkFrameFilenames(mode)) {
                final BufferedImage frame = readFrame(mode, filename);
                final int headRow = FRAME_HEIGHT - ART_HEIGHT;

                assertThat(opaquePixelsInColumnRange(frame, headRow, 0, FRAME_WIDTH / 2 - 1))
                    .as("%s/%s draws the head tip in its left half", mode, filename)
                    .isPositive();
                assertThat(opaquePixelsInColumnRange(frame, headRow, FRAME_WIDTH / 2, FRAME_WIDTH - 1))
                    .as("%s/%s leaves the right half empty at the head tip's row", mode, filename)
                    .isZero();
            }
        }
    }

    @Test
    @DisplayName("Green and red are the same patterns under a different palette, so only the shell differs")
    void theTwoColoursDifferOnlyInTheShell() throws IOException {
        // The ROM ships ONE set of troopa patterns; the colour comes from the object attribute table
        // choosing OA1_PAL1 or OA1_PAL2, and those two rows of PalSet_Plains agree on everything but
        // colour index 3. The head and feet are forced to SPR_PAL3 by Troopa_Draw either way, so a
        // pixel that is not shell must be byte-identical between the folders.
        for (final String filename : walkFrameFilenames(RED)) {
            final BufferedImage red = readFrame(RED, filename);
            final BufferedImage green = readFrame(GREEN, filename);
            int shellPixels = 0;

            for (int row = 0; row < FRAME_HEIGHT; row++) {
                for (int column = 0; column < FRAME_WIDTH; column++) {
                    final int redPixel = red.getRGB(column, row);
                    final int greenPixel = green.getRGB(column, row);
                    if (redPixel == SHELL_RED) {
                        shellPixels++;
                        assertThat(greenPixel)
                            .as("%s shell pixel (%d, %d) is recoloured to $2A", filename, column, row)
                            .isEqualTo(SHELL_GREEN);
                        continue;
                    }
                    assertThat(greenPixel)
                        .as("%s pixel (%d, %d) is off-shell and so unchanged", filename, column, row)
                        .isEqualTo(redPixel);
                }
            }

            assertThat(shellPixels).as("%s has a shell to recolour at all", filename).isPositive();
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** The walk-cycle filenames as the mode's descriptor lists them, so the pixel tests follow the JSON. */
    private List<String> walkFrameFilenames(final KoopaTroopaMode mode) {
        final List<String> filenames = new ArrayList<>();
        AnimatorAssetsLoader.load(
            KoopaTroopaAnimatorAssets.class,
            framesContext(mode),
            filename -> {
                filenames.add(filename);
                return null;
            }
        );
        return filenames;
    }

    private static String framesContext(final KoopaTroopaMode mode) {
        return FRAMES_CONTEXT_TEMPLATE.formatted(mode.getFramesFolder());
    }

    private static int opaquePixelsInRow(final BufferedImage image, final int row) {
        return opaquePixelsInColumnRange(image, row, 0, image.getWidth() - 1);
    }

    private static int opaquePixelsInColumnRange(
        final BufferedImage image,
        final int row,
        final int fromColumn,
        final int toColumn
    ) {
        int opaque = 0;
        for (int column = fromColumn; column <= toColumn; column++) {
            if ((image.getRGB(column, row) >>> 24) != 0) {
                opaque++;
            }
        }
        return opaque;
    }

    private BufferedImage readFrame(
        final KoopaTroopaMode mode,
        final String filename
    ) throws IOException {
        final String path = framesContext(mode) + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
