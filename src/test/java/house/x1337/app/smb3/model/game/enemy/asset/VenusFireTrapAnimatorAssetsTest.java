package house.x1337.app.smb3.model.game.enemy.asset;

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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the fire plant's bundles: two 16x16 heads ({@code $F1 $F3} and {@code $F5 $F7}, prg005.asm:425) and
 * one 16x16 stem per colour. The stem is the plain plant's green one in both, because
 * {@code ObjNorm_Piranha} forces {@code SPR_PAL2} on it (prg005.asm:1407-1430).
 */
class VenusFireTrapAnimatorAssetsTest {
    private static final List<String> CONTEXTS = List.of(
        "sprites/enemy/piranha/venus/green/",
        "sprites/enemy/piranha/venus/red/"
    );
    private static final List<String> FILES = List.of(
        "venus_fire_trap_head_1.png",
        "venus_fire_trap_head_2.png",
        "venus_fire_trap_stem.png"
    );
    private static final String PLAIN_STEM = "sprites/enemy/piranha/plant/green/piranha_plant_stem.png";

    @Test
    @DisplayName("assets.json names the two heads, then the stem, in both colours")
    void theDescriptorNamesEveryFile() {
        for (final String context : CONTEXTS) {
            // Prepare
            final List<String> requested = new ArrayList<>();

            // Execute
            final VenusFireTrapAnimatorAssets assets = AnimatorAssetsLoader.load(
                VenusFireTrapAnimatorAssets.class,
                context,
                filename -> {
                    requested.add(filename);
                    return null;
                }
            );

            // Verify
            assertThat(requested).as(context).containsExactlyElementsOf(FILES);
            assertThat(assets.head()).hasSize(2);
        }
    }

    @Test
    @DisplayName("Every file is a 16x16 sprite")
    void everyFileIsSixteenBySixteen() throws IOException {
        for (final String context : CONTEXTS) {
            for (final String file : FILES) {
                // Prepare & Execute
                final BufferedImage image = read(context + file);

                // Verify
                assertThat(image.getWidth()).as(context + file).isEqualTo(TILE_SPRITE_SIZE);
                assertThat(image.getHeight()).as(context + file).isEqualTo(TILE_SPRITE_SIZE);
            }
        }
    }

    @Test
    @DisplayName("Both colours' stems are the plain plant's green stem, pixel for pixel")
    void bothStemsAreThePlainGreenStem() throws IOException {
        // Prepare
        final BufferedImage plain = read(PLAIN_STEM);

        for (final String context : CONTEXTS) {
            // Execute
            final BufferedImage stem = read(context + "venus_fire_trap_stem.png");

            // Verify
            for (int row = 0; row < plain.getHeight(); row++) {
                for (int column = 0; column < plain.getWidth(); column++) {
                    assertThat(stem.getRGB(column, row))
                        .as("%s pixel (%d, %d)", context, column, row)
                        .isEqualTo(plain.getRGB(column, row));
                }
            }
        }
    }

    private BufferedImage read(final String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("%s exists on the classpath", path).isNotNull();
            return ImageIO.read(input);
        }
    }
}
