package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ImageResource.fromData;

public sealed interface LevelObjectRecordCapabilities permits LevelObjectRecord {
    /**
     * Resolves this record's type string to its {@link LevelObjectType}, searching
     * {@link LevelObjectTypeSingleTiled} then {@link LevelObjectTypeMultiTiled}.
     *
     * <p>Empty for a tile that has not been classified yet ({@code type == null}) and for a type string
     * no longer present in either enum. Callers use this to decide <em>which world</em> a placement
     * belongs to before instantiating anything: a single-tiled type is a terrain cell the collision
     * grid owns, a multi-tiled one is a whole entity placed elsewhere (an enemy, spawned into the
     * active-object world).
     *
     * @return the resolved type, or empty if the record names none
     */
    default Optional<LevelObjectType> findLevelObjectType() {
        final String typeName = ((LevelObjectRecord) this).getType();
        if (typeName == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LevelObjectTypeSingleTiled.valueOf(typeName));
        } catch (final IllegalArgumentException ignored) {
            // not a single-tiled type - try multi-tiled below
        }
        try {
            return Optional.of(LevelObjectTypeMultiTiled.valueOf(typeName));
        } catch (final IllegalArgumentException ignored) {
            // not a multi-tiled type either
            return Optional.empty();
        }
    }

    /**
     * Instantiates a fresh {@link LevelObject} for this record. A single-tiled object is handed the
     * painted tile's image — its appearance <em>is</em> that tile — while a multi-tiled one is not,
     * because it is a whole entity whose sprites come from its own assets.
     *
     * @throws IllegalArgumentException if the type string cannot be resolved to either enum.
     */
    default LevelObject toLevelObject(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        final LevelObjectRecord record = (LevelObjectRecord) this;
        final LevelObjectType type = resolveLevelObjectType();
        if (type.isSingleTiled()) {
            final TileService tileService = getBean(TileService.class);
            final Optional<Tile> tile = tileService.findById(record.getId());
            ImageResource imageResource = null;

            if (tile.isPresent()) {
                imageResource = fromData(tile.get().getArgbData(), TILE_SIZE, TILE_SIZE);
            }
            final LevelObject newLevelObject = getBean(
                type.getInstanceType(),
                gameEngine,
                imageResource,
                offset
            );
            enrichData(type, newLevelObject);
            return newLevelObject;
        }
        return getBean(type.getInstanceType(), gameEngine, offset);
    }

    @NonNull
    private LevelObjectType resolveLevelObjectType() {
        return findLevelObjectType().orElseThrow(() -> new IllegalArgumentException(
            "Cannot resolve LevelObjectType for type=\"" + ((LevelObjectRecord) this).getType() + "\""
        ));
    }

    private void enrichData(final LevelObjectType type, final LevelObject newLevelObject) {
        final Map<String, Object> thatData = ((LevelObjectRecord) this).getData();
        if (newLevelObject instanceof Block) {
           thatData.put("blockType", type.toString());
        }
    }
}
