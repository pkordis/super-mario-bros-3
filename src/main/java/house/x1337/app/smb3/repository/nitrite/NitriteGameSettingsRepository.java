package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.repository.GameSettingsRepository;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.common.tuples.Pair;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.dizitart.no2.filters.FluentFilter.where;

/**
 * The settings record is found by {@code id: "settings"}, because this database reserves {@code _id} for ids it
 * assigns itself. Its own fields, those whose names start with an underscore, are left out of what is loaded.
 */
@Repository
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitriteGameSettingsRepository implements GameSettingsRepository {
    private static final String COLLECTION_NAME = "gameData";
    private static final String ID_FIELD = "id";

    private final NitriteCollection collection;

    public NitriteGameSettingsRepository(final Nitrite nitrite) {
        this.collection = nitrite.getCollection(COLLECTION_NAME);
    }

    @Override
    public Optional<Map<String, Object>> load() {
        final Document record = collection.find(where(ID_FIELD).eq(RECORD_ID)).firstOrNull();
        if (record == null) {
            return Optional.empty();
        }
        final Map<String, Object> fields = new LinkedHashMap<>();
        for (final Pair<String, Object> field : record) {
            if (!field.getFirst().equals(ID_FIELD) && !field.getFirst().startsWith("_")) {
                fields.put(field.getFirst(), field.getSecond());
            }
        }
        return Optional.of(fields);
    }

    @Override
    public void save(final Map<String, Object> fields) {
        final Document record = Document.createDocument(ID_FIELD, RECORD_ID);
        fields.forEach(record::put);
        collection.remove(where(ID_FIELD).eq(RECORD_ID));
        collection.insert(record);
    }
}
