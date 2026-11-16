package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.EmptyBlock;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.LevelObjectRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.EMPTY_BLOCK;
import static java.util.Comparator.comparing;

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

    /** The record a tile stands for: the one it is linked to, or else the one with the tile's own id. */
    public Optional<LevelObjectRecord> findByTile(final Tile tile) {
        final Integer linkedId = tile.getLevelObjectId();
        final LevelObjectRecord linked = linkedId == null ? null : cache.get(linkedId);
        return Optional.ofNullable(linked != null ? linked : cache.get(tile.getId()));
    }

    /** @return the record of every tile that has one, by tile id */
    public Map<Integer, LevelObjectRecord> findAllByTiles(final Collection<Tile> tiles) {
        final Map<Integer, LevelObjectRecord> result = new HashMap<>();
        for (final Tile tile : tiles) {
            findByTile(tile).ifPresent(record -> result.put(tile.getId(), record));
        }
        return result;
    }

    /** @return the ids of the tiles with no record, or with a record of no type */
    public Set<Integer> getUnclassifiedIds(final Collection<Tile> tiles) {
        return tiles
            .stream()
            .filter(tile -> findByTile(tile).map(LevelObjectRecord::getType).isEmpty())
            .map(Tile::getId)
            .collect(Collectors.toUnmodifiableSet());
    }

    public List<LevelObjectRecord> getSingleTiledLevelObjects() {
        return cache
            .values()
            .stream()
            .filter(record -> record.findLevelObjectType().filter(LevelObjectType::isSingleTiled).isPresent())
            .sorted(comparing((LevelObjectRecord record) -> record.findLevelObjectType().orElseThrow().getLabel())
                .thenComparing(LevelObjectRecord::getId))
            .toList();
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

    public EmptyBlock createEmptyBlock(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        final Optional<Integer> emptyBlockId = getLevelObjectIdOfType(EMPTY_BLOCK);
        return (EmptyBlock) cache
            .get(emptyBlockId.orElseThrow())
            .toLevelObject(gameEngine, offset);
    }
}
