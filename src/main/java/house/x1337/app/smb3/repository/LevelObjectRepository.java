package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.model.repository.LevelObjectRecord;

import java.util.Optional;

public interface LevelObjectRepository {

    void upsert(LevelObjectRecord record);

    Iterable<LevelObjectRecord> findAll();

    Optional<LevelObjectRecord> findById(int id);
}
