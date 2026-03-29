package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import house.x1337.app.smb3.game.Physics;
import house.x1337.app.smb3.repository.PhysicsRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoPhysicsRepository implements PhysicsRepository {

    private static final String RECORD_ID = "physics";
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoCollection<Document> configurationMongoCollection;

    @Override
    public Map<Physics.Parameter, Float> load() {
        final Document doc = configurationMongoCollection.find(eq("_id", RECORD_ID)).first();
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
        final Document doc = new Document("_id", RECORD_ID);
        for (final Physics.Parameter param : Physics.Parameter.values()) {
            doc.put(param.name(), values.get(param));
        }
        configurationMongoCollection.replaceOne(eq("_id", RECORD_ID), doc, UPSERT);
    }
}


