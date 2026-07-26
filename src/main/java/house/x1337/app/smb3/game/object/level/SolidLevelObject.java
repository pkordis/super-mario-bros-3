package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.TileType.Category.ONE_WAY_PLATFORM;

/**
 * Wraps a {@link Tile} as a {@link LevelObject} for {@link house.x1337.app.smb3.enumeration.TileType.Category#COLLIDING}
 * tiles that have no {@link house.x1337.app.smb3.model.repository.LevelObjectRecord} in the repository
 * (e.g. solid ground, pipes). Collision behavior is derived directly from the tile's
 * {@link house.x1337.app.smb3.enumeration.TileType}.
 */
@Getter
@Builder
@RequiredArgsConstructor
public final class SolidLevelObject implements LevelObject {
    private final Tile tile;
    private final LevelObjectType type;
    private final Offset offset;

    @Override
    public boolean isOneWayPlatform() {
        return tile.getType().getCategory() == ONE_WAY_PLATFORM;
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
    }
}
