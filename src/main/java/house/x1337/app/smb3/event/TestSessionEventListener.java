package house.x1337.app.smb3.event;

import house.x1337.app.smb3.model.event.GameEngineStopped;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public interface TestSessionEventListener extends GameEventBusAware {
    @PostConstruct
    default void subscribeListeners() {
        getGameEventBus().subscribe(GameEngineStopped.class, event -> onGameEngineStopped());
    }

    @PreDestroy
    default void unsubscribeListeners() {
        getGameEventBus().unsubscribe(GameEngineStopped.class);
    }

    void onGameEngineStopped();
}
