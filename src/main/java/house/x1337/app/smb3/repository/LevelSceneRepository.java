package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.model.repository.LevelSceneRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LevelSceneRepository {
    List<LevelSceneRecord> findPage(int offset, int pageSize);
    Map<String, String> findTitlesById();
    Optional<LevelSceneRecord> findById(String id);
    Optional<LevelSceneRecord> findLastEdited();
    void delete(String id);
    void upsert(LevelSceneRecord record);
}
