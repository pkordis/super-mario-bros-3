package house.x1337.app.smb3.event;

import house.x1337.app.smb3.enumeration.GameEventType;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface EventBusAware {
    default void publishEvent(final GameEventType eventType) {
        getBean(EventBus.class).publishEvent(eventType);
    }
}
