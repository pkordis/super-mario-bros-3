package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.ItemType;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.service.LevelObjectData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.enumeration.ItemType.COIN_SINGLE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class QuestionBlock implements LevelObject, AnimatableLevelObject {
    private final LevelObjectType type = QUESTION_BLOCK;
    private final Offset offset;
    private ItemType reward;

    @Override
    public void configure(final LevelObjectData data) {
        reward = data
            .getEnum(ItemType.class, "reward")
            .orElse(COIN_SINGLE);
        log.debug(
            "Question block at {}x{}, configured with reward {}",
            offset.x(),
            offset.y(),
            reward
        );
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {

    }
}
