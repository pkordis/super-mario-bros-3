package house.x1337.app.smb3.game.object.level.effect;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.AnimationOnlyMotionManager;
import house.x1337.app.smb3.model.AnimationImageResource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class PoofMotionManager implements AnimationOnlyMotionManager<PoofAnimation> {
    private final List<PoofAnimation> activeAnimations = new ArrayList<>();
    private final Class<PoofAnimation> animationType = PoofAnimation.class;

    @Value("classpath:/sprites/effect/poof/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

}
