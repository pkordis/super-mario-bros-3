package house.x1337.app.smb3.event;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.event.PlayerDied;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public interface TestSessionEventListener extends GameEventBusAware, GameEngineAware {
    @PostConstruct
    default void subscribeListeners() {
        final GameEventBus gameEventBus = getGameEventBus();
        final GameEngine gameEngine = getGameEngine();

        gameEventBus.subscribe(GameEngineStopped.class, event -> onGameEngineStopped());
        gameEventBus.subscribe(PlayerDied.class, event -> gameEngine.enqueue(this::onLevelSceneRestart));
    }

    @PreDestroy
    default void unsubscribeListeners() {
        getGameEventBus().unsubscribe(GameEngineStopped.class);
    }

    void onGameEngineStopped();
    void onLevelSceneRestart();
}
