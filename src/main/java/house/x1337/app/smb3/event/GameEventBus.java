package house.x1337.app.smb3.event;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.event.GameEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A lightweight, session-scoped event bus owned by a single {@code GameEngine}
 * instance. Prototype beans within the engine's object graph use this to
 * publish and subscribe to intra-session events without depending on Spring's
 * singleton {@code ApplicationEventPublisher}.
 *
 * <p>The bus is garbage-collected together with its owning engine — no manual
 * cleanup of subscriptions is needed unless a subscriber outlives the session.</p>
 */
@Slf4j
@Singleton
public final class GameEventBus {
    private final Map<Class<? extends GameEvent>, List<Consumer<GameEvent>>> listeners = new ConcurrentHashMap<>();

    public <T extends GameEvent> void subscribe(
        final Class<T> eventType,
        final Consumer<T> listener
    ) {
        if (!listeners.containsKey(eventType)) {
            listeners.put(eventType, new CopyOnWriteArrayList<>());
        }
        @SuppressWarnings("unchecked")
        final Consumer<GameEvent> castedListener = (Consumer<GameEvent>) listener;
        listeners.get(eventType).add(castedListener);
    }

    public <T extends GameEvent> void unsubscribe(final Class<T> eventType) {
        listeners.remove(eventType);
    }

    public <T extends GameEvent> void publish(final T event) {
        @SuppressWarnings("unchecked")
        final Class<T> eventType = (Class<T>) event.getClass();
        final List<Consumer<GameEvent>> consumers = listeners.get(eventType);
        if (consumers == null) {
            return;
        }
        for (final Consumer<GameEvent> consumer : consumers) {
            try {
                consumer.accept(event);
            } catch (final Exception e) {
                log.error(
                    "Error dispatching event {} to listener: {}",
                    event.getClass().getSimpleName(),
                    e.getMessage(),
                    e
                );
            }
        }
    }

    public void purgeEventListeners() {
        listeners.clear();
    }
}
