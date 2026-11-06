package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.model.repository.PipePortalRecord;

import java.util.List;

public interface PipePortalRepository {
    List<PipePortalRecord> findByLevelSceneId(String levelSceneId);
    void deleteByLevelSceneId(String levelSceneId);
    void insertAll(List<PipePortalRecord> records);
}
