package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.model.repository.PipePortalRecord;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PipePortalRepository {
    List<PipePortalRecord> findAll();
    List<PipePortalRecord> findByLevelSceneId(String levelSceneId);
    Optional<PipePortalRecord> findById(String id);
    void clearLinksTo(Collection<String> targetIds);
    void deleteByLevelSceneId(String levelSceneId);
    void insertAll(List<PipePortalRecord> records);
    void updateLinksToId(String id, String linksToId);
}
