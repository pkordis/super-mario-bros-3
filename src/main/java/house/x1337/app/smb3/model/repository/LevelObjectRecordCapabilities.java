package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
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
     * Instantiates a fresh {@link LevelObject} for this record, drawn as the record's own tile. See
     * {@link #toLevelObject(GameEngine, Coordinates, Tile)}.
     */
    default LevelObject toLevelObject(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        final LevelObjectRecord record = (LevelObjectRecord) this;
        final Tile ownTile = resolveLevelObjectType().isSingleTiled()
            ? getBean(TileService.class).findById(record.getId()).orElse(null)
            : null;
        return toLevelObject(gameEngine, offset, ownTile);
    }

    /**
     * Instantiates a fresh {@link LevelObject} for this record. A single-tiled object is drawn with the pixels of
     * {@code tile}, the tile painted in its cell, so every tile linked to the record keeps its own look. A
     * multi-tiled one ignores it: its sprites come from its own assets.
     *
     * @throws IllegalArgumentException if the type string cannot be resolved to either enum.
     */
    default LevelObject toLevelObject(
        final GameEngine gameEngine,
        final Coordinates offset,
        final Tile tile
    ) {
        final LevelObjectType type = resolveLevelObjectType();
        if (type.isSingleTiled()) {
            final ImageResource imageResource = tile == null
                ? null
                : fromData(tile.getArgbData(), TILE_SIZE, TILE_SIZE);
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
