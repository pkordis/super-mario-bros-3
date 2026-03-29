package house.x1337.app.smb3.model.event;

import house.x1337.app.smb3.enumeration.GameEventType;

public class GameEngineStopped extends GameEvent {
    public GameEngineStopped() {
        super(GameEventType.GAME_ENGINE_STOPPED);
    }
}
