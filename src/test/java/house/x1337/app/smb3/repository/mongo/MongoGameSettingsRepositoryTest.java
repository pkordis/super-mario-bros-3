package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The MongoDB settings repository keeps one record, {@code _id: "settings"}, upserted as a whole on every save. Run
 * against a mocked collection.
 */
class MongoGameSettingsRepositoryTest {
    private final MongoCollection<Document> collection = mock();
    private final FindIterable<Document> found = mock();
    private final MongoGameSettingsRepository repository = new MongoGameSettingsRepository(collection);

    @BeforeEach
    void prepare() {
        when(collection.find(any(Bson.class))).thenReturn(found);
    }

    @Test
    @DisplayName("Saving replaces the settings record by its _id, creating it if needed, with every field given")
    void savingUpsertsTheWholeRecord() {
        // Prepare
        final ArgumentCaptor<Document> record = ArgumentCaptor.forClass(Document.class);
        final ArgumentCaptor<ReplaceOptions> options = ArgumentCaptor.forClass(ReplaceOptions.class);

        // Execute
        repository.save(Map.of("timerPace", "STANDARD"));

        // Verify
        verify(collection).replaceOne(any(Bson.class), record.capture(), options.capture());
        assertThat(record.getValue())
            .containsExactlyInAnyOrderEntriesOf(Map.of("_id", "settings", "timerPace", "STANDARD"));
        assertThat(options.getValue().isUpsert()).isTrue();
    }

    @Test
    @DisplayName("Loading looks the record up by _id and hands back its fields without the _id")
    void loadingReadsTheFieldsWithoutTheId() {
        // Prepare
        when(found.first()).thenReturn(new Document("_id", "settings").append("timerPace", "STANDARD"));

        // Execute
        final Map<String, Object> loaded = repository.load().orElseThrow();

        // Verify
        assertThat(loaded).containsExactlyEntriesOf(Map.of("timerPace", "STANDARD"));
        verify(collection).find(eq("_id", "settings"));
    }

    @Test
    @DisplayName("No record loads nothing")
    void noRecordLoadsNothing() {
        // Execute & Verify
        assertThat(repository.load()).isEmpty();
    }
}
