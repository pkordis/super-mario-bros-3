package house.x1337.app.smb3.engine.core;

import house.x1337.app.smb3.event.EventBusAware;

public sealed interface GameEngineCapabilities
    extends
        GameEngineRenderer,
        EventBusAware
    permits
        GameEngine {
}

