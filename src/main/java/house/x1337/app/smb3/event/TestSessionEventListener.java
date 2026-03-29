package house.x1337.app.smb3.event;

import house.x1337.app.smb3.enumeration.GameEventType;
import house.x1337.app.smb3.model.event.GameEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public interface TestSessionEventListener extends EventBusAware {
    @Async
    @EventListener
    default void processEvent(final GameEvent gameEvent) {
        if (gameEvent.getSource() == GameEventType.GAME_ENGINE_STOPPED) {
            onGameEngineStopped();
        }
    }

    default void onGameEngineStopped() {
    }
}
