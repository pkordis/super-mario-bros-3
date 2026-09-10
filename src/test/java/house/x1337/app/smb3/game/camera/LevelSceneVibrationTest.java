package house.x1337.app.smb3.game.camera;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link LevelSceneVibration} against SMB3's {@code Player_DoVibration}
 * (dasm prg008.asm ~:950, table {@code VibrationOffset} ~:945).
 *
 * <p>The expected pattern is the ROM's, not the table's declaration order: {@code DEC} operates on
 * memory, so the index is the <em>pre</em>-decrement count {@code & 3}. With {@code $10} frames the
 * indices descend {@code 0, 3, 2, 1} through the table {@code 0, 2, 3, 1}, giving offsets
 * {@code 0, 1, 3, 2} played four times over 16 frames.
 */
class LevelSceneVibrationTest {

    private static final double TOLERANCE = 1.0e-9;
    private static final int POWER_SHAKE_FRAMES = 16;
    private static final int[] EXPECTED_CYCLE = {0, 1, 3, 2};

    @Test
    @DisplayName("Idle by default — no offset and nothing to tick down")
    void idleByDefault() {
        // Prepare
        final LevelSceneVibration vibration = new LevelSceneVibration();

        // Execute
        vibration.tick();

        // Verify
        assertThat(vibration.isVibrating()).as("Idle vibration reports not vibrating").isFalse();
        assertThat(vibration.getVerticalOffsetPixels()).isZero();
        assertThat(vibration.getCameraOffsetUnits()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A power shake plays the 0, 1, 3, 2 pixel cycle four times over 16 frames")
    void powerShakePlaysFourCycles() {
        // Prepare
        final LevelSceneVibration vibration = new LevelSceneVibration();
        vibration.powerShake();

        // Execute & Verify
        for (int frame = 0; frame < POWER_SHAKE_FRAMES; frame++) {
            vibration.tick();
            assertThat(vibration.getVerticalOffsetPixels())
                .as("Offset at shake frame %d", frame)
                .isEqualTo(EXPECTED_CYCLE[frame % EXPECTED_CYCLE.length]);
        }
    }

    @Test
    @DisplayName("The shake ends after exactly 16 frames and stays at rest")
    void shakeEndsAfterSixteenFrames() {
        // Prepare
        final LevelSceneVibration vibration = new LevelSceneVibration();
        vibration.powerShake();
        for (int frame = 0; frame < POWER_SHAKE_FRAMES - 1; frame++) {
            vibration.tick();
        }

        // Execute — the 16th tick consumes the final frame, the 17th finds nothing left
        vibration.tick();
        final boolean vibratingAfterLastFrame = vibration.isVibrating();
        vibration.tick();

        // Verify
        assertThat(vibratingAfterLastFrame).as("Shake is spent after its 16th frame").isFalse();
        assertThat(vibration.getVerticalOffsetPixels()).isZero();
    }

    @Test
    @DisplayName("The press frame itself shows no displacement — $10 & 3 == 0")
    void firstFrameHasNoDisplacement() {
        // Prepare
        final LevelSceneVibration vibration = new LevelSceneVibration();

        // Execute
        vibration.powerShake();
        vibration.tick();

        // Verify
        assertThat(vibration.getVerticalOffsetPixels()).isZero();
        assertThat(vibration.isVibrating()).as("Shake is still running after its first frame").isTrue();
    }

    @Test
    @DisplayName("Camera offset is the pixel offset negated and scaled to game units")
    void cameraOffsetIsNegatedPixelsInGameUnits() {
        // Prepare — advance to the frame carrying the peak offset of 3 px
        final LevelSceneVibration vibration = new LevelSceneVibration();
        vibration.powerShake();
        vibration.tick();
        vibration.tick();

        // Execute
        vibration.tick();

        // Verify — the ROM adds to Level_VertScroll (view moves down the level); the camera is Y-up
        assertThat(vibration.getVerticalOffsetPixels()).isEqualTo(3);
        assertThat(vibration.getCameraOffsetUnits()).isCloseTo(-3.0 / 16.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Restarting an in-flight shake reloads the full 16 frames")
    void restartReloadsTheCounter() {
        // Prepare
        final LevelSceneVibration vibration = new LevelSceneVibration();
        vibration.powerShake();
        for (int frame = 0; frame < 10; frame++) {
            vibration.tick();
        }

        // Execute
        vibration.powerShake();

        // Execute & Verify — the cycle restarts from the top
        for (int frame = 0; frame < POWER_SHAKE_FRAMES; frame++) {
            vibration.tick();
            assertThat(vibration.getVerticalOffsetPixels())
                .as("Offset at restarted shake frame %d", frame)
                .isEqualTo(EXPECTED_CYCLE[frame % EXPECTED_CYCLE.length]);
        }
        assertThat(vibration.isVibrating()).as("Restarted shake is spent after 16 more frames").isFalse();
    }

    @Test
    @DisplayName("Reset clears a running shake so it cannot leak into the next level")
    void resetClearsARunningShake() {
        // Prepare
        final LevelSceneVibration vibration = new LevelSceneVibration();
        vibration.powerShake();
        vibration.tick();
        vibration.tick();

        // Execute
        vibration.reset();

        // Verify
        assertThat(vibration.isVibrating()).isFalse();
        assertThat(vibration.getVerticalOffsetPixels()).isZero();
        vibration.tick();
        assertThat(vibration.getVerticalOffsetPixels()).as("Still at rest after a further tick").isZero();
    }
}
