package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.repository.LevelSceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;
import static java.util.stream.StreamSupport.stream;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoLevelSceneRepository implements LevelSceneRepository {
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoCollection<LevelSceneRecord> levelSceneMongoCollection;

    @Override
    public void upsert(final LevelSceneRecord record) {
        levelSceneMongoCollection.replaceOne(eq("_id", record.getId()), record, UPSERT);
    }

    @Override
    public Optional<LevelSceneRecord> findLastEdited() {
        final LevelSceneRecord record = levelSceneMongoCollection.find()
            .sort(descending("updatedAt"))
            .limit(1)
            .first();
        return Optional.ofNullable(record);
    }

    @Override
    public List<LevelSceneRecord> findPage(final int offset, final int pageSize) {
        return stream(
            levelSceneMongoCollection.find()
                .sort(descending("updatedAt"))
                .skip(offset)
                .limit(pageSize)
                .spliterator(),
            false
        ).toList();
    }

    @Override
    public void delete(final String id) {
        levelSceneMongoCollection.deleteOne(eq("_id", id));
    }
}
