package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.Physics;
import house.x1337.app.smb3.repository.PhysicsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

import static house.x1337.app.smb3.game.Physics.Parameter.values;

/**
 * Service responsible for persisting and loading physics parameter values from the database.
 * The record with {@code id = "physics"} stores all tunable physics parameters.
 *
 * <p>If the record does not exist, every parameter defaults to {@code 0f}.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConfigurationService {

    private final PhysicsRepository physicsRepository;

    public void loadPhysics() {
        final Map<Physics.Parameter, Float> values = physicsRepository.load();
        final Physics physics = Physics.get();
        if (values.values().stream().allMatch(v -> v == 0f)) {
            log.info("No 'physics' record found in configuration; setting all parameters to 0.");
        } else {
            log.info("Loading physics parameters from DB.");
        }
        for (final Physics.Parameter param : values()) {
            physics.set(param, values.getOrDefault(param, 0f));
        }
    }

    public void savePhysics() {
        final Physics physics = Physics.get();
        final Map<Physics.Parameter, Float> values = new EnumMap<>(Physics.Parameter.class);
        for (final Physics.Parameter param : values()) {
            values.put(param, physics.get(param));
        }
        physicsRepository.save(values);
        log.info("Saved physics parameters to DB.");
    }
}
