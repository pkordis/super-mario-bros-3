package house.x1337.app.smb3.game.player.level.death;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PlayerDeathFallEnded;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerDied;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.MusicTrack.PLAYER_DOWN;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;

/**
 * Ends a death: publishes {@link PlayerDied} once, when the death music has played to its end
 * ({@link SoundPlayComplete} for {@code PLAYER_DOWN}) and the body is gone. A player who fell into a pit is gone at
 * once; the body of a player killed by an enemy is gone once it has fallen past the bottom of the level
 * ({@link PlayerDeathFallEnded}). The two can come in either order.
 *
 * <p>The R reset forgets a death in progress. It is a singleton that outlives each engine, so it subscribes through
 * {@link #subscribe()} once per engine, as an engine's {@code destroy()} purges the bus.
 */
@Getter
@Singleton
@RequiredArgsConstructor
public final class PlayerDeathSequence {
    private final GameEventBus gameEventBus;
    private boolean deathInProgress;
    private boolean bodyGone;
    private boolean musicOver;

    /** Listens for a death, its music's end and its body's fall. Called once by each engine as it starts. */
    public void subscribe() {
        forget();
        gameEventBus.subscribe(PlayerDeathStarted.class, this::onPlayerDeathStarted);
        gameEventBus.subscribe(SoundPlayComplete.class, this::onSoundPlayComplete);
        gameEventBus.subscribe(PlayerDeathFallEnded.class, event -> onBodyGone());
        gameEventBus.subscribe(LevelSceneResetRequested.class, event -> forget());
    }

    private void onPlayerDeathStarted(final PlayerDeathStarted event) {
        deathInProgress = true;
        bodyGone = event.getKind() == FELL_OFF;
        musicOver = false;
    }

    private void onSoundPlayComplete(final SoundPlayComplete event) {
        if (event.getTrack() == PLAYER_DOWN) {
            musicOver = true;
            endIfDone();
        }
    }

    private void onBodyGone() {
        bodyGone = true;
        endIfDone();
    }

    private void endIfDone() {
        if (deathInProgress && bodyGone && musicOver) {
            forget();
            gameEventBus.publish(new PlayerDied());
        }
    }

    private void forget() {
        deathInProgress = false;
        bodyGone = false;
        musicOver = false;
    }
}
