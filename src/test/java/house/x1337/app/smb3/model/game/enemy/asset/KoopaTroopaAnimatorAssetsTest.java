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

    private static final List<String> WALK_FILENAMES = List.of(
        "koopa_troopa_walk_1.png",
        "koopa_troopa_walk_2.png"
    );

    private static final String SHELL_FILENAME = "koopa_troopa_shell.png";

    private static final List<String> SHELL_FEET_FILENAMES = List.of(
        "koopa_troopa_shell_foot_left.png",
        "koopa_troopa_shell_foot_right.png"
    );

    /** Tile wide, two tiles tall - the 16x32 block of hardware sprites Troopa_Draw fills. */
    private static final int FRAME_WIDTH = TILE_SPRITE_SIZE;
    private static final int FRAME_HEIGHT = TILE_SPRITE_SIZE * 2;

    /** 5 px of head tip + 16 px of body + 6 px of feet, measured out of chr079. */
    private static final int ART_HEIGHT = 27;

    /**
     * The one pixel {@code KoopaTroopaFamilyEnemy#walkFrameBob} drops the odd walk frame by -
     * {@code Troopa_YOffByFrame = {$06, $05}} (dasm prg004.asm:4266) as a draw-time offset.
     *
     * <p>The art meets it halfway, which is the thing to understand before touching either: the odd
     * frame's silhouette is authored one row <b>higher</b> in its canvas, so the drop cancels at the
     * feet and both frames plant them on the quad's bottom edge. What survives is the bob itself - the
     * odd frame reaches a pixel further up. Repad one frame without the other and the feet start sinking,
     * which is why the two tests below measure the <em>drawn</em> band rather than the raw canvas.
     */
    private static final int WALK_FRAME_BOB_PIXELS = 1;

    /**
     * Colour index 3 of the sprite palette the object attribute table picks: {@code $16} for the red
     * troopa's {@code OA1_PAL1}, {@code $2A} for the green one's {@code OA1_PAL2} (dasm
     * prg004.asm:154-155, {@code PalSet_Plains}). RGB read off the project's own emulator capture.
     */
    private static final int SHELL_RED = 0xFFB72913;
    private static final int SHELL_GREEN = 0xFF5CE430;

    @Test
    @DisplayName("Every mode's assets.json names the walk cycle, the shell and both waking frames in order")
    void everyModeDescribesEveryAction() {
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
                .containsExactlyInAnyOrderElementsOf(everyFilename());
            assertThat(assets.walk())
                .as("%s walk cycle", mode)
                .hasSize(WALK_FILENAMES.size());
            assertThat(assets.shellFeet())
                .as("%s waking frames", mode)
                .hasSize(SHELL_FEET_FILENAMES.size());
        }
    }

    @Test
    @DisplayName("Only the left-facing frames are shipped, because the mirror is a draw-time transform")
    void shipsLeftFacingArtOnly() {
        // The ROM keeps one set of walk patterns and flips them through SPR_HFLIP (Troopa_Draw, dasm
        // prg004.asm:4349). Mirrored art files would be a second copy of something the renderer can
        // produce with a scale sign, so their absence is the point rather than an omission. The shell
        // frames carry "left"/"right" in their names for the rock they draw, not for a facing.
        assertThat(WALK_FILENAMES)
            .as("No _left/_right walk pairs")
            .noneMatch(filename -> filename.contains("_left") || filename.contains("_right"));
    }

    @Test
    @DisplayName("Every walking frame is a 16x32 canvas, so the head can reach above the tracked shell")
    void walkFramesAreOneTileWideAndTwoTall() throws IOException {
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (final String filename : WALK_FILENAMES) {
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
    @DisplayName("Every shelled frame is one hardware sprite: a 16x16 shell and two 8x16 feet")
    void shellFramesMatchTheirHardwareSprites() throws IOException {
        // Object_DrawShelled draws one mirrored 16x16 sprite with its top on Objects_Y - none of
        // Troopa_Draw's head tip above it or feet below it, and no Troopa_YOffByFrame. The feet are two
        // further 8x16 sprites in Sprite_RAM+$08 and +$0C.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            final BufferedImage shell = readFrame(mode, SHELL_FILENAME);

            assertThat(shell.getWidth()).as("%s shell is one tile wide", mode).isEqualTo(TILE_SPRITE_SIZE);
            assertThat(shell.getHeight()).as("%s shell is one tile tall", mode).isEqualTo(TILE_SPRITE_SIZE);

            for (final String filename : SHELL_FEET_FILENAMES) {
                final BufferedImage foot = readFrame(mode, filename);

                assertThat(foot.getWidth())
                    .as("%s/%s is half a tile wide", mode, filename)
                    .isEqualTo(TILE_SPRITE_SIZE / 2);
                assertThat(foot.getHeight())
                    .as("%s/%s is a full tile tall", mode, filename)
                    .isEqualTo(TILE_SPRITE_SIZE);
            }
        }
    }

    @Test
    @DisplayName("The feet art sits low in its canvas, which is what lets it share the shell's anchor")
    void theFeetArtSitsLowInItsCanvas() throws IOException {
        // There is no vertical offset applied to a foot quad; it is placed on the shell's own anchor, so
        // ObjWakeUp_FeetYOff's +10 has to be where the pixels sit inside the 8x16 canvas. Art drifting
        // into the upper half would appear partway up the shell instead of under it.
        for (final String filename : SHELL_FEET_FILENAMES) {
            final BufferedImage foot = readFrame(RED, filename);
            final int topmostOpaqueRow = topmostOpaqueRow(foot);

            assertThat(topmostOpaqueRow)
                .as("%s keeps its art clear of the top half of the canvas", filename)
                .isGreaterThanOrEqualTo(TILE_SPRITE_SIZE / 2);
        }
    }

    @Test
    @DisplayName("The two feet are mirrors of each other, as one pattern plus SPR_HFLIP makes them")
    void theFeetAreAMirrorPair() throws IOException {
        // The ROM writes pattern $F9 to both slots and sets the flip bit on the second (dasm
        // prg000.asm:2406-2411); shipping the mirror as art keeps the animator flip-free.
        final BufferedImage left = readFrame(RED, SHELL_FEET_FILENAMES.getFirst());
        final BufferedImage right = readFrame(RED, SHELL_FEET_FILENAMES.getLast());

        for (int row = 0; row < left.getHeight(); row++) {
            for (int column = 0; column < left.getWidth(); column++) {
                assertThat(right.getRGB(column, row))
                    .as("Pixel (%d, %d) of the right foot mirrors the left", column, row)
                    .isEqualTo(left.getRGB(left.getWidth() - 1 - column, row));
            }
        }
    }

    @Test
    @DisplayName("The resting shell is horizontally symmetric, as drawing it from one mirrored half forces")
    void theRestingShellIsSymmetric() throws IOException {
        // Object_ShakeAndDrawMirrored writes two 8x16 sprites from one pattern pair with SPR_HFLIP on
        // the right one, so an asymmetric resting shell could not be what the ROM draws.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            final BufferedImage shell = readFrame(mode, SHELL_FILENAME);

            for (int row = 0; row < TILE_SPRITE_SIZE; row++) {
                for (int column = 0; column < TILE_SPRITE_SIZE; column++) {
                    assertThat(shell.getRGB(column, row))
                        .as("%s shell pixel (%d, %d) mirrors its opposite", mode, column, row)
                        .isEqualTo(shell.getRGB(TILE_SPRITE_SIZE - 1 - column, row));
                }
            }
        }
    }

    @Test
    @DisplayName("The feet are palette-independent, so both colours ship byte-identical foot art")
    void theFeetAreTheSameInBothColours() throws IOException {
        // Object_ShakeAndDrawMirrored forces SPR_PAL3 on the feet with ORA #$03 (dasm prg000.asm:2403),
        // overriding whichever palette the object itself draws in - so there is no green foot.
        for (final String filename : SHELL_FEET_FILENAMES) {
            final BufferedImage red = readFrame(RED, filename);
            final BufferedImage green = readFrame(GREEN, filename);

            for (int row = 0; row < red.getHeight(); row++) {
                for (int column = 0; column < red.getWidth(); column++) {
                    assertThat(green.getRGB(column, row))
                        .as("%s pixel (%d, %d)", filename, column, row)
                        .isEqualTo(red.getRGB(column, row));
                }
            }
        }
    }

    @Test
    @DisplayName("Both walk frames land their feet on the same line once the bob is applied")
    void bothWalkFramesAgreeOnTheFeetLine() throws IOException {
        // This is the invariant the walk bob depends on, so it gets its own test - and it is an
        // invariant about the DRAWN result, not the raw canvas. Troopa_YOffByFrame ({$06, $05}, dasm
        // prg004.asm:4266) draws the odd frame one pixel lower, and the art pre-compensates by sitting
        // one row higher in its own canvas. The two cancel at the feet, which stay planted, and what is
        // left over is the bob: the odd frame's silhouette reaches a pixel further up.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (int frameIndex = 0; frameIndex < WALK_FILENAMES.size(); frameIndex++) {
                final String filename = WALK_FILENAMES.get(frameIndex);
                final BufferedImage frame = readFrame(mode, filename);

                assertThat(bottommostOpaqueRow(frame) + bobPixels(frameIndex))
                    .as("%s/%s draws its feet on the quad's bottom edge", mode, filename)
                    .isEqualTo(FRAME_HEIGHT - 1);
            }
        }
    }

    @Test
    @DisplayName("The art is bottom-anchored within 27 px, so the feet land on the quad's bottom edge")
    void artIsBottomAnchored() throws IOException {
        // The quad's bottom edge is the feet line at Objects_Y + 16, and nothing repositions it per
        // frame beyond the one-pixel walk bob. The silhouette therefore has to sit against the bottom
        // of the canvas; art floating in the middle would leave the troopa hovering. 27 px is the
        // ceiling on it - 5 px of head tip + 16 px of body + 6 px of feet, as Troopa_Draw stacks them.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (int frameIndex = 0; frameIndex < WALK_FILENAMES.size(); frameIndex++) {
                final String filename = WALK_FILENAMES.get(frameIndex);
                final BufferedImage frame = readFrame(mode, filename);

                assertThat(topmostOpaqueRow(frame) + bobPixels(frameIndex))
                    .as("%s/%s reaches no higher than its 27 px of art allows", mode, filename)
                    .isGreaterThanOrEqualTo(FRAME_HEIGHT - ART_HEIGHT);
                assertThat(frame.getRGB(0, 0) >>> 24)
                    .as("%s/%s kept its transparency", mode, filename)
                    .isZero();
            }
        }
    }

    @Test
    @DisplayName("The art faces left: the head tip is on the leading half and nothing is on the trailing one")
    void artFacesLeft() throws IOException {
        // Objects_Y tracks the shell, and the head is drawn from the leading body half (Troopa_Draw
        // places the head tip over the sprite the facing bit selects). Left-facing therefore puts the
        // taller silhouette - head and neck - in the left half of the canvas. Measured at each frame's
        // own topmost row rather than a shared one, because the two frames draw different body patterns
        // and their head tips need not reach equally high.
        for (final KoopaTroopaMode mode : KoopaTroopaMode.values()) {
            for (final String filename : WALK_FILENAMES) {
                final BufferedImage frame = readFrame(mode, filename);
                final int headRow = topmostOpaqueRow(frame);

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
        final List<String> shellColouredFiles = new ArrayList<>(WALK_FILENAMES);
        shellColouredFiles.add(SHELL_FILENAME);

        for (final String filename : shellColouredFiles) {
            final BufferedImage red = readFrame(RED, filename);
            final BufferedImage green = readFrame(GREEN, filename);
            int shellPixels = 0;

            for (int row = 0; row < red.getHeight(); row++) {
                for (int column = 0; column < red.getWidth(); column++) {
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

    private static List<String> everyFilename() {
        final List<String> filenames = new ArrayList<>(WALK_FILENAMES);
        filenames.add(SHELL_FILENAME);
        filenames.addAll(SHELL_FEET_FILENAMES);
        return filenames;
    }

    private static String framesContext(final KoopaTroopaMode mode) {
        return FRAMES_CONTEXT_TEMPLATE.formatted(mode.getFramesFolder());
    }

    private static int opaquePixelsInRow(final BufferedImage image, final int row) {
        return opaquePixelsInColumnRange(image, row, 0, image.getWidth() - 1);
    }

    private static int topmostOpaqueRow(final BufferedImage image) {
        for (int row = 0; row < image.getHeight(); row++) {
            if (opaquePixelsInRow(image, row) > 0) {
                return row;
            }
        }
        throw new AssertionError("The frame is entirely transparent");
    }

    private static int bottommostOpaqueRow(final BufferedImage image) {
        for (int row = image.getHeight() - 1; row >= 0; row--) {
            if (opaquePixelsInRow(image, row) > 0) {
                return row;
            }
        }
        throw new AssertionError("The frame is entirely transparent");
    }

    /** {@code KoopaTroopaFamilyEnemy#walkFrameBob}: the odd frame is drawn one pixel lower. */
    private static int bobPixels(final int frameIndex) {
        return frameIndex % 2 == 0 ? 0 : WALK_FRAME_BOB_PIXELS;
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
