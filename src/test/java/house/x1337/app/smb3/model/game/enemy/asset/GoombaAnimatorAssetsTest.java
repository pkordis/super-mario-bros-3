package house.x1337.app.smb3.model.game.enemy.asset;

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

class GoombaAnimatorAssetsTest {
    private static final String FRAMES_CONTEXT = "sprites/enemy/goomba/normal/";

    @Test
    @DisplayName("The mode's assets.json names the walk cycle in Objects_Frame order")
    void descriptorNamesTheWalkCycle() {
        // Prepare
        final List<String> requestedFilenames = new ArrayList<>();

        // Execute
        final GoombaAnimatorAssets assets = AnimatorAssetsLoader.load(
            GoombaAnimatorAssets.class,
            FRAMES_CONTEXT,
            filename -> {
                requestedFilenames.add(filename);
                return null;
            }
        );

        // Verify
        assertThat(requestedFilenames)
            .as("Every name in the descriptor is resolved through the sprite loader")
            .containsExactly("goomba_left.png", "goomba_right.png");
        assertThat(assets.walkFrameTextures())
            .as("The record's component is populated from the matching JSON key")
            .hasSize(requestedFilenames.size());
    }

    @Test
    @DisplayName("Both walking frames are a single 16x16 tile")
    void framesAreOneTileEach() throws IOException {
        for (final String filename : walkFrameFilenames()) {
            final BufferedImage frame = readFrame(filename);

            assertThat(frame.getWidth()).as("%s is one tile wide", filename).isEqualTo(TILE_SPRITE_SIZE);
            assertThat(frame.getHeight()).as("%s is one tile tall", filename).isEqualTo(TILE_SPRITE_SIZE);
        }
    }

    @Test
    @DisplayName("The two walking frames are horizontal mirrors of one another")
    void framesAreMirrored() throws IOException {
        // Prepare
        final List<String> filenames = walkFrameFilenames();
        final BufferedImage first = readFrame(filenames.getFirst());
        final BufferedImage second = readFrame(filenames.getLast());

        // Execute & Verify
        for (int row = 0; row < TILE_SPRITE_SIZE; row++) {
            for (int column = 0; column < TILE_SPRITE_SIZE; column++) {
                final int mirroredColumn = TILE_SPRITE_SIZE - 1 - column;
                assertThat(second.getRGB(column, row))
                    .as("pixel (%d, %d) mirrors the other frame", column, row)
                    .isEqualTo(first.getRGB(mirroredColumn, row));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** The walk-cycle filenames as the descriptor lists them, so the pixel tests follow the JSON. */
    private List<String> walkFrameFilenames() {
        final List<String> filenames = new ArrayList<>();
        AnimatorAssetsLoader.load(
            GoombaAnimatorAssets.class,
            FRAMES_CONTEXT,
            filename -> {
                filenames.add(filename);
                return null;
            }
        );
        return filenames;
    }

    private BufferedImage readFrame(final String filename) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FRAMES_CONTEXT + filename)) {
            assertThat(input).as("%s exists on the classpath", filename).isNotNull();
            return ImageIO.read(input);
        }
    }
}
