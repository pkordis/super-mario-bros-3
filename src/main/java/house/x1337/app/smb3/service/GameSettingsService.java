package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.TimerPace;
import house.x1337.app.smb3.model.settings.GameSettings;
import house.x1337.app.smb3.repository.GameSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the game settings in effect, loads them once at editor start and saves them. Game code reads settings only
 * from here.
 *
 * <p>Every setting has the default {@link GameSettings} gives it. The saved record overrides a default field by field:
 * a missing record leaves every default, and a field that is missing or cannot be read leaves that setting's default.
 * Each setting is stored under its own field name, an enum by its constant's name, e.g. {@value #TIMER_PACE_FIELD}.
 *
 * <p>The editor saves on the Swing thread and the game reads on its own, so the settings in effect are swapped as a
 * whole and never changed in place.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GameSettingsService {
    static final String TIMER_PACE_FIELD = "timerPace";

    private final GameSettingsRepository gameSettingsRepository;

    private volatile GameSettings settings = new GameSettings();

    /** Replaces the settings in effect with the saved ones, each missing or unreadable one with its default. */
    public void load() {
        final Optional<Map<String, Object>> saved = gameSettingsRepository.load();
        if (saved.isEmpty()) {
            log.info("No saved game settings; using the defaults.");
            settings = new GameSettings();
            return;
        }
        settings = toGameSettings(saved.get());
        log.info("Loaded game settings: {}", settings);
    }

    /** @return a copy of the settings in effect, which the caller may change freely */
    public GameSettings getSettings() {
        return settings.copy();
    }

    /** Saves every setting, defaults included, and puts them into effect. */
    public void save(final GameSettings newSettings) {
        gameSettingsRepository.save(toFields(newSettings));
        settings = newSettings.copy();
        log.info("Saved game settings: {}", settings);
    }

    private GameSettings toGameSettings(final Map<String, Object> fields) {
        final GameSettings loaded = new GameSettings();
        loaded.setTimerPace(readEnum(fields, loaded.getTimerPace()));
        return loaded;
    }

    private Map<String, Object> toFields(final GameSettings toSave) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(TIMER_PACE_FIELD, toSave.getTimerPace().name());
        return fields;
    }

    private <E extends Enum<E>> E readEnum(
        final Map<String, Object> fields,
        final E fallback
    ) {
        final Object value = fields.get(TIMER_PACE_FIELD);
        if (value == null) {
            log.warn("Game setting '{}' is not saved; using its default {}.", TIMER_PACE_FIELD, fallback);
            return fallback;
        }
        if (value instanceof final String name) {
            try {
                @SuppressWarnings("unchecked")
                final E enumValue = Enum.valueOf((Class<E>) TimerPace.class, name);
                return enumValue;
            } catch (final IllegalArgumentException e) {
                // Falls through to the warning below
            }
        }
        log.warn(
            "Game setting '{}' has an unreadable value '{}'; using its default {}.",
            TIMER_PACE_FIELD,
            value,
            fallback
        );
        return fallback;
    }
}
