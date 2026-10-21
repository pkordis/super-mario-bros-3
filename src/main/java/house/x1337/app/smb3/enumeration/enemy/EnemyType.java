package house.x1337.app.smb3.enumeration.enemy;

import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_TROOPA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PARA_GOOMBA;

@Getter
@RequiredArgsConstructor
public enum EnemyType {
    GOOMBA_NORMAL("Goomba - Normal", GOOMBA, GoombaMode.NORMAL),
    GOOMBA_RED("Goomba - Red", GOOMBA, GoombaMode.RED),
    KOOPA_TROOPA_GREEN("Koopa Troopa - Green", KOOPA_TROOPA, KoopaTroopaMode.GREEN),
    KOOPA_TROOPA_RED("Koopa Troopa - Red", KOOPA_TROOPA, KoopaTroopaMode.RED),
    PARA_GOOMBA_NORMAL("Para-Goomba - Normal", PARA_GOOMBA, GoombaMode.NORMAL),
    PARA_GOOMBA_RED("Para-Goomba - Red", PARA_GOOMBA, GoombaMode.RED);

    private final String label;
    private final LevelObjectType levelObjectType;
    private final EnemyMode mode;
}
