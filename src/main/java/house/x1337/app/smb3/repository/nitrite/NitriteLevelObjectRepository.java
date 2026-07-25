package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.repository.LevelObjectRepository;
import lombok.RequiredArgsConstructor;
import org.dizitart.no2.repository.ObjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static org.dizitart.no2.filters.FluentFilter.where;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "db.provider", havingValue = "NITRITE", matchIfMissing = true)
public class NitriteLevelObjectRepository implements LevelObjectRepository {

    private final ObjectRepository<LevelObjectRecord> levelObjectObjectRepository;

    @Override
    public void upsert(final LevelObjectRecord record) {
        levelObjectObjectRepository.update(record, true);
    }

    @Override
    public Iterable<LevelObjectRecord> findAll() {
        return levelObjectObjectRepository.find();
    }

    @Override
    public Optional<LevelObjectRecord> findById(final int id) {
        for (final LevelObjectRecord record : levelObjectObjectRepository.find(where("id").eq(id))) {
            return Optional.of(record);
        }
        return Optional.empty();
    }
}
