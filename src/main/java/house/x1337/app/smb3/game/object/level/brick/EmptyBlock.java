package house.x1337.app.smb3.game.object.level.brick;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.EMPTY_BLOCK;

@Getter
@Prototype
@RequiredArgsConstructor
public class EmptyBlock implements LevelObject {
    private final LevelObjectType type = EMPTY_BLOCK;
    private final Offset offset;

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
    }
}
