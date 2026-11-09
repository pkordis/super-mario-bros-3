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
 * Pins the Piranha Plant's bundles: two 16x16 heads and one 16x16 stem per colour. The stem is green in
 * both, because the stem is always drawn in the green sprite palette.
 */
class PiranhaPlantAnimatorAssetsTest {
    private static final List<String> CONTEXTS = List.of(
        "sprites/enemy/piranha/plant/green/",
        "sprites/enemy/piranha/plant/red/"
    );
    private static final List<String> FILES = List.of(
        "piranha_plant_head_1.png",
        "piranha_plant_head_2.png",
        "piranha_plant_stem.png"
    );

    @Test
    @DisplayName("assets.json names the two heads, then the stem, in both colours")
    void theDescriptorNamesEveryFile() {
        for (final String context : CONTEXTS) {
            // Prepare
            final List<String> requested = new ArrayList<>();

            // Execute
            final PiranhaPlantAnimatorAssets assets = AnimatorAssetsLoader.load(
                PiranhaPlantAnimatorAssets.class,
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
    @DisplayName("The red plant's stem is the green plant's stem, pixel for pixel")
    void theRedStemIsGreen() throws IOException {
        // Prepare
        final BufferedImage green = read(CONTEXTS.get(0) + "piranha_plant_stem.png");

        // Execute
        final BufferedImage red = read(CONTEXTS.get(1) + "piranha_plant_stem.png");

        // Verify
        for (int row = 0; row < green.getHeight(); row++) {
            for (int column = 0; column < green.getWidth(); column++) {
                assertThat(red.getRGB(column, row))
                    .as("Pixel (%d, %d)", column, row)
                    .isEqualTo(green.getRGB(column, row));
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
