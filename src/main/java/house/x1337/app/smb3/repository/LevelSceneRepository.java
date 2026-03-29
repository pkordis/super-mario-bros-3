package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.model.repository.LevelSceneRecord;

import java.util.List;
import java.util.Optional;

public interface LevelSceneRepository {

    void upsert(LevelSceneRecord record);

    Optional<LevelSceneRecord> findLastEdited();

    List<LevelSceneRecord> findPage(int offset, int pageSize);

    void delete(String id);
}

