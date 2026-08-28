package house.x1337.app.smb3.game.camera;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link LevelSceneVerticalScroll} against the SMB3 {@code Level_FreeVertScroll}
 * mode-0 behaviour used by World 1-1 (dasm prg008 Player_DoScrolling @
 * PRG008_B208 / PRG008_B246 / PRG008_B258): the camera rests locked at the
 * bottom of the level and only scrolls vertically while the player is flying or
 * climbing, capped at 3&nbsp;px/frame upward and returning to the bottom
 * afterwards.
 *
 * <p>Fixtures use a 20-tile-tall level and the real frustum half-height (6), so
 * the camera centre is bounded to {@code [6, 14]} and the dead-zone band offsets
 * are {@code [1.6, 3.6]} tiles above the camera centre.
 */
class LevelSceneVerticalScrollTest {

    private static final float TOLERANCE = 1.0e-4f;
    private static final float HALF_VIEW = 6.0f;
    private static final int ROWS = 20;
    private static final float BOTTOM = HALF_VIEW;              // 6
    private static final float MAX_UP_PER_TICK = 3.0f / 16.0f;  // 0.1875

    @Test
    @DisplayName("Locked at the bottom during normal play — jumping never scrolls the camera")
    void lockedAtBottomWhenNotFlying() {
        // Prepare
        final LevelSceneVerticalScroll scroll = LevelSceneVerticalScroll.forLevel(ROWS, HALF_VIEW);

        // Execute & Verify — the player jumps high (world Y rises) but no
        // flight/climb override is active, so the camera stays pinned.
        for (final float playerY : new float[] {6f, 9f, 12f, 15f, 10f, 6f}) {
            final float cameraY = scroll.update(playerY, false);
            assertThat(cameraY).as("camera stays locked at the bottom").isCloseTo(BOTTOM, within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("Flight scrolls the camera up, capped at 3 px/frame")
    void flightScrollsUpCappedAtThreePixelsPerFrame() {
        // Prepare
        final LevelSceneVerticalScroll scroll = LevelSceneVerticalScroll.forLevel(ROWS, HALF_VIEW);
        final float highPlayer = 15f; // well above the dead-zone band

        // Execute & Verify — each flying tick raises the camera by exactly the cap.
        float previous = BOTTOM;
        for (int tick = 0; tick < 4; tick++) {
            final float cameraY = scroll.update(highPlayer, true);
            assertThat(cameraY)
                .as("camera rises by the 3px/frame cap each tick")
                .isCloseTo(previous + MAX_UP_PER_TICK, within(TOLERANCE));
            previous = cameraY;
        }
    }

    @Test
    @DisplayName("Player inside the dead-zone band does not move the camera")
    void deadZoneHoldsCameraStill() {
        // Prepare — raise the camera off the bottom via flight so free-scroll is active.
        final LevelSceneVerticalScroll scroll = LevelSceneVerticalScroll.forLevel(ROWS, HALF_VIEW);
        for (int tick = 0; tick < 10; tick++) {
            scroll.update(15f, true);
        }
        final float raised = scroll.getCameraY();
        assertThat(raised).as("flight raised the camera above the bottom").isGreaterThan(BOTTOM);

        // Execute — place the player squarely inside the band ([1.6, 3.6] above centre).
        final float inBandPlayer = raised + 2.5f;
        final float cameraY = scroll.update(inBandPlayer, false);

        // Verify
        assertThat(cameraY).as("no scroll while the player sits within the dead zone")
            .isCloseTo(raised, within(TOLERANCE));
    }

    @Test
    @DisplayName("After flight, the camera returns to the bottom as the player descends and then re-locks")
    void returnsToBottomAfterFlight() {
        // Prepare — fly up for a while to lift the camera.
        final LevelSceneVerticalScroll scroll = LevelSceneVerticalScroll.forLevel(ROWS, HALF_VIEW);
        for (int tick = 0; tick < 20; tick++) {
            scroll.update(15f, true);
        }
        assertThat(scroll.getCameraY()).as("camera lifted by flight").isGreaterThan(BOTTOM);

        // Execute — flight ends and the player falls back to the ground. Even
        // with the override off, free-scroll persists until the bottom is
        // reached (dasm PRG008_B246).
        float cameraY = scroll.getCameraY();
        for (int tick = 0; tick < 200 && cameraY > BOTTOM + TOLERANCE; tick++) {
            cameraY = scroll.update(BOTTOM, false); // player back near the bottom
        }

        // Verify — the camera settles exactly at the bottom and stays there.
        assertThat(cameraY).as("camera eases back to the bottom").isCloseTo(BOTTOM, within(TOLERANCE));
        assertThat(scroll.update(6f, false)).as("and re-locks at the bottom").isCloseTo(BOTTOM, within(TOLERANCE));
    }

    @Test
    @DisplayName("Downward return is not capped to 3 px/frame")
    void downwardReturnIsNotRateCapped() {
        // Prepare — lift the camera high via flight.
        final LevelSceneVerticalScroll scroll = LevelSceneVerticalScroll.forLevel(ROWS, HALF_VIEW);
        for (int tick = 0; tick < 30; tick++) {
            scroll.update(20f, true);
        }
        final float raised = scroll.getCameraY();

        // Execute — a single tick with the player at the bottom.
        final float afterOneTick = scroll.update(BOTTOM, false);

        // Verify — the drop far exceeds the 3px/frame upward cap.
        assertThat(raised - afterOneTick)
            .as("downward correction is unclamped")
            .isGreaterThan(MAX_UP_PER_TICK * 2);
    }

    @Test
    @DisplayName("A level no taller than the viewport locks the camera to its centre")
    void shortLevelLocksToCentre() {
        // Prepare — 10-tile level with a 12-tile view (2 * HALF_VIEW).
        final int shortRows = 10;
        final LevelSceneVerticalScroll scroll = LevelSceneVerticalScroll.forLevel(shortRows, HALF_VIEW);
        final float centre = shortRows / 2.0f;

        // Execute & Verify — neither flight nor jumps move the locked centre.
        assertThat(scroll.update(9f, true)).isCloseTo(centre, within(TOLERANCE));
        assertThat(scroll.update(2f, false)).isCloseTo(centre, within(TOLERANCE));
    }
}
