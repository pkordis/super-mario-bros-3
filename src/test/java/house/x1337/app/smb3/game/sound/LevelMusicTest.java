package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.enumeration.PlayerDeathKind;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerTimerHurryUp;
import house.x1337.app.smb3.model.event.PowerSwitchActivated;
import house.x1337.app.smb3.model.event.PowerSwitchExpired;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.MusicTrack.ATHLETIC;
import static house.x1337.app.smb3.enumeration.MusicTrack.COURSE_CLEAR;
import static house.x1337.app.smb3.enumeration.MusicTrack.HURRY_UP;
import static house.x1337.app.smb3.enumeration.MusicTrack.OVERWORLD;
import static house.x1337.app.smb3.enumeration.MusicTrack.PLAYER_DOWN;
import static house.x1337.app.smb3.enumeration.MusicTrack.P_SWITCH;
import static house.x1337.app.smb3.enumeration.MusicTrack.UNDERGROUND;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.THROE;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.game.engine.LevelRunStart.FRESH;
import static house.x1337.app.smb3.game.engine.LevelRunStart.carryOver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What {@link LevelMusic} plays as a level is built, as the P-switch opens and closes, and for the jingles, with the
 * events that drive it published by hand.
 */
class LevelMusicTest {
    private final GameEventBus gameEventBus = new GameEventBus();
    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final LevelMusic levelMusic = new LevelMusic(gameEventBus, soundPlayer, powerSwitchTimeWindow);

    @BeforeEach
    void prepare() {
        levelMusic.subscribe();
    }

    @Test
    @DisplayName("A fresh build plays the scene's track from its start")
    void aFreshBuildPlaysTheScenesTrack() {
        // Execute
        levelMusic.start(scene(OVERWORLD), FRESH);

        // Verify
        verify(soundPlayer).play(OVERWORLD);
    }

    @Test
    @DisplayName("A fresh build of a scene without music stops whatever plays")
    void aSceneWithoutMusicIsSilent() {
        // Execute
        levelMusic.start(scene(null), FRESH);

        // Verify
        verify(soundPlayer).stopMusic();
        verify(soundPlayer, never()).play(any(MusicTrack.class));
    }

    @Test
    @DisplayName("A fresh build restarts the track even when it is already playing")
    void aFreshBuildRestartsTheTrack() {
        // Prepare
        when(soundPlayer.getCurrentTrack()).thenReturn(OVERWORLD);

        // Execute
        levelMusic.start(scene(OVERWORLD), FRESH);

        // Verify
        verify(soundPlayer).play(OVERWORLD);
    }

    @Test
    @DisplayName("A pipe into a scene with the same music keeps it playing")
    void aCarryOverKeepsTheSameTrack() {
        // Prepare
        when(soundPlayer.getCurrentTrack()).thenReturn(OVERWORLD);

        // Execute
        levelMusic.start(scene(OVERWORLD), carryOver(NORMAL));

        // Verify
        verify(soundPlayer, never()).play(any(MusicTrack.class));
        verify(soundPlayer, never()).stopMusic();
    }

    @Test
    @DisplayName("A pipe into a scene with other music switches to it")
    void aCarryOverSwitchesToADifferentTrack() {
        // Prepare
        when(soundPlayer.getCurrentTrack()).thenReturn(OVERWORLD);

        // Execute
        levelMusic.start(scene(UNDERGROUND), carryOver(NORMAL));

        // Verify
        verify(soundPlayer).play(UNDERGROUND);
    }

    @Test
    @DisplayName("A pipe into a scene without music stops the track")
    void aCarryOverIntoSilenceStops() {
        // Prepare
        when(soundPlayer.getCurrentTrack()).thenReturn(OVERWORLD);

        // Execute
        levelMusic.start(scene(null), carryOver(NORMAL));

        // Verify
        verify(soundPlayer).stopMusic();
    }

    @Test
    @DisplayName("A build with the P-switch window still open plays the P-switch music")
    void aBuildWithTheWindowOpenPlaysThePowerSwitchMusic() {
        // Prepare
        powerSwitchTimeWindow.activate();

        // Execute
        levelMusic.start(scene(OVERWORLD), FRESH);

        // Verify
        verify(soundPlayer).play(P_SWITCH);
        verify(soundPlayer, never()).play(OVERWORLD);
    }

    @Test
    @DisplayName("A pipe taken while the P-switch window is open keeps the P-switch music playing without a restart")
    void aCarryOverWithTheWindowOpenKeepsThePowerSwitchMusic() {
        // Prepare
        powerSwitchTimeWindow.activate();
        when(soundPlayer.getCurrentTrack()).thenReturn(P_SWITCH);

        // Execute
        levelMusic.start(scene(UNDERGROUND), carryOver(NORMAL));

        // Verify
        verify(soundPlayer, never()).play(any(MusicTrack.class));
        verify(soundPlayer, never()).stopMusic();
    }

    @Test
    @DisplayName("The window running out in the scene a pipe led to plays that scene's track")
    void theWindowRunningOutAfterAPipePlaysTheNewScenesTrack() {
        // Prepare
        powerSwitchTimeWindow.activate();
        when(soundPlayer.getCurrentTrack()).thenReturn(P_SWITCH);
        levelMusic.start(scene(UNDERGROUND), carryOver(NORMAL));

        // Execute
        gameEventBus.publish(new PowerSwitchExpired());

        // Verify
        verify(soundPlayer).play(UNDERGROUND);
    }

    @Test
    @DisplayName("The P-switch plays its music while the window is open, then the scene's track from its start")
    void thePowerSwitchSwapsTheMusicForItsDuration() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PowerSwitchActivated());
        gameEventBus.publish(new PowerSwitchExpired());

        // Verify
        final InOrder order = inOrder(soundPlayer);
        order.verify(soundPlayer).play(P_SWITCH);
        order.verify(soundPlayer).play(OVERWORLD);
        verify(soundPlayer, times(1)).play(P_SWITCH);
    }

    @Test
    @DisplayName("A second P-switch pressed while the window is open does not restart its music")
    void aSecondPressKeepsThePowerSwitchMusic() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        gameEventBus.publish(new PowerSwitchActivated());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PowerSwitchActivated());

        // Verify
        verifyNoInteractions(soundPlayer);
    }

    @Test
    @DisplayName("When the P-switch expires in a scene without music, the music stops")
    void thePowerSwitchExpiryInASilentSceneStops() {
        // Prepare
        levelMusic.start(scene(null), FRESH);
        gameEventBus.publish(new PowerSwitchActivated());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PowerSwitchExpired());

        // Verify
        verify(soundPlayer).stopMusic();
    }

    @Test
    @DisplayName("The hurry-up jingle plays once, then the scene's track starts again when the jingle has ended")
    void theHurryUpJingleResumesTheMusic() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PlayerTimerHurryUp());
        final boolean playingBeforeTheEnd = levelMusic.isJinglePlaying();
        verify(soundPlayer, never()).play(OVERWORLD);
        gameEventBus.publish(new SoundPlayComplete(HURRY_UP));

        // Verify
        assertThat(playingBeforeTheEnd).isTrue();
        assertThat(levelMusic.isJinglePlaying()).isFalse();
        final InOrder order = inOrder(soundPlayer);
        order.verify(soundPlayer).play(HURRY_UP);
        order.verify(soundPlayer).play(OVERWORLD);
        verify(soundPlayer, times(1)).play(HURRY_UP);
    }

    @Test
    @DisplayName("In a scene without music the end of the hurry-up jingle leaves the level silent")
    void theHurryUpJingleEndsInSilence() {
        // Prepare
        levelMusic.start(scene(null), FRESH);
        gameEventBus.publish(new PlayerTimerHurryUp());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new SoundPlayComplete(HURRY_UP));

        // Verify
        verify(soundPlayer).stopMusic();
        verify(soundPlayer, never()).play(any(MusicTrack.class));
    }

    @Test
    @DisplayName("Another track's end does not end the jingle that plays")
    void anotherTracksEndIsIgnored() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        gameEventBus.publish(new PlayerTimerHurryUp());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new SoundPlayComplete(COURSE_CLEAR));

        // Verify
        assertThat(levelMusic.isJinglePlaying()).isTrue();
        verifyNoInteractions(soundPlayer);
    }

    @Test
    @DisplayName("A P-switch pressed during the hurry-up jingle does not cut it, and plays once the jingle ends")
    void aPowerSwitchDuringAJingleWaitsForIt() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        gameEventBus.publish(new PlayerTimerHurryUp());
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PowerSwitchActivated());
        verifyNoInteractions(soundPlayer);
        gameEventBus.publish(new SoundPlayComplete(HURRY_UP));

        // Verify
        verify(soundPlayer).play(P_SWITCH);
        verify(soundPlayer, never()).play(OVERWORLD);
    }

    @ParameterizedTest
    @EnumSource(value = PlayerDeathKind.class, names = {"FELL_OFF", "THROE"})
    @DisplayName("A death plays the player-down jingle, and the level stays silent after it")
    void aDeathPlaysThePlayerDownJingle(final PlayerDeathKind kind) {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        clearInvocations(soundPlayer);

        // Execute
        gameEventBus.publish(new PlayerDeathStarted(kind));
        gameEventBus.publish(new SoundPlayComplete(PLAYER_DOWN));

        // Verify
        verify(soundPlayer).play(PLAYER_DOWN);
        verify(soundPlayer, never()).play(OVERWORLD);
        verify(soundPlayer, never()).stopMusic();
    }

    @Test
    @DisplayName("The card plays course clear until it ends, and the card's closing of the P-switch plays nothing")
    void theCardPlaysCourseClear() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        gameEventBus.publish(new PowerSwitchActivated());
        clearInvocations(soundPlayer);

        // Execute - the card closes the window without an event, then its jingle plays out
        powerSwitchTimeWindow.reset();
        gameEventBus.publish(new LevelCardCollected(MUSHROOM));
        final boolean playingBeforeTheEnd = levelMusic.isJinglePlaying();
        gameEventBus.publish(new SoundPlayComplete(COURSE_CLEAR));

        // Verify
        assertThat(playingBeforeTheEnd).isTrue();
        assertThat(levelMusic.isJinglePlaying()).isFalse();
        verify(soundPlayer).play(COURSE_CLEAR);
        verify(soundPlayer, never()).play(OVERWORLD);
        verify(soundPlayer, never()).play(P_SWITCH);
    }

    @Test
    @DisplayName("A level build cuts a running jingle and plays the new scene's track")
    void aBuildCutsAJingle() {
        // Prepare
        levelMusic.start(scene(OVERWORLD), FRESH);
        gameEventBus.publish(new PlayerDeathStarted(THROE));
        clearInvocations(soundPlayer);

        // Execute
        levelMusic.start(scene(ATHLETIC), FRESH);

        // Verify
        assertThat(levelMusic.isJinglePlaying()).isFalse();
        verify(soundPlayer).play(ATHLETIC);
    }

    private LevelScene scene(final MusicTrack track) {
        final LevelScene scene = mock(LevelScene.class);
        when(scene.getBackgroundMusic()).thenReturn(track);
        return scene;
    }
}
