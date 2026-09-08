package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.game.object.level.LevelObjectType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.ONE_UP_MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SUPER_MUSHROOM;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SuperMushroomType {
    GROWING_CAPABLE(SUPER_MUSHROOM),
    LIFE_AWARDING_GREEN(ONE_UP_MUSHROOM);

    private final LevelObjectType asLevelObjectType;
}
