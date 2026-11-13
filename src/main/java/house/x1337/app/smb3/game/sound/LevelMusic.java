package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.engine.LevelRunStart;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerTimerHurryUp;
import house.x1337.app.smb3.model.event.PowerSwitchActivated;
import house.x1337.app.smb3.model.event.PowerSwitchExpired;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.MusicTrack.COURSE_CLEAR;
import static house.x1337.app.smb3.enumeration.MusicTrack.HURRY_UP;
import static house.x1337.app.smb3.enumeration.MusicTrack.PLAYER_DOWN;
import static house.x1337.app.smb3.enumeration.MusicTrack.VICTORY_FANFARE;
import static house.x1337.app.smb3.enumeration.MusicTrack.P_SWITCH;

/**
 * Decides what music plays during a level, so no gameplay class has to.
 *
 * <p>The music the level wants is {@link MusicTrack#P_SWITCH} while the P-switch window is open, otherwise the
 * scene's background music, otherwise silence. Every level build starts it from the beginning through
 * {@link #start(LevelScene, LevelRunStart)}, except that a run carried over from another scene keeps a track that
 * is already playing when the new scene wants the same one.
 *
 * <p>Three moments play a jingle, which stops the music and plays once:
 * <ul>
 *     <li>the timer stepping down to 100 plays {@link MusicTrack#HURRY_UP}; after it the music the level wants
 *         starts again from its beginning,</li>
 *     <li>a death plays {@link MusicTrack#PLAYER_DOWN},</li>
 *     <li>the end-level card plays {@link MusicTrack#COURSE_CLEAR}, or {@link MusicTrack#VICTORY_FANFARE} when it
 *         makes three matching cards.</li>
 * </ul>
 * After the last two the level stays silent until it is built again. While a jingle plays, the P-switch opening or
 * closing only changes what plays once the jingle is over.
 *
 * <p>Everything here follows events: a jingle's end is its {@link SoundPlayComplete}, and the P-switch is
 * {@link PowerSwitchActivated} and {@link PowerSwitchExpired}. A window closed by a fresh level build, by a death or
 * by the card publishes nothing: the build calls {@link #start(LevelScene, LevelRunStart)}, which reads the window,
 * and the death's and the card's jingles silence the level anyway. A build carried over from another scene finds the
 * window still open, so the P-switch music plays on through the scene change.
 *
 * <p>It is a singleton that outlives each engine, so it subscribes through {@link #subscribe()} once per engine, as
 * an engine's {@code destroy()} purges the bus.
 */
@Singleton
@RequiredArgsConstructor
public final class LevelMusic {
    private final GameEventBus gameEventBus;
    private final SoundPlayer soundPlayer;
    private final PowerSwitchTimeWindow powerSwitchTimeWindow;

    private MusicTrack sceneTrack;
    private boolean powerSwitchOpen;
    private MusicTrack jingle;
    private boolean silenced;

    /** Listens for the jingles' moments and ends, and for the P-switch. Called once by each engine as it starts. */
    public void subscribe() {
        reset();
        gameEventBus.subscribe(PlayerTimerHurryUp.class, event -> playJingle(HURRY_UP));
        gameEventBus.subscribe(PlayerDeathStarted.class, event -> playJingle(PLAYER_DOWN));
        gameEventBus.subscribe(LevelCardCollected.class, event -> playJingle(
            event.isThreeOfAKind() ? VICTORY_FANFARE : COURSE_CLEAR
        ));
        gameEventBus.subscribe(SoundPlayComplete.class, this::onSoundPlayComplete);
        gameEventBus.subscribe(PowerSwitchActivated.class, event -> onPowerSwitchChanged(true));
        gameEventBus.subscribe(PowerSwitchExpired.class, event -> onPowerSwitchChanged(false));
    }

    /**
     * Starts the music of a freshly built level, cutting any jingle still playing.
     *
     * @param levelScene the scene just built, whose background music may be {@code null}
     * @param start      how the run started: a carried-over run keeps the track already playing if it is the
     *                   scene's own
     */
    public void start(final LevelScene levelScene, final LevelRunStart start) {
        reset();
        sceneTrack = levelScene.getBackgroundMusic();
        powerSwitchOpen = powerSwitchTimeWindow.isActive();
        final MusicTrack wanted = wantedTrack();
        if (start.isCarryOver() && wanted != null && wanted == soundPlayer.getCurrentTrack()) {
            return;
        }
        playWanted();
    }

    /** @return whether a jingle is still playing */
    public boolean isJinglePlaying() {
        return jingle != null;
    }

    private void reset() {
        jingle = null;
        silenced = false;
        powerSwitchOpen = false;
    }

    private void playJingle(final MusicTrack track) {
        soundPlayer.play(track);
        jingle = track;
        silenced = track != HURRY_UP;
    }

    private void onSoundPlayComplete(final SoundPlayComplete event) {
        if (event.getTrack() != jingle) {
            return;
        }
        jingle = null;
        if (!silenced) {
            playWanted();
        }
    }

    private void onPowerSwitchChanged(final boolean open) {
        if (open == powerSwitchOpen) {
            return;
        }
        powerSwitchOpen = open;
        if (jingle == null && !silenced) {
            playWanted();
        }
    }

    private void playWanted() {
        final MusicTrack wanted = wantedTrack();
        if (wanted == null) {
            soundPlayer.stopMusic();
        } else {
            soundPlayer.play(wanted);
        }
    }

    private MusicTrack wantedTrack() {
        return powerSwitchOpen ? P_SWITCH : sceneTrack;
    }
}
