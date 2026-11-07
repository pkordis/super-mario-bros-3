package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.model.repository.PipePortalRecord;
import house.x1337.app.smb3.repository.PipePortalRepository;
import lombok.RequiredArgsConstructor;
import org.dizitart.no2.repository.ObjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.model.repository.PipePortalRecord.Fields.levelSceneId;
import static house.x1337.app.smb3.model.repository.PipePortalRecord.Fields.linksToId;
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
    public Optional<PipePortalRecord> findById(final String portalId) {
        return Optional.ofNullable(pipePortalObjectRepository.getById(portalId));
    }

    @Override
    public List<PipePortalRecord> findAll() {
        return stream(pipePortalObjectRepository.find().spliterator(), false).toList();
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

    @Override
    public void updateLinksToId(final String portalId, final String partnerId) {
        findById(portalId).ifPresent(record -> {
            record.setLinksToId(partnerId);
            pipePortalObjectRepository.update(record);
        });
    }

    @Override
    public void clearLinksTo(final Collection<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        final List<PipePortalRecord> linked = stream(
            pipePortalObjectRepository.find(where(linksToId).in(targetIds.toArray(String[]::new))).spliterator(),
            false
        ).toList();
        for (final PipePortalRecord record : linked) {
            record.setLinksToId(null);
            pipePortalObjectRepository.update(record);
        }
    }
}
