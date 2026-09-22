package house.x1337.app.smb3.game.object.level.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.object.level.effect.TailAttackFlashAnimation.FRAME_COUNT;
import static house.x1337.app.smb3.game.object.level.effect.TailAttackFlashAnimation.LIFETIME_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * The tail-attack flash's timing, checked against the ROM without needing a renderer.
 *
 * <p>{@code Object_RespondToTailAttack} seeds {@code ShellKillFlash_Cnt = $0a} and prg007 @ PRG007_A268
 * decrements it once per tick, cycling the palette on {@code Counter_1 AND #$03} and toggling the
 * vertical flip on {@code Counter_1} bit 2.
 */
class TailAttackFlashAnimationTest {
    private static final String FRAMES_CONTEXT = "sprites/effect/wham/";

    /**
     * The frame arithmetic is pure — it reads only its argument — so it is exercised on a bare instance
     * rather than through the real constructor, which would need a live jME asset manager and scene graph.
     */
    private final TailAttackFlashAnimation animation = mock(TailAttackFlashAnimation.class, CALLS_REAL_METHODS);

    @Test
    @DisplayName("The flash lives for the ROM's 10 ticks")
    void lifetimeIsTenTicks() {
        assertThat(LIFETIME_TICKS).as("ShellKillFlash_Cnt = $0a").isEqualTo(10);
    }

    @Test
    @DisplayName("The palette advances every tick and wraps after four")
    void paletteAdvancesEveryTickAndWraps() {
        // The ROM reads Counter_1 & $03, which changes on every frame — not every few frames like the
        // poof cloud — so the flash shimmers through all four colours twice over its short life.
        final int[] actual = new int[LIFETIME_TICKS];
        for (int tick = 0; tick < LIFETIME_TICKS; tick++) {
            actual[tick] = animation.frameIndexAt(tick);
        }

        assertThat(actual)
            .as("one colour per tick, wrapping")
            .containsExactly(0, 1, 2, 3, 0, 1, 2, 3, 0, 1);
    }

    @Test
    @DisplayName("The vertical flip alternates every four ticks")
    void flipAlternatesEveryFourTicks() {
        // Counter_1 bit 2 (dasm prg007: LSR A x3 / ROR A / AND #SPR_VFLIP).
        final boolean[] actual = new boolean[LIFETIME_TICKS];
        for (int tick = 0; tick < LIFETIME_TICKS; tick++) {
            actual[tick] = animation.isFlippedVerticallyAt(tick);
        }

        assertThat(actual).containsExactly(
            false, false, false, false,
            true, true, true, true,
            false, false
        );
    }

    @Test
    @DisplayName("All four frames exist, are a single tile, and are distinct")
    void framesAreFourDistinctTiles() throws IOException {
        // Prepare
        final BufferedImage[] frames = new BufferedImage[FRAME_COUNT];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            frames[frame] = readFrame("frame_%d.png".formatted(frame));
        }

        // Verify - one tile each
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            assertThat(frames[frame].getWidth()).as("frame_%d width", frame).isEqualTo(TILE_SPRITE_SIZE);
            assertThat(frames[frame].getHeight()).as("frame_%d height", frame).isEqualTo(TILE_SPRITE_SIZE);
        }

        // Verify - genuinely four palette states, not the same image repeated
        for (int first = 0; first < FRAME_COUNT; first++) {
            for (int second = first + 1; second < FRAME_COUNT; second++) {
                assertThat(differs(frames[first], frames[second]))
                    .as("frame_%d differs from frame_%d", first, second)
                    .isTrue();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static boolean differs(final BufferedImage first, final BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private BufferedImage readFrame(final String filename) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FRAMES_CONTEXT + filename)) {
            assertThat(input).as("%s exists on the classpath", filename).isNotNull();
            return ImageIO.read(input);
        }
    }
}
