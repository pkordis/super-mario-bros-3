package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.event.GameEvent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GameEventType {
    GAME_ENGINE_STOPPED(GameEngineStopped.class);

    private final Class<? extends GameEvent> eventClass;

    public GameEvent wrap() {
        try {
            return eventClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create event instance", e);
        }
    }
}
