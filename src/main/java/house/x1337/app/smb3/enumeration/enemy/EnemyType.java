package house.x1337.app.smb3.enumeration.enemy;

import house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnemyType {
    GOOMBA("Goomba - Normal", LevelObjectTypeMultiTiled.GOOMBA);

    private final String label;
    private final LevelObjectType levelObjectType;
}
