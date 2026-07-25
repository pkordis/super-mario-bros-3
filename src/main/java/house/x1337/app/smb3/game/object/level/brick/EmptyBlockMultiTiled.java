package house.x1337.app.smb3.game.object.level.brick;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.EMPTY_BLOCK;

@Getter
public class EmptyBlockMultiTiled implements LevelObject {
    private final LevelObjectType type = EMPTY_BLOCK;
}
