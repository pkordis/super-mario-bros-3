package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.event.GameEventBusAware;
import house.x1337.app.smb3.model.event.LevelScenePlayerSwitchedLayer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public interface LevelScenePlayerActionEventListener extends GameEventBusAware {
    @PostConstruct
    default void subscribeListeners() {
        getGameEventBus().subscribe(LevelScenePlayerSwitchedLayer.class, event -> {
            if (this instanceof LevelScenePlayer levelScenePlayer &&
                levelScenePlayer.getInputHandler().getId().equals(event.getInputHandlerId())
            ) {
                onLayerSwitch();
            }
        });
    }

    @PreDestroy
    default void unsubscribeListeners() {
        getGameEventBus().unsubscribe(LevelScenePlayerSwitchedLayer.class);
    }

    void onLayerSwitch();
}
