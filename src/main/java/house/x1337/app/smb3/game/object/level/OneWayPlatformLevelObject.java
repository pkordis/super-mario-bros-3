package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.DUMMY_SOLID_OBJECT;

/**
 * A jump-through tile with no level object of its own, such as a walkable panel top: solid to whatever lands on it,
 * thin air to heads and sides.
 */
@Getter
@RequiredArgsConstructor
public final class OneWayPlatformLevelObject implements LevelObject {
    private final LevelObjectType type = DUMMY_SOLID_OBJECT;
    private final GameEngine gameEngine;
    private final Coordinates offset;

    @Override
    public boolean isOneWayPlatform() {
        return true;
    }
}
