package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.game.engine.GameEngine;
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
    private final GameEngine gameEngine;
    private final Offset offset;

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {

    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        // Giant brick blocks do not respond to the tail attack.
    }
}
