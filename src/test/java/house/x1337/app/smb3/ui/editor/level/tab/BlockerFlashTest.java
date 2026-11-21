package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.model.game.MultiTileArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The border that blinks around the objects blocking a placement: 250 ms on, 250 ms off, for 2 seconds, once per
 * blocker in a press and drag.
 */
class BlockerFlashTest {
    private static final MultiTileArea ENEMY = new MultiTileArea(1, 1, 2, 2);
    private static final MultiTileArea BRICK = new MultiTileArea(4, 0, 1, 1);

    private final AtomicLong clock = new AtomicLong(10_000);
    private final List<Rectangle> repainted = new ArrayList<>();
    private final BlockerFlash flash = new BlockerFlash(repainted::add, clock::get);

    @AfterEach
    void tearDown() {
        flash.stop();
    }

    @Test
    @DisplayName("A refusal blinks the blocker 250 ms on and 250 ms off, then it is gone after 2 seconds")
    void blinksForTwoSeconds() {
        // Prepare
        flash.beginGesture();

        // Execute & Verify
        flash.refuse(List.of(ENEMY));
        assertThat(flash.isOn()).as("At once").isTrue();
        assertThat(repainted).as("The blocker's box is repainted").contains(new Rectangle(64, 64, 128, 128));
        advance(249);
        assertThat(flash.isOn()).as("249 ms").isTrue();
        advance(1);
        assertThat(flash.isOn()).as("250 ms").isFalse();
        advance(250);
        assertThat(flash.isOn()).as("500 ms").isTrue();
        advance(1250);
        assertThat(flash.isOn()).as("1750 ms").isFalse();
        advance(250);
        flash.update();
        assertThat(flash.isOn()).as("2 s").isFalse();
        assertThat(flash.getFlashing()).as("Nothing is left to blink").isEmpty();
    }

    @Test
    @DisplayName("During one press and drag a blocker blinks once, and a new gesture can blink it again")
    void blinksOncePerGesture() {
        // Prepare
        flash.beginGesture();
        flash.refuse(List.of(ENEMY));
        advance(1000);

        // Execute & Verify
        flash.refuse(List.of(ENEMY));
        advance(1000);
        assertThat(flash.isOn()).as("The second refusal did not restart the 2 seconds").isFalse();
        flash.beginGesture();
        flash.refuse(List.of(ENEMY));
        assertThat(flash.isOn()).as("A new press").isTrue();
    }

    @Test
    @DisplayName("A new blocker restarts the 2 seconds and blinks along with the ones still blinking")
    void newBlockerRestartsTheBlink() {
        // Prepare
        flash.beginGesture();
        flash.refuse(List.of(ENEMY));
        advance(1500);

        // Execute
        flash.refuse(List.of(ENEMY, BRICK));
        advance(1000);

        // Verify
        assertThat(flash.isOn()).as("2.5 s after the first refusal, 1 s after the second").isTrue();
        assertThat(flash.getFlashing()).containsExactlyInAnyOrder(ENEMY, BRICK);
    }

    @Test
    @DisplayName("An object placed during the same press and drag never blinks as a blocker")
    void objectPlacedInTheGestureNeverBlinks() {
        // Prepare
        flash.beginGesture();
        flash.notePlaced(ENEMY);

        // Execute
        flash.refuse(List.of(ENEMY));

        // Verify
        assertThat(flash.isOn()).isFalse();
        assertThat(repainted).isEmpty();
    }

    @Test
    @DisplayName("The blinking border is black and yellow stripes just inside the blocker's box")
    void theBorderIsBlackAndYellowStripes() {
        // Prepare
        flash.beginGesture();
        flash.refuse(List.of(BRICK));
        final BufferedImage image = new BufferedImage(8 * TILE_SIZE, 2 * TILE_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();

        // Execute
        flash.paint(graphics);
        graphics.dispose();

        // Verify
        final Set<Integer> colours = new HashSet<>();
        for (int x = 4 * TILE_SIZE; x < 5 * TILE_SIZE; x++) {
            colours.add(image.getRGB(x, 2));
        }
        assertThat(colours).as("Along the top edge").contains(Color.BLACK.getRGB(), new Color(255, 216, 0).getRGB());
        assertThat(image.getRGB(4 * TILE_SIZE + TILE_SIZE / 2, TILE_SIZE / 2)).as("The middle stays clear").isZero();
        assertThat(image.getRGB(3 * TILE_SIZE + TILE_SIZE / 2, 2)).as("Outside the box").isZero();
    }

    private void advance(final long millis) {
        clock.addAndGet(millis);
    }
}
