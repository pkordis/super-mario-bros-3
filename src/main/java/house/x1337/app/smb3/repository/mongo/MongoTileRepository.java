package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.repository.TileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoTileRepository implements TileRepository {

    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final MongoCollection<TileRecord> tileMongoCollection;

    @Override
    public void insert(final TileRecord record) {
        tileMongoCollection.replaceOne(eq("_id", record.getId()), record, UPSERT);
    }

    @Override
    public Iterable<TileRecord> findAll() {
        return tileMongoCollection.find();
    }

    @Override
    public void updateMetadata(final int id, final TileType type, final String description, final int[] argbData) {
        tileMongoCollection.updateOne(
            eq("_id", id),
            combine(
                set("type", type != null ? type.name() : null),
                set("description", description),
                set("argbData", argbData)
            )
        );
    }
}

