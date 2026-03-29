package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.game.Physics;
import house.x1337.app.smb3.repository.PhysicsRepository;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;

import static org.dizitart.no2.filters.FluentFilter.where;

@Repository
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitritePhysicsRepository implements PhysicsRepository {

    private static final String COLLECTION_NAME = "configuration";
    private static final String RECORD_ID = "physics";

    private final NitriteCollection collection;

    public NitritePhysicsRepository(final Nitrite nitrite) {
        this.collection = nitrite.getCollection(COLLECTION_NAME);
    }

    @Override
    public Map<Physics.Parameter, Float> load() {
        final Document doc = collection.find(where("id").eq(RECORD_ID)).firstOrNull();
        final Map<Physics.Parameter, Float> result = new EnumMap<>(Physics.Parameter.class);
        for (final Physics.Parameter param : Physics.Parameter.values()) {
            if (doc != null) {
                final Object value = doc.get(param.name());
                result.put(param, value instanceof Number n ? n.floatValue() : 0f);
            } else {
                result.put(param, 0f);
            }
        }
        return result;
    }

    @Override
    public void save(final Map<Physics.Parameter, Float> values) {
        final Document existing = collection.find(where("id").eq(RECORD_ID)).firstOrNull();
        if (existing != null) {
            for (final Physics.Parameter param : Physics.Parameter.values()) {
                existing.put(param.name(), values.get(param));
            }
            collection.update(existing);
        } else {
            final Document doc = Document.createDocument("id", RECORD_ID);
            for (final Physics.Parameter param : Physics.Parameter.values()) {
                doc.put(param.name(), values.get(param));
            }
            collection.insert(doc);
        }
    }
}

