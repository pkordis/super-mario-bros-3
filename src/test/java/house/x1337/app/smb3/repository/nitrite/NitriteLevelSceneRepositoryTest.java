package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.config.db.NitriteJacksonMapper;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import org.dizitart.no2.Nitrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.dizitart.no2.common.module.NitriteModule.module;

/**
 * Runs the Nitrite scene repository's lookups against an in-memory database with the production Jackson mapper.
 */
class NitriteLevelSceneRepositoryTest {
    private Nitrite nitrite;
    private NitriteLevelSceneRepository repository;

    @BeforeEach
    void prepare() {
        nitrite = Nitrite.builder()
            .loadModule(module(new NitriteJacksonMapper()))
            .openOrCreate();
        repository = new NitriteLevelSceneRepository(nitrite.getRepository(LevelSceneRecord.class));
    }

    @AfterEach
    void tearDown() {
        nitrite.close();
    }

    @Test
    @DisplayName("A scene is found by its id, and an unknown id finds nothing")
    void findsASceneById() {
        // Prepare
        repository.upsert(record("scene-1", "1-1"));
        repository.upsert(record("scene-2", "1-1 bonus room"));

        // Execute & Verify
        assertThat(repository.findById("scene-2").orElseThrow().getTitle()).isEqualTo("1-1 bonus room");
        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    @DisplayName("Every scene's title is listed under its id, untitled ones included")
    void listsEveryTitleById() {
        // Prepare
        repository.upsert(record("scene-1", "1-1"));
        repository.upsert(record("scene-2", null));

        // Execute
        final Map<String, String> titles = repository.findTitlesById();

        // Verify
        assertThat(titles).hasSize(2).containsEntry("scene-1", "1-1").containsEntry("scene-2", null);
    }

    @Test
    @DisplayName("The level completion card's cell is stored on the scene document and read back")
    void storesTheLevelCompletionCard() {
        // Prepare
        final LevelSceneRecord withCard = record("scene-1", "1-1");
        withCard.setLevelCompletionCard(of(168, 21));
        repository.upsert(withCard);
        repository.upsert(record("scene-2", "1-1 bonus room"));

        // Execute
        final LevelSceneRecord readWithCard = repository.findById("scene-1").orElseThrow();
        final LevelSceneRecord readWithoutCard = repository.findById("scene-2").orElseThrow();

        // Verify
        assertThat(readWithCard.getLevelCompletionCard()).isEqualTo(of(168, 21));
        assertThat(readWithoutCard.getLevelCompletionCard()).isNull();
    }

    private static LevelSceneRecord record(final String id, final String title) {
        return LevelSceneRecord.builder()
            .id(id)
            .title(title)
            .rows(5)
            .columns(4)
            .build();
    }
}
