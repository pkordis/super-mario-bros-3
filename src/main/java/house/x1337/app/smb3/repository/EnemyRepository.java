package house.x1337.app.smb3.repository;

import house.x1337.app.smb3.model.repository.EnemyRecord;

import java.util.Optional;

public interface EnemyRepository {
    void upsert(EnemyRecord record);
    Iterable<EnemyRecord> findAll();
    Optional<EnemyRecord> findById(String id);
}
