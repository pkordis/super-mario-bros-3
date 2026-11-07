package house.x1337.app.smb3.event;

import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import house.x1337.app.smb3.model.event.PlayerDied;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public interface TestSessionEventListener extends GameEventBusAware, GameEngineAware {
    @PostConstruct
    default void subscribeListeners() {
        final GameEventBus gameEventBus = getGameEventBus();
        gameEventBus.subscribe(GameEngineStopped.class, event -> onGameEngineStopped());
        gameEventBus.subscribe(PlayerDied.class, event -> requestLevelSceneRestart());
        gameEventBus.subscribe(LevelSceneResetRequested.class, event -> requestLevelSceneRestart());
        gameEventBus.subscribe(PipePortalTransitRequested.class, this::onPipePortalTransitRequested);
    }

    @PreDestroy
    default void unsubscribeListeners() {
        getGameEventBus().unsubscribe(GameEngineStopped.class);
    }

    void onGameEngineStopped();
    void onLevelSceneRestart();
    void requestLevelSceneRestart();
    void onPipePortalTransitRequested(PipePortalTransitRequested event);
}
