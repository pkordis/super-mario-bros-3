package house.x1337.app.smb3.enumeration.enemy;

import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_PARATROOPA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_TROOPA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PARA_GOOMBA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PIRANHA_PLANT;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.VENUS_FIRE_TRAP;

@Getter
@RequiredArgsConstructor
public enum EnemyType {
    GOOMBA_NORMAL("Goomba - Normal", GOOMBA, GoombaMode.NORMAL),
    GOOMBA_RED("Goomba - Red", GOOMBA, GoombaMode.RED),
    KOOPA_PARATROOPA_GREEN("Koopa Paratroopa - Green", KOOPA_PARATROOPA, KoopaTroopaMode.GREEN),
    KOOPA_PARATROOPA_RED("Koopa Paratroopa - Red", KOOPA_PARATROOPA, KoopaTroopaMode.RED),
    KOOPA_TROOPA_GREEN("Koopa Troopa - Green", KOOPA_TROOPA, KoopaTroopaMode.GREEN),
    KOOPA_TROOPA_RED("Koopa Troopa - Red", KOOPA_TROOPA, KoopaTroopaMode.RED),
    PARA_GOOMBA_NORMAL("Para-Goomba - Normal", PARA_GOOMBA, GoombaMode.NORMAL),
    PARA_GOOMBA_RED("Para-Goomba - Red", PARA_GOOMBA, GoombaMode.RED),
    PIRANHA_PLANT_GREEN("Piranha Plant - Green", PIRANHA_PLANT, PiranhaPlantMode.GREEN),
    PIRANHA_PLANT_RED("Piranha Plant - Red", PIRANHA_PLANT, PiranhaPlantMode.RED),
    VENUS_FIRE_TRAP_GREEN("Venus Fire Trap - Green", VENUS_FIRE_TRAP, PiranhaPlantMode.GREEN),
    VENUS_FIRE_TRAP_RED("Venus Fire Trap - Red", VENUS_FIRE_TRAP, PiranhaPlantMode.RED);

    private final String label;
    private final LevelObjectType levelObjectType;
    private final EnemyMode mode;
}
