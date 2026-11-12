package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.config.db.NitriteJacksonMapper;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dizitart.no2.common.module.NitriteModule.module;
import static org.dizitart.no2.filters.FluentFilter.where;

/**
 * Runs the Nitrite settings repository against an in-memory database: one record in {@code gameData}, found by
 * {@code id: "settings"}, replaced as a whole on every save.
 */
class NitriteGameSettingsRepositoryTest {
    private Nitrite nitrite;
    private NitriteGameSettingsRepository repository;

    @BeforeEach
    void prepare() {
        nitrite = Nitrite.builder()
            .loadModule(module(new NitriteJacksonMapper()))
            .openOrCreate();
        repository = new NitriteGameSettingsRepository(nitrite);
    }

    @AfterEach
    void tearDown() {
        nitrite.close();
    }

    @Test
    @DisplayName("Nothing is loaded before the settings were ever saved")
    void nothingIsLoadedBeforeASave() {
        // Execute & Verify
        assertThat(repository.load()).isEmpty();
    }

    @Test
    @DisplayName("Saved fields are stored in the settings record of gameData and read back without its own fields")
    void savedFieldsAreReadBack() {
        // Prepare
        repository.save(Map.of("timerPace", "STANDARD"));

        // Execute
        final Map<String, Object> loaded = repository.load().orElseThrow();

        // Verify
        assertThat(loaded).containsExactlyEntriesOf(Map.of("timerPace", "STANDARD"));
        final Document record = gameData().find(where("id").eq("settings")).firstOrNull();
        assertThat(record).isNotNull();
        assertThat(record.get("timerPace")).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("A second save replaces the record instead of adding another one")
    void aSecondSaveReplacesTheRecord() {
        // Prepare
        repository.save(Map.of("timerPace", "STANDARD", "dropped", "yes"));

        // Execute
        repository.save(Map.of("timerPace", "REAL_TIME"));

        // Verify
        assertThat(gameData().size()).isEqualTo(1);
        assertThat(repository.load().orElseThrow()).containsExactlyEntriesOf(Map.of("timerPace", "REAL_TIME"));
    }

    private NitriteCollection gameData() {
        return nitrite.getCollection("gameData");
    }
}
