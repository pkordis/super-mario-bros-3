package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.model.repository.EnemyRecord;
import house.x1337.app.smb3.repository.EnemyRepository;
import lombok.RequiredArgsConstructor;
import org.dizitart.no2.repository.ObjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static org.dizitart.no2.filters.FluentFilter.where;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitriteEnemyRepository implements EnemyRepository {
    private final ObjectRepository<EnemyRecord> enemyObjectRepository;

    @Override
    public void upsert(final EnemyRecord record) {
        enemyObjectRepository.update(record, true);
    }

    @Override
    public Iterable<EnemyRecord> findAll() {
        return enemyObjectRepository.find();
    }

    @Override
    public Optional<EnemyRecord> findById(final String id) {
        for (final EnemyRecord record : enemyObjectRepository.find(where("id").eq(id))) {
            return Optional.of(record);
        }
        return Optional.empty();
    }
}
