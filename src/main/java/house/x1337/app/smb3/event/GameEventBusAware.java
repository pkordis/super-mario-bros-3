package house.x1337.app.smb3.event;

import house.x1337.app.smb3.model.event.GameEvent;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface GameEventBusAware {
    default GameEventBus getGameEventBus() {
        return getBean(GameEventBus.class);
    }

    default <T extends GameEvent> void publish(final T event) {
        getGameEventBus().publish(event);
    }
}
