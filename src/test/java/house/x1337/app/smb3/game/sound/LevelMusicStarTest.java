package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.event.PowerSwitchActivated;
import house.x1337.app.smb3.model.event.PowerSwitchExpired;
import house.x1337.app.smb3.model.event.StarInvincibilityMusicEnded;
import house.x1337.app.smb3.model.event.StarInvincibilityStarted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.MusicTrack.OVERWORLD;
import static house.x1337.app.smb3.enumeration.MusicTrack.P_SWITCH;
import static house.x1337.app.smb3.enumeration.MusicTrack.STAR;
import static house.x1337.app.smb3.game.engine.LevelRunStart.FRESH;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What {@link LevelMusic} plays while the star invincibility runs. */
class LevelMusicStarTest {
    private final GameEventBus gameEventBus = new GameEventBus();
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final LevelMusic levelMusic = new LevelMusic(gameEventBus, soundPlayer, new PowerSwitchTimeWindow());

    @BeforeEach
    void prepare() {
        levelMusic.subscribe();
        final LevelScene scene = mock(LevelScene.class);
        when(scene.getBackgroundMusic()).thenReturn(OVERWORLD);
        levelMusic.start(scene, FRESH);
        clearInvocations(soundPlayer);
    }

    @Test
    @DisplayName("Collecting a star plays the star music")
    void aStarPlaysItsMusic() {
        // Execute
        gameEventBus.publish(new StarInvincibilityStarted());

        // Verify
        verify(soundPlayer).play(STAR, false);
    }

    @Test
    @DisplayName("Collecting another star starts the star music over")
    void anotherStarRestartsTheMusic() {
        // Prepare
        gameEventBus.publish(new StarInvincibilityStarted());

        // Execute
        gameEventBus.publish(new StarInvincibilityStarted());

        // Verify
        verify(soundPlayer, times(2)).play(STAR, false);
    }

    @Test
    @DisplayName("The level's music comes back when the star's music ends")
    void theLevelMusicReturns() {
        // Prepare
        gameEventBus.publish(new StarInvincibilityStarted());

        // Execute
        gameEventBus.publish(new StarInvincibilityMusicEnded());

        // Verify
        verify(soundPlayer).play(OVERWORLD, false);
    }

    @Test
    @DisplayName("The star's music ending with no star music playing changes nothing")
    void anEndWithoutAStarChangesNothing() {
        // Execute
        gameEventBus.publish(new StarInvincibilityMusicEnded());

        // Verify
        verify(soundPlayer, never()).play(OVERWORLD, false);
    }

    @Test
    @DisplayName("A star collected while the P-switch runs leaves the P-switch music playing")
    void thePowerSwitchMusicWins() {
        // Prepare
        gameEventBus.publish(new PowerSwitchActivated());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new StarInvincibilityStarted());

        // Verify
        verify(soundPlayer, never()).play(STAR, false);
    }

    @Test
    @DisplayName("When the P-switch ends the star music resumes if the star's music is still running")
    void theStarMusicResumesAfterThePowerSwitch() {
        // Prepare
        gameEventBus.publish(new PowerSwitchActivated());
        gameEventBus.publish(new StarInvincibilityStarted());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PowerSwitchExpired());

        // Verify
        verify(soundPlayer).play(STAR, false);
    }

    @Test
    @DisplayName("When the P-switch ends the level's music returns if the star's music ended meanwhile")
    void theLevelMusicReturnsAfterThePowerSwitch() {
        // Prepare
        gameEventBus.publish(new PowerSwitchActivated());
        gameEventBus.publish(new StarInvincibilityStarted());
        gameEventBus.publish(new StarInvincibilityMusicEnded());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PowerSwitchExpired());

        // Verify
        verify(soundPlayer).play(OVERWORLD, false);
        verify(soundPlayer, never()).play(P_SWITCH, false);
    }
}
