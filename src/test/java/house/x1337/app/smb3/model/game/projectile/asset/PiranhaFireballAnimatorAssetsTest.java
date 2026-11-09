package house.x1337.app.smb3.model.game.projectile.asset;

import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballAnimator.FRAMES_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the piranha fireball's two 8x16 frames. They are drawn in a sprite palette that World 1-1 makes
 * white with a red border, so the art uses two opaque colours and no more.
 */
class PiranhaFireballAnimatorAssetsTest {
    private static final List<String> FILES = List.of("piranha_fireball_1.png", "piranha_fireball_2.png");
    private static final String TOP_ROWS = "........\n........\n........\n";
    private static final String BOTTOM_ROWS = "........\n........\n........\n........\n";

    /**
     * The two fireball patterns as the source sprite sheet stores them: {@code R} the red border, {@code W}
     * the white fill. The code adds every mirror itself, so a file drawn mirrored reverses the direction the
     * fireball spins in.
     */
    private static final List<String> STORED_PATTERNS = List.of(
        TOP_ROWS
            + "........\n"
            + "..RRRR..\n"
            + ".RRWWWR.\n"
            + ".RWWWWWR\n"
            + "RRRWWWWR\n"
            + "R..RWWWR\n"
            + ".R.RWWRR\n"
            + "R...RRR.\n"
            + "..R.....\n"
            + BOTTOM_ROWS,
        TOP_ROWS
            + "..R.....\n"
            + ".R.RRR..\n"
            + "..R.RRR.\n"
            + "R...RWRR\n"
            + "..RRWWWR\n"
            + ".RWWWWWR\n"
            + ".RWWWWWR\n"
            + ".RRWWWR.\n"
            + "..RRRR..\n"
            + BOTTOM_ROWS
    );

    @Test
    @DisplayName("assets.json names the first frame then the second")
    void theDescriptorNamesBothFrames() {
        // Prepare
        final List<String> requested = new ArrayList<>();

        // Execute
        final PiranhaFireballAnimatorAssets assets = AnimatorAssetsLoader.load(
            PiranhaFireballAnimatorAssets.class,
            FRAMES_CONTEXT,
            filename -> {
                requested.add(filename);
                return null;
            }
        );

        // Verify
        assertThat(requested).containsExactlyElementsOf(FILES);
        assertThat(assets.frames()).hasSize(2);
    }

    @Test
    @DisplayName("Both frames are 8x16 and use only a white fill and a red border")
    void bothFramesAreWhiteWithARedBorder() throws IOException {
        for (final String file : FILES) {
            // Prepare
            final BufferedImage image = read(FRAMES_CONTEXT + file);
            final Set<Integer> colours = new HashSet<>();

            // Execute
            for (int row = 0; row < image.getHeight(); row++) {
                for (int column = 0; column < image.getWidth(); column++) {
                    final int argb = image.getRGB(column, row);
                    if ((argb >>> 24) != 0) {
                        colours.add(argb);
                    }
                }
            }

            // Verify
            assertThat(image.getWidth()).as(file).isEqualTo(8);
            assertThat(image.getHeight()).as(file).isEqualTo(16);
            assertThat(colours).as(file).hasSize(2).contains(0xFFFFFFFF);
            assertThat(colours.stream().filter(argb -> argb != 0xFFFFFFFF))
                .as("%s: the border is red", file)
                .allMatch(argb -> ((argb >> 16) & 0xFF) > 2 * ((argb >> 8) & 0xFF));
        }
    }

    @Test
    @DisplayName("Both frames are the patterns as stored, not mirrored, so the fireball spins the right way")
    void bothFramesAreThePatternsAsStored() throws IOException {
        for (int frame = 0; frame < FILES.size(); frame++) {
            // Prepare
            final BufferedImage image = read(FRAMES_CONTEXT + FILES.get(frame));

            // Execute
            final String pattern = asPattern(image);

            // Verify
            assertThat(pattern).as(FILES.get(frame)).isEqualTo(STORED_PATTERNS.get(frame));
        }
    }

    private static String asPattern(final BufferedImage image) {
        final StringBuilder pattern = new StringBuilder();
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                final int argb = image.getRGB(column, row);
                if ((argb >>> 24) == 0) {
                    pattern.append('.');
                } else {
                    pattern.append(argb == 0xFFFFFFFF ? 'W' : 'R');
                }
            }
            pattern.append('\n');
        }
        return pattern.toString();
    }

    private BufferedImage read(final String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
