package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.DUMMY_SOLID_OBJECT;

/**
 * Wraps a {@link Tile} as a {@link LevelObject} for {@link house.x1337.app.smb3.enumeration.TileType.Category#COLLIDING}
 * tiles that have no {@link house.x1337.app.smb3.model.repository.LevelObjectRecord} in the repository
 * (e.g. solid ground, pipes). Collision behavior is derived directly from the tile's
 * {@link house.x1337.app.smb3.enumeration.TileType}.
 */
@Getter
@Builder
@Prototype
@RequiredArgsConstructor
public final class SolidLevelObject implements LevelObject {
    private final LevelObjectType type = DUMMY_SOLID_OBJECT;
    private final GameEngine gameEngine;
    private final ImageResource imageResource;
    private final Coordinates offset;
}
