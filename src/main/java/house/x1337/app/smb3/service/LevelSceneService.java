package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord.LevelSceneLayerData;
import house.x1337.app.smb3.model.service.TileMoveSummary;
import house.x1337.app.smb3.repository.LevelSceneRepository;
import house.x1337.app.smb3.util.converter.LevelSceneConverter;
import house.x1337.app.smb3.util.provider.TilesProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static java.lang.System.currentTimeMillis;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LevelSceneService implements LevelSceneConverter {
    private final LevelSceneRepository levelSceneRepository;
    private final TileService tileService;
    private final PipePortalService pipePortalService;

    public void save(final LevelScene levelScene) {
        final LevelSceneRecord levelSceneRecord = normalize(toLevelSceneRecord(levelScene));
        levelSceneRecord.setUpdatedAt(currentTimeMillis());
        levelSceneRepository.upsert(levelSceneRecord);
        pipePortalService.replaceAll(levelSceneRecord.getId(), levelScene.getPipePortals());
        log.info(
            "Saved scene: id={}, title={}, updatedAt={}",
            levelSceneRecord.getId(),
            levelSceneRecord.getTitle(),
            levelSceneRecord.getUpdatedAt()
        );
    }

    public Optional<LevelScene> findLastEdited() {
        final Optional<LevelSceneRecord> record = levelSceneRepository.findLastEdited();
        return record.map(levelSceneRecord -> {
            final LevelScene levelScene = withPipePortals(toLevelScene(levelSceneRecord));
            log.info(
                "Loaded last edited scene: id={}, title={}, updatedAt={}",
                levelScene.getId(),
                levelScene.getTitle(),
                levelScene.getUpdatedAt()
            );
            return levelScene;
        });
    }

    public List<LevelScene> findPage(final int offset, final int pageSize) {
        return levelSceneRepository.findPage(offset, pageSize)
            .stream()
            .map(this::toLevelScene)
            .map(this::withPipePortals)
            .toList();
    }

    public Optional<LevelScene> findById(final String id) {
        return levelSceneRepository.findById(id)
            .map(this::toLevelScene)
            .map(this::withPipePortals);
    }

    public Map<String, String> findTitlesById() {
        return levelSceneRepository.findTitlesById();
    }

    public void delete(final String id) {
        levelSceneRepository.delete(id);
        pipePortalService.deleteByLevelSceneId(id);
        log.info("Deleted scene: id={}", id);
    }

    public TileMoveSummary countTileCellsOutside(final int tileId, final LevelSceneLayerType target) {
        return relocateTile(tileId, target, false);
    }

    /**
     * In every stored scene, moves each cell holding the tile in a layer other than {@code target} into the same
     * cell of {@code target}, overwriting what that cell held, and clears it where it was. A changed scene is saved
     * with its {@code updatedAt} untouched, so the scene the editor reopens at start stays the same.
     */
    public TileMoveSummary moveTile(final int tileId, final LevelSceneLayerType target) {
        final TileMoveSummary summary = relocateTile(tileId, target, true);
        log.info("Moved tile {} to the {} layer: {}", tileId, target, summary);
        return summary;
    }

    private TileMoveSummary relocateTile(final int tileId, final LevelSceneLayerType target, final boolean save) {
        int scenes = 0;
        int cells = 0;
        for (final String id : levelSceneRepository.findTitlesById().keySet()) {
            final Optional<LevelSceneRecord> stored = levelSceneRepository.findById(id);
            if (stored.isEmpty()) {
                continue;
            }
            // The normalized copy has a full array per layer, and arrays of its own, so counting changes nothing
            final LevelSceneRecord record = normalize(stored.get());
            final int moved = relocateTileIn(record, tileId, target);
            if (moved > 0) {
                scenes++;
                cells += moved;
                if (save) {
                    levelSceneRepository.upsert(record);
                }
            }
        }
        return new TileMoveSummary(scenes, cells);
    }

    private int relocateTileIn(final LevelSceneRecord record, final int tileId, final LevelSceneLayerType target) {
        final int[] targetIds = record.getLayers().get(target.name()).getTileIds();
        int moved = 0;
        for (final LevelSceneLayerData layer : record.getLayers().values()) {
            if (layer.getType() == target) {
                continue;
            }
            final int[] tileIds = layer.getTileIds();
            for (int index = 0; index < tileIds.length; index++) {
                if (tileIds[index] == tileId) {
                    tileIds[index] = NULL_TILE.getId();
                    targetIds[index] = tileId;
                    moved++;
                }
            }
        }
        return moved;
    }

    @Override
    public TilesProvider getTilesProvider() {
        return tileService;
    }

    private LevelScene withPipePortals(final LevelScene levelScene) {
        levelScene.setPipePortals(pipePortalService.findByLevelSceneId(levelScene.getId()));
        return levelScene;
    }
}
