package house.x1337.app.smb3.game.object.level.block.animation;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.QuestionBlock;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Getter
@Singleton
public final class QuestionBlockAnimator extends GameObjectAnimatorSingleTiled<QuestionBlock> {
    private final List<Class<? extends QuestionBlock>> supportedTypes = List.of(QuestionBlock.class);
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/block/question/frame_{0,3}.png")
    private AnimationImageResource animationFrames;
}
