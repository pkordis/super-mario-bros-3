package house.x1337.app.smb3.game.object.level.brick.animation.management;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.brick.BrickBlock;
import house.x1337.app.smb3.game.object.level.brick.BrickBlockWithoutReward;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Getter
@Singleton
public final class BrickBlockAnimator extends GameObjectAnimatorSingleTiled<BrickBlock> {
    private final List<Class<? extends BrickBlock>> supportedTypes = List.of(
        BrickBlockWithoutReward.class
    );
    private final int ticksPerFrame = 8;

    @Value("classpath:/sprites/object/brick/plain/frame_{0,3}.png")
    private AnimationImageResource animationFrames;
}
