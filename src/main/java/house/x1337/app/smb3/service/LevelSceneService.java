package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.repository.LevelSceneRepository;
import house.x1337.app.smb3.util.converter.LevelSceneConverter;
import house.x1337.app.smb3.util.provider.TilesProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import static java.lang.System.currentTimeMillis;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LevelSceneService implements LevelSceneConverter {
    private final LevelSceneRepository levelSceneRepository;
    private final TileService tileService;

    public void save(final LevelScene levelScene) {
        final LevelSceneRecord levelSceneRecord = normalize(toLevelSceneRecord(levelScene));
        levelSceneRecord.setUpdatedAt(currentTimeMillis());
        levelSceneRepository.upsert(levelSceneRecord);
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
            final LevelScene levelScene = toLevelScene(levelSceneRecord);
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
            .toList();
    }

    public void delete(final String id) {
        levelSceneRepository.delete(id);
        log.info("Deleted scene: id={}", id);
    }

    @Override
    public TilesProvider getTilesProvider() {
        return tileService;
    }
}
