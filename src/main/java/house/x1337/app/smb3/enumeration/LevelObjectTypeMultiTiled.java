package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.brick.GiantBrickBlock;
import house.x1337.app.smb3.game.object.level.brick.GiantEmptyBlock;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LevelObjectTypeMultiTiled implements LevelObjectType {
    BRICK_BLOCK("Brick Block (breakable)", GiantBrickBlock.class),
    EMPTY_BLOCK("Empty Block (solid/used)", GiantEmptyBlock.class);

    private final boolean singleTiled = false;
    private final String label;
    private final Class<? extends LevelObject> instanceType;
}
