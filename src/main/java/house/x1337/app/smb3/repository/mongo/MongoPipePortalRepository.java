package house.x1337.app.smb3.repository.mongo;

import com.mongodb.client.MongoCollection;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.PipePortalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Updates.set;
import static house.x1337.app.smb3.model.repository.PipePortalRecord.Fields.levelSceneId;
import static house.x1337.app.smb3.model.repository.PipePortalRecord.Fields.linksToId;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoPipePortalRepository implements PipePortalRepository {
    private final MongoCollection<PipePortalRecord> pipePortalMongoCollection;

    @Override
    public List<PipePortalRecord> findByLevelSceneId(final String id) {
        return pipePortalMongoCollection.find(eq(levelSceneId, id)).into(new ArrayList<>());
    }

    @Override
    public Optional<PipePortalRecord> findById(final String id) {
        return Optional.ofNullable(pipePortalMongoCollection.find(eq("_id", id)).first());
    }

    @Override
    public List<PipePortalRecord> findAll() {
        return pipePortalMongoCollection.find().into(new ArrayList<>());
    }

    @Override
    public void insertAll(final List<PipePortalRecord> records) {
        // insertMany rejects an empty list.
        if (records.isEmpty()) {
            return;
        }
        pipePortalMongoCollection.insertMany(records);
    }

    @Override
    public void deleteByLevelSceneId(final String id) {
        pipePortalMongoCollection.deleteMany(eq(levelSceneId, id));
    }

    @Override
    public void updateLinksToId(final String id, final String partnerId) {
        pipePortalMongoCollection.updateOne(eq("_id", id), set(linksToId, partnerId));
    }

    @Override
    public void clearLinksTo(final Collection<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        pipePortalMongoCollection.updateMany(in(linksToId, targetIds), set(linksToId, null));
    }
}
