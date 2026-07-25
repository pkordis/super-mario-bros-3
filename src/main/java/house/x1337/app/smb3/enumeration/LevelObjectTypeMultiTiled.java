package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.brick.BrickBlock;
import house.x1337.app.smb3.game.object.level.brick.EmptyBlockMultiTiled;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LevelObjectTypeMultiTiled implements LevelObjectType {
    BRICK_BLOCK("Brick Block (breakable)", BrickBlock.class),
    EMPTY_BLOCK("Empty Block (solid/used)", EmptyBlockMultiTiled.class);

    private final boolean singleTiled = false;
    private final String label;
    private final Class<? extends LevelObject> instanceType;
}
