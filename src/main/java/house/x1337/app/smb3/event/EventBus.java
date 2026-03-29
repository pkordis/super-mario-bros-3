package house.x1337.app.smb3.event;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.GameEventType;
import house.x1337.app.smb3.model.event.GameEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

@Singleton
@RequiredArgsConstructor
public class EventBus {
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishEvent(final GameEventType eventType) {
        final GameEvent gameEvent = eventType.wrap();
        applicationEventPublisher.publishEvent(gameEvent);
    }
}
