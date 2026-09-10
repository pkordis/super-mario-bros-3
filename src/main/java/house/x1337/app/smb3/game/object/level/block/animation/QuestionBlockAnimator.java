package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_SWITCH_BLOCK_SPAWNER;

@Getter
@Singleton
public final class QuestionBlockAnimator extends GameObjectAnimatorSingleTiled<Block> {
    private final List<LevelObjectType> supportedTypes = List.of(
        QUESTION_BLOCK,
        QUESTION_SWITCH_BLOCK_SPAWNER
    );
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/block/question/frame_{0,3}.png")
    private AnimationImageResource animationFrames;
}
