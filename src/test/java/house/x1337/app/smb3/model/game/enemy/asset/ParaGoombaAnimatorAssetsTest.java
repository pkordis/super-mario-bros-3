package house.x1337.app.smb3.model.game.enemy.asset;

import house.x1337.app.smb3.enumeration.enemy.GoombaMode;
import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Para-Goomba's asset bundle: three descriptor keys, one per action, and six files whose
 * dimensions match the hardware sprites they back - a 16x16 body frame (two 8x16 sprites side by side)
 * and an 8x16 wing.
 */
class ParaGoombaAnimatorAssetsTest {
    private static final String FRAMES_CONTEXT_TEMPLATE = "sprites/enemy/paragoomba/%s/";
    private static final int WALK_FRAME_COUNT = 2;
    private static final int WING_WIDTH = TILE_SPRITE_SIZE / 2;

    private static final List<String> EXPECTED_FILENAMES = List.of(
        "paragoomba_left.png",
        "paragoomba_right.png",
        "paragoomba_wing_up_left.png",
        "paragoomba_wing_up_right.png",
        "paragoomba_wing_down_left.png",
        "paragoomba_wing_down_right.png"
    );

    @Test
    @DisplayName("Every mode's assets.json names the body cycle and both wing beats, in that order")
    void everyModeDescribesAllThreeActions() {
        for (final GoombaMode mode : GoombaMode.values()) {
            // Prepare
            final List<String> requestedFilenames = new ArrayList<>();

            // Execute
            final ParaGoombaAnimatorAssets assets = AnimatorAssetsLoader.load(
                ParaGoombaAnimatorAssets.class,
                framesContext(mode),
                filename -> {
                    requestedFilenames.add(filename);
                    return null;
                }
            );

            // Verify
            assertThat(requestedFilenames)
                .as("%s resolves every name in its descriptor through the sprite loader", mode)
                .containsExactlyElementsOf(EXPECTED_FILENAMES);
            assertThat(assets.walk()).as("%s walk cycle", mode).hasSize(WALK_FRAME_COUNT);
            assertThat(assets.wingUp()).as("%s raised wing pair", mode).hasSize(2);
            assertThat(assets.wingDown()).as("%s lowered wing pair", mode).hasSize(2);
        }
    }

    @Test
    @DisplayName("Both body frames are a single 16x16 tile, as the Goomba's are")
    void bodyFramesAreOneTileEach() throws IOException {
        // Prepare & Execute & Verify
        for (final GoombaMode mode : GoombaMode.values()) {
            for (final String filename : EXPECTED_FILENAMES.subList(0, WALK_FRAME_COUNT)) {
                final BufferedImage frame = readFrame(mode, filename);

                assertThat(frame.getWidth()).as("%s/%s is one tile wide", mode, filename)
                    .isEqualTo(TILE_SPRITE_SIZE);
                assertThat(frame.getHeight()).as("%s/%s is one tile tall", mode, filename)
                    .isEqualTo(TILE_SPRITE_SIZE);
            }
        }
    }

    @Test
    @DisplayName("Each of the four wing frames is one 8x16 hardware sprite")
    void wingFramesAreHalfATileWide() throws IOException {
        // Prepare & Execute & Verify - ParaGoomba_Draw writes a single Sprite_RAM entry per wing, and the
        // object is OA1_HEIGHT32, so a wing is 8 px across and 16 px tall
        for (final GoombaMode mode : GoombaMode.values()) {
            for (final String filename : EXPECTED_FILENAMES.subList(WALK_FRAME_COUNT, EXPECTED_FILENAMES.size())) {
                final BufferedImage frame = readFrame(mode, filename);

                assertThat(frame.getWidth()).as("%s/%s is half a tile wide", mode, filename)
                    .isEqualTo(WING_WIDTH);
                assertThat(frame.getHeight()).as("%s/%s is a full tile tall", mode, filename)
                    .isEqualTo(TILE_SPRITE_SIZE);
            }
        }
    }

    @Test
    @DisplayName("Each wing beat ships its mirror as art, so the animator never flips a quad")
    void wingPairsAreHorizontalMirrors() throws IOException {
        // Prepare - the ROM stores one pattern for both wings and mirrors the right one through
        // SPR_HFLIP; carrying the mirror as a file keeps ParaGoombaAnimator flip-free
        assertMirrored("paragoomba_wing_up_left.png", "paragoomba_wing_up_right.png");

        // Execute & Verify
        assertMirrored("paragoomba_wing_down_left.png", "paragoomba_wing_down_right.png");
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void assertMirrored(final String leftFilename, final String rightFilename) throws IOException {
        final BufferedImage left = readFrame(GoombaMode.NORMAL, leftFilename);
        final BufferedImage right = readFrame(GoombaMode.NORMAL, rightFilename);

        for (int row = 0; row < left.getHeight(); row++) {
            for (int column = 0; column < left.getWidth(); column++) {
                final int mirroredColumn = left.getWidth() - 1 - column;
                assertThat(right.getRGB(column, row))
                    .as("pixel (%d, %d) of %s mirrors %s", column, row, rightFilename, leftFilename)
                    .isEqualTo(left.getRGB(mirroredColumn, row));
            }
        }
    }

    private static String framesContext(final GoombaMode mode) {
        return FRAMES_CONTEXT_TEMPLATE.formatted(mode.getFramesFolder());
    }

    private BufferedImage readFrame(
        final GoombaMode mode,
        final String filename
    ) throws IOException {
        final String path = framesContext(mode) + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
