package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import house.x1337.app.smb3.repository.GameSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

/** The settings record keeps {@code _id: "settings"}. */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoGameSettingsRepository implements GameSettingsRepository {
    private static final String ID_FIELD = "_id";
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoCollection<Document> gameDataMongoCollection;

    @Override
    public Optional<Map<String, Object>> load() {
        final Document record = gameDataMongoCollection.find(eq(ID_FIELD, RECORD_ID)).first();
        if (record == null) {
            return Optional.empty();
        }
        final Map<String, Object> fields = new LinkedHashMap<>(record);
        fields.remove(ID_FIELD);
        return Optional.of(fields);
    }

    @Override
    public void save(final Map<String, Object> fields) {
        final Document record = new Document(ID_FIELD, RECORD_ID);
        record.putAll(fields);
        gameDataMongoCollection.replaceOne(eq(ID_FIELD, RECORD_ID), record, UPSERT);
    }
}
