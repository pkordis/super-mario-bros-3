package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.PipePortalRepository;
import lombok.RequiredArgsConstructor;
import org.dizitart.no2.repository.ObjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

import static house.x1337.app.smb3.model.repository.PipePortalRecord.Fields.levelSceneId;
import static java.util.stream.StreamSupport.stream;
import static org.dizitart.no2.filters.FluentFilter.where;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitritePipePortalRepository implements PipePortalRepository {
    private final ObjectRepository<PipePortalRecord> pipePortalObjectRepository;

    @Override
    public List<PipePortalRecord> findByLevelSceneId(final String id) {
        return stream(
            pipePortalObjectRepository.find(where(levelSceneId).eq(id)).spliterator(),
            false
        ).toList();
    }

    @Override
    public void insertAll(final List<PipePortalRecord> records) {
        for (final PipePortalRecord record : records) {
            pipePortalObjectRepository.insert(record);
        }
    }

    @Override
    public void deleteByLevelSceneId(final String id) {
        pipePortalObjectRepository.remove(where(levelSceneId).eq(id));
    }
}
