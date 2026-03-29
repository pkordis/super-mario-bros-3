package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.repository.LevelSceneRepository;
import lombok.RequiredArgsConstructor;
import org.dizitart.no2.repository.Cursor;
import org.dizitart.no2.repository.ObjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static java.util.stream.StreamSupport.stream;
import static org.dizitart.no2.collection.FindOptions.orderBy;
import static org.dizitart.no2.common.SortOrder.Descending;
import static org.dizitart.no2.filters.FluentFilter.where;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitriteLevelSceneRepository implements LevelSceneRepository {

    private final ObjectRepository<LevelSceneRecord> levelSceneObjectRepository;

    @Override
    public void upsert(final LevelSceneRecord record) {
        levelSceneObjectRepository.update(record, true);
    }

    @Override
    public Optional<LevelSceneRecord> findLastEdited() {
        final Cursor<LevelSceneRecord> cursor = levelSceneObjectRepository.find(
            orderBy("updatedAt", Descending).limit(1L));
        for (final LevelSceneRecord record : cursor) {
            return Optional.of(record);
        }
        return Optional.empty();
    }

    @Override
    public List<LevelSceneRecord> findPage(final int offset, final int pageSize) {
        final Cursor<LevelSceneRecord> cursor = levelSceneObjectRepository.find(
            orderBy("updatedAt", Descending).skip(offset).limit(pageSize));
        return stream(cursor.spliterator(), false).toList();
    }

    @Override
    public void delete(final String id) {
        levelSceneObjectRepository.remove(where("id").eq(id));
    }
}
