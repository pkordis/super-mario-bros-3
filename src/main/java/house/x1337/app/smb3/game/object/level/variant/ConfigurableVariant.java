package house.x1337.app.smb3.game.object.level.variant;

import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public interface ConfigurableVariant<V extends ConfigurableVariant.VariantData> {
    interface VariantData {}

    default void configure(V data) {
        if (data != null) {
            final Class<?> thisClass = getClass();
            final Logger log = getLogger(thisClass);
            log.warn("ConfigurableVariant-specific data for: {} disregarded", thisClass.getSimpleName());
        }
    }
}
