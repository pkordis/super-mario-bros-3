package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.object.level.block.EmptyBlock;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.repository.LevelObjectRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.EMPTY_BLOCK;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LevelObjectService {
    private final Map<LevelObjectTypeSingleTiled, Integer> singleTiledLevelObjectIdsByType = new HashMap<>();
    private final Map<Integer, LevelObjectRecord> cache = new HashMap<>();
    private final LevelObjectRepository levelObjectRepository;

    @PostConstruct
    public void initCache() {
        levelObjectRepository.findAll().forEach(record -> cache.put(record.getId(), record));
        log.info("Level object cache initialised with {} entries.", cache.size());
    }

    public Optional<LevelObjectRecord> findByTileId(final int tileId) {
        return Optional.ofNullable(cache.get(tileId));
    }

    /**
     * Returns a map from tile id to {@link LevelObjectRecord} for every id in
     * {@code tileIds} that has a record in the cache. Ids with no cached record
     * are silently omitted from the result.
     */
    public Map<Integer, LevelObjectRecord> findAllByIds(final Set<Integer> tileIds) {
        final Map<Integer, LevelObjectRecord> result = new HashMap<>();
        for (final int id : tileIds) {
            final LevelObjectRecord record = cache.get(id);
            if (record != null) {
                result.put(id, record);
            }
        }
        return result;
    }

    /**
     * Returns the subset of {@code tileIds} that have no {@link LevelObjectRecord} in the cache,
     * or whose cached record has a {@code null} type — i.e. tiles that still need classification.
     */
    public Set<Integer> getUnclassifiedIds(final Collection<Integer> tileIds) {
        return tileIds
            .stream()
            .filter(id -> {
                final LevelObjectRecord record = cache.get(id);
                return record == null || record.getType() == null;
            })
            .collect(Collectors.toUnmodifiableSet());
    }

    public void upsert(final LevelObjectRecord record) {
        levelObjectRepository.upsert(record);
        cache.put(record.getId(), record);
        log.debug(
            "Upserted level object (id={}, type={}).",
            record.getId(),
            record.getType()
        );
    }

    public Optional<Integer> getLevelObjectIdOfType(
        final LevelObjectTypeSingleTiled levelObjectTypeSingleTiled
    ) {
        final Integer id = singleTiledLevelObjectIdsByType.get(levelObjectTypeSingleTiled);
        if (id != null) {
            return Optional.of(id);
        }
        final String type = EMPTY_BLOCK.name();
        final Integer matchedId = cache
            .entrySet()
            .stream()
            .filter(e -> type.equals(e.getValue().getType()))
            .findFirst()
            .map(Map.Entry::getKey)
            .orElseThrow();
        singleTiledLevelObjectIdsByType.put(levelObjectTypeSingleTiled, matchedId);
        return Optional.of(matchedId);
    }

    public EmptyBlock createEmptyBlock(final Offset offset) {
        final Optional<Integer> emptyBlockId = getLevelObjectIdOfType(EMPTY_BLOCK);
        return (EmptyBlock) cache
            .get(emptyBlockId.orElseThrow())
            .toLevelObject(offset);
    }
}
