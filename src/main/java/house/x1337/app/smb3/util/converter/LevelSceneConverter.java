package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.util.normalizer.LevelSceneRecordNormalizer;
import house.x1337.app.smb3.util.provider.TilesProvider;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.*;

public interface LevelSceneConverter extends LevelSceneLayerConverter, LevelSceneRecordNormalizer {
    TilesProvider getTilesProvider();

    default LevelScene toLevelScene(final LevelSceneRecord levelSceneRecord) {
        return LevelScene.builder()
            .id(levelSceneRecord.getId())
            .title(levelSceneRecord.getTitle())
            .description(levelSceneRecord.getDescription())
            .dimensions(new LevelSceneDimensions(levelSceneRecord.getColumns(), levelSceneRecord.getRows()))
            .updatedAt(levelSceneRecord.getUpdatedAt())
            .renderingStarterRow(levelSceneRecord.getRenderingStarterRow())
            .renderingStarterColumn(levelSceneRecord.getRenderingStarterColumn())
            .spawnPointRow(levelSceneRecord.getSpawnPointRow())
            .spawnPointColumn(levelSceneRecord.getSpawnPointColumn())
            .airLayer(toLevelSceneLayer(AIR, levelSceneRecord))
            .airDecorationsLayer(toLevelSceneLayer(DECORATIONS_AIR, levelSceneRecord))
            .landDecorationsLayer(toLevelSceneLayer(DECORATIONS_LAND, levelSceneRecord))
            .staticEnvironmentLayer(toLevelSceneLayer(STATIC_ENVIRONMENT, levelSceneRecord))
            .interactiveObjectsLayer(toLevelSceneLayer(INTERACTIVE_OBJECTS, levelSceneRecord))
            .nonPlayableCharactersLayer(toLevelSceneLayer(NON_PLAYABLE_CHARACTERS, levelSceneRecord))
            .build();
    }

    default LevelSceneRecord toLevelSceneRecord(final LevelScene levelScene) {
        final LevelSceneRecord.LevelSceneRecordBuilder builder = LevelSceneRecord.builder()
            .id(levelScene.getId())
            .title(levelScene.getTitle())
            .description(levelScene.getDescription())
            .rows(levelScene.getDimensions().rows())
            .columns(levelScene.getDimensions().columns())
            .updatedAt(System.currentTimeMillis())
            .renderingStarterRow(levelScene.getRenderingStarterRow())
            .renderingStarterColumn(levelScene.getRenderingStarterColumn())
            .spawnPointRow(levelScene.getSpawnPointRow())
            .spawnPointColumn(levelScene.getSpawnPointColumn());
        for (final LevelSceneLayerType type : LevelSceneLayerType.values()) {
            builder.layer(type.name(), toLayerData(type, levelScene.getLayer(type)));
        }
        return normalize(builder.build());
    }
}
