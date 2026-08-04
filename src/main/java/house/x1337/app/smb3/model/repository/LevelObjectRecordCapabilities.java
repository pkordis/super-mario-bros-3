package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ImageResource.fromData;

public sealed interface LevelObjectRecordCapabilities permits LevelObjectRecord {
    /**
     * Resolves the {@link LevelObjectType} from the record's type string and
     * instantiates a fresh {@link LevelObject} via the type's no-args constructor.
     * Resolution order: {@link LevelObjectTypeSingleTiled} → {@link LevelObjectTypeMultiTiled}.
     *
     * @throws IllegalArgumentException if the type string cannot be resolved to either enum.
     * @throws IllegalStateException if the resolved type's instance class cannot be instantiated.
     */
    default LevelObject toLevelObject(final Offset offset) {
        final LevelObjectRecord record = (LevelObjectRecord) this;
        final LevelObjectType type = resolveLevelObjectType(record.getType());
        if (type.isSingleTiled()) {
            final TileService tileService = getBean(TileService.class);
            final Optional<Tile> tile = tileService.findById(record.getId());
            ImageResource imageResource = null;

            if (tile.isPresent()) {
                imageResource = fromData(tile.get().getArgbData(), TILE_SIZE, TILE_SIZE);
            }
            return getBean(
                type.getInstanceType(),
                imageResource,
                offset
            );
        }
        return getBean(type.getInstanceType(), offset);
    }

    @NonNull
    private LevelObjectType resolveLevelObjectType(final String typeName) {
        LevelObjectType type = null;
        try {
            type = LevelObjectTypeSingleTiled.valueOf(typeName);
        } catch (final IllegalArgumentException ignored) {
            // not a single-tiled type - try multi-tiled below
        }
        if (type == null) {
            try {
                type = LevelObjectTypeMultiTiled.valueOf(typeName);
            } catch (final IllegalArgumentException ignored) {
                // not a multi-tiled type either
            }
        }
        if (type == null) {
            throw new IllegalArgumentException(
                "Cannot resolve LevelObjectType for type=\"" + typeName + "\""
            );
        }
        return type;
    }
}
