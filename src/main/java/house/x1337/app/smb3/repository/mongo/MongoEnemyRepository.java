package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import house.x1337.app.smb3.model.repository.EnemyRecord;
import house.x1337.app.smb3.repository.EnemyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoEnemyRepository implements EnemyRepository {
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoCollection<EnemyRecord> enemyMongoCollection;

    @Override
    public void upsert(final EnemyRecord record) {
        enemyMongoCollection.replaceOne(eq("_id", record.getId()), record, UPSERT);
    }

    @Override
    public Iterable<EnemyRecord> findAll() {
        return enemyMongoCollection.find();
    }

    @Override
    public Optional<EnemyRecord> findById(final String id) {
        final EnemyRecord record = enemyMongoCollection.find(eq("_id", id)).first();
        return Optional.ofNullable(record);
    }
}
