package house.x1337.app.smb3.game.object.level.brick;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.BRICK_BLOCK;

@Getter
public class BrickBlock implements LevelObject {
    private final LevelObjectType type = BRICK_BLOCK;
}
