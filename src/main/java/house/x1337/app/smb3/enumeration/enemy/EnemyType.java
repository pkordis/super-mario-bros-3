package house.x1337.app.smb3.enumeration.enemy;

import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PARA_GOOMBA;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.RED;

@Getter
@RequiredArgsConstructor
public enum EnemyType {
    GOOMBA_NORMAL("Goomba - Normal", GOOMBA, NORMAL),
    GOOMBA_RED("Goomba - Red", GOOMBA, RED),
    PARA_GOOMBA_NORMAL("Para-Goomba - Normal", PARA_GOOMBA, NORMAL),
    PARA_GOOMBA_RED("Para-Goomba - Red", PARA_GOOMBA, RED);

    private final String label;
    private final LevelObjectType levelObjectType;
    private final EnemyMode mode;
}
