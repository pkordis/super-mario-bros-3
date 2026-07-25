package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.repository.LevelObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoLevelObjectRepository implements LevelObjectRepository {

    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoCollection<LevelObjectRecord> levelObjectMongoCollection;

    @Override
    public void upsert(final LevelObjectRecord record) {
        levelObjectMongoCollection.replaceOne(eq("_id", record.getId()), record, UPSERT);
    }

    @Override
    public Iterable<LevelObjectRecord> findAll() {
        return levelObjectMongoCollection.find();
    }

    @Override
    public Optional<LevelObjectRecord> findById(final int id) {
        final LevelObjectRecord record = levelObjectMongoCollection.find(eq("_id", id)).first();
        return Optional.ofNullable(record);
    }
}
