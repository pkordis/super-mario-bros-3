package house.x1337.app.smb3.model.service;

import java.util.Map;
import java.util.Optional;

public record LevelObjectData(Map<String, Object> asMap) {
    public <T extends Enum<T>> Optional<T> getEnum(
        final Class<T> type,
        final String propertyName
    ) {
        try {
            final T option = Enum.valueOf(type, String.valueOf(asMap.get(propertyName)));
            return Optional.of(option);
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    /** @return whether the property is set to {@code true}, as a boolean or as the text the editor stores */
    public boolean getBoolean(final String propertyName) {
        return asMap != null && Boolean.parseBoolean(String.valueOf(asMap.get(propertyName)).trim());
    }

    public boolean areAvailable() {
        return asMap != null && !asMap.isEmpty();
    }
}
