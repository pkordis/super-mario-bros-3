package house.x1337.app.smb3.game.object.level.brick;

import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.BRICK_BLOCK;

@Getter
@RequiredArgsConstructor
public class GiantBrickBlock implements LevelObject {
    private final LevelObjectType type = BRICK_BLOCK;
    private final Offset offset;

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {

    }
}
