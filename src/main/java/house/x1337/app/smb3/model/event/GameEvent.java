package house.x1337.app.smb3.model.event;

import house.x1337.app.smb3.enumeration.GameEventType;
import org.springframework.context.ApplicationEvent;

public abstract class GameEvent extends ApplicationEvent {
    public GameEvent(final GameEventType eventType) {
        super(eventType);
    }

    @Override
    public GameEventType getSource() {
        return (GameEventType) super.getSource();
    }
}
