package house.x1337.app.smb3.game.object.level.effect;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.ForemostSpriteOverlay;
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
public final class TailAttackFlashMotionManager implements AnimationOnlyMotionManager<TailAttackFlashAnimation> {
    private final ForemostSpriteOverlay foremostSpriteOverlay;
    private final List<TailAttackFlashAnimation> activeAnimations = new ArrayList<>();
    private final Class<TailAttackFlashAnimation> animationType = TailAttackFlashAnimation.class;

    @Value("classpath:/sprites/effect/wham/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    /**
     * Advances the flashes, then pushes the overlay scene's transforms to the renderer.
     *
     * <p>The flashes live outside {@code rootNode} — see {@link ForemostSpriteOverlay} — and the engine
     * only walks its own root, so nothing else would ever call {@code updateGeometricState} on them.
     */
    @Override
    public void update() {
        AnimationOnlyMotionManager.super.update();
        foremostSpriteOverlay.update();
    }

    @Override
    public void reset() {
        activeAnimations.forEach(TailAttackFlashAnimation::detach);
        activeAnimations.clear();
        foremostSpriteOverlay.reset();
    }
}
