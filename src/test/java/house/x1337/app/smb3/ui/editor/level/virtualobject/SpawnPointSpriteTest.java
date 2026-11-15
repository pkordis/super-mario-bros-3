package house.x1337.app.smb3.ui.editor.level.virtualobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The standing player shown inside the spawn point: the player's still frame, trimmed to its opaque pixels and
 * mirrored.
 */
class SpawnPointSpriteTest {
    private static final int RED = Color.RED.getRGB();
    private static final int BLUE = Color.BLUE.getRGB();

    private final SpawnPointSprite sprite = new SpawnPointSprite();

    @Test
    @DisplayName("Trimming keeps only the rows and columns holding an opaque pixel")
    void trimmingKeepsTheOpaqueBounds() {
        // Prepare
        final BufferedImage source = new BufferedImage(6, 5, TYPE_INT_ARGB);
        source.setRGB(1, 2, RED);
        source.setRGB(3, 4, BLUE);

        // Execute
        final BufferedImage trimmed = sprite.trimmed(source);

        // Verify
        assertThat(trimmed.getWidth()).isEqualTo(3);
        assertThat(trimmed.getHeight()).isEqualTo(3);
        assertThat(trimmed.getRGB(0, 0)).isEqualTo(RED);
        assertThat(trimmed.getRGB(2, 2)).isEqualTo(BLUE);
    }

    @Test
    @DisplayName("Mirroring swaps left and right and keeps the rows")
    void mirroringSwapsLeftAndRight() {
        // Prepare
        final BufferedImage source = new BufferedImage(3, 2, TYPE_INT_ARGB);
        source.setRGB(0, 0, RED);
        source.setRGB(2, 1, BLUE);

        // Execute
        final BufferedImage mirrored = sprite.mirrored(source);

        // Verify
        assertThat(mirrored.getRGB(2, 0)).isEqualTo(RED);
        assertThat(mirrored.getRGB(0, 1)).isEqualTo(BLUE);
        assertThat(mirrored.getRGB(0, 0) >>> 24).as("The old left edge is clear").isZero();
    }

    @Test
    @DisplayName("The sprite is the player's still frame, trimmed and mirrored")
    void theSpriteIsTheStillFrameTrimmedAndMirrored() throws IOException {
        // Prepare
        final BufferedImage still;
        try (final InputStream input =
                 new DefaultResourceLoader().getResource(SpawnPointSprite.RESOURCE).getInputStream()) {
            still = ImageIO.read(input);
        }
        final BufferedImage expected = sprite.mirrored(sprite.trimmed(still));

        // Execute
        final BufferedImage image = sprite.getImage();

        // Verify
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).as("Narrower than the frame").isLessThan(still.getWidth());
        assertThat(image.getHeight()).as("No taller than the frame").isLessThanOrEqualTo(still.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertThat(image.getRGB(x, y)).as("Pixel %d, %d", x, y).isEqualTo(expected.getRGB(x, y));
            }
        }
    }

    @Test
    @DisplayName("The sprite is read once and reused")
    void theSpriteIsReadOnce() {
        // Execute & Verify
        assertThat(sprite.getImage()).isSameAs(sprite.getImage());
    }
}
