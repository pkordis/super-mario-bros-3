package house.x1337.app.smb3.model.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;

/** A rectangle of level pixels: 32 by 16 pixels from (10, 20), so its last pixel is (41, 35). */
class PixelAreaTest {
    private final PixelArea area = new PixelArea(10, 20, 32, 16);

    @Test
    @DisplayName("It holds the pixels from its first to its last, both included")
    void containsItsPixels() {
        // Execute & Verify
        assertThat(area.contains(of(10, 20))).as("First").isTrue();
        assertThat(area.contains(of(41, 35))).as("Last").isTrue();
        assertThat(area.contains(of(42, 35))).as("Right of it").isFalse();
        assertThat(area.contains(of(10, 19))).as("Above it").isFalse();
    }

    @Test
    @DisplayName("Two areas overlap when they share a pixel, not when they only touch")
    void overlapsByThePixel() {
        // Execute & Verify
        assertThat(area.intersects(new PixelArea(41, 35, 4, 4))).as("Sharing the last pixel").isTrue();
        assertThat(area.intersects(new PixelArea(42, 20, 4, 4))).as("Touching on the right").isFalse();
        assertThat(area.intersects(new PixelArea(0, 0, 100, 100))).as("Around it").isTrue();
    }

    @Test
    @DisplayName("Clipped to a scene, it keeps the part inside, or nothing when it is all outside")
    void clipsToTheScene() {
        // Prepare - a scene 2 columns by 2 rows is 32 by 32 pixels
        final LevelSceneDimensions scene = new LevelSceneDimensions(2, 2);

        // Execute & Verify
        assertThat(area.clippedTo(scene)).contains(new PixelArea(10, 20, 22, 12));
        assertThat(new PixelArea(-8, -8, 10, 10).clippedTo(scene)).contains(new PixelArea(0, 0, 2, 2));
        assertThat(new PixelArea(40, 0, 4, 4).clippedTo(scene)).isEmpty();
    }

    @Test
    @DisplayName("A rectangle of cells covers 16 by 16 pixels per cell")
    void cellsToPixels() {
        // Execute & Verify
        assertThat(new MultiTileArea(2, 3, 2, 1).toPixelArea()).isEqualTo(new PixelArea(32, 48, 32, 16));
    }
}
