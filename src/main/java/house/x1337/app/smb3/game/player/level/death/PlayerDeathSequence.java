package house.x1337.app.smb3.game.player.level.death;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PlayerDeathMusicFinished;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerDied;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;

@Getter
@Singleton
@RequiredArgsConstructor
public final class PlayerDeathSequence {
    private final GameEventBus gameEventBus;
    private boolean fellOffDeathInProgress;

    public void subscribe() {
        fellOffDeathInProgress = false;
        gameEventBus.subscribe(PlayerDeathStarted.class, this::onPlayerDeathStarted);
        gameEventBus.subscribe(PlayerDeathMusicFinished.class, event -> onDeathMusicFinished());
        gameEventBus.subscribe(LevelSceneResetRequested.class, event -> fellOffDeathInProgress = false);
    }

    private void onPlayerDeathStarted(final PlayerDeathStarted event) {
        fellOffDeathInProgress = event.getKind() == FELL_OFF;
    }

    private void onDeathMusicFinished() {
        if (!fellOffDeathInProgress) {
            return;
        }
        fellOffDeathInProgress = false;
        gameEventBus.publish(new PlayerDied());
    }
}
