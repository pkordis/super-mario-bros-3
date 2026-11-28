package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.model.event.StarInvincibilityMusicEnded;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The star invincibility's countdown and palette cycle. */
class LevelScenePlayerStarInvincibilityCapableTest {
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
    private final LevelFrameCounter frameCounter = new LevelFrameCounter();
    private final GameEngine gameEngine = mock(GameEngine.class);
    private final LevelScenePlayerStarInvincibilityCapable invincibilityCapable = mock(
        LevelScenePlayerStarInvincibilityCapable.class,
        CALLS_REAL_METHODS
    );

    LevelScenePlayerStarInvincibilityCapableTest() {
        when(invincibilityCapable.getRuntimeState()).thenReturn(runtimeState);
        when(invincibilityCapable.getGameEngine()).thenReturn(gameEngine);
        when(gameEngine.getLevelFrameCounter()).thenReturn(frameCounter);
    }

    @Test
    @DisplayName("The counter goes down on every second frame, so the invincibility lasts 447 or 448 frames")
    void itLastsAboutFourHundredAndFortyEightFrames() {
        // Prepare
        runtimeState.startStarInvincibility();
        runtimeState.setMovement(JUMPING);

        // Execute
        int frames = 0;
        while (runtimeState.isStarInvincible()) {
            frameCounter.tick();
            frames++;
            invincibilityCapable.tickStarInvincibilityCountdown();
        }

        // Verify
        assertThat(frames).isBetween(447, 448);
    }

    @Test
    @DisplayName("The music cue comes once, when the counter reaches 32, with 64 frames still to run")
    void theMusicCueComesOnceAtThirtyTwo() {
        // Prepare
        runtimeState.startStarInvincibility();

        // Execute
        int cueFrame = 0;
        for (int frame = 1; frame <= 448; frame++) {
            frameCounter.tick();
            final int before = runtimeState.getStarInvincibilityCounter();
            invincibilityCapable.tickStarInvincibilityCountdown();
            if (before > 32 && runtimeState.getStarInvincibilityCounter() == 32) {
                cueFrame = frame;
            }
        }

        // Verify
        verify(gameEngine, times(1)).publish(any(StarInvincibilityMusicEnded.class));
        assertThat(447 - cueFrame).as("Frames left after the cue").isEqualTo(64);
    }

    @Test
    @DisplayName("Landing ends the air flip")
    void landingEndsTheFlip() {
        // Prepare
        runtimeState.startStarInvincibility();
        runtimeState.setAirFlipping(true);

        // Execute
        invincibilityCapable.tickStarInvincibilityCountdown();

        // Verify
        assertThat(runtimeState.isAirFlipping()).isFalse();
    }

    @Test
    @DisplayName("Without the invincibility nothing counts down and no cue is published")
    void nothingHappensWithoutTheInvincibility() {
        // Execute
        for (int frame = 0; frame < 20; frame++) {
            frameCounter.tick();
            invincibilityCapable.tickStarInvincibilityCountdown();
        }

        // Verify
        assertThat(runtimeState.getStarInvincibilityCounter()).isZero();
        verify(gameEngine, never()).publish(any());
    }

    @Test
    @DisplayName("The palette changes every frame while the counter is 32 or more, and then every four frames")
    void thePaletteSlowsDownAtTheEnd() {
        // Prepare
        runtimeState.startStarInvincibility();

        // Execute & Verify
        for (int frame = 0; frame < 16; frame++) {
            assertThat(invincibilityCapable.starPalette()).as("Frame %d", frame).isEqualTo(frame % 4);
            frameCounter.tick();
        }
        runtimeState.setStarInvincibilityCounter(31);
        for (int frame = 0; frame < 16; frame++) {
            assertThat(invincibilityCapable.starPalette()).as("Slow frame %d", frame).isEqualTo((frame / 4) % 4);
            frameCounter.tick();
        }
    }

    @Test
    @DisplayName("Without the invincibility the palette is the normal one")
    void noInvincibilityMeansTheNormalPalette() {
        // Verify
        for (int frame = 0; frame < 16; frame++) {
            assertThat(invincibilityCapable.starPalette()).isZero();
            frameCounter.tick();
        }
    }
}
