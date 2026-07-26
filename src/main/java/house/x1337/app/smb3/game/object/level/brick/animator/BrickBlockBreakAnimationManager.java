package house.x1337.app.smb3.game.object.level.brick.animator;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimationManager;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Singleton that owns every in-flight brick animation.
 * Call {@link #update()} once per game-tick from {@code GameEngine.simpleUpdate()}.
 */
@Singleton
@RequiredArgsConstructor
public final class BrickBlockBreakAnimationManager implements AnimationManager {
    private final List<BrickBlockAnimation> active = new ArrayList<>();
    private final BrickBlockAnimator brickBlockAnimator;

    /**
     * Advances all active brick animations and the tile shimmer animator by one game-tick.
     * Removes completed animations.
     */
    @Override
    public void update() {
        brickBlockAnimator.tick();
        final Iterator<BrickBlockAnimation> it = active.iterator();
        while (it.hasNext()) {
            final BrickBlockAnimation anim = it.next();
            anim.tick();
            if (anim.isExpired()) {
                anim.detach();
                it.remove();
            }
        }
    }

    public void spawnBreak(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        for (final BrickBlockAnimation existing : active) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        active.add(new BrickBlockAnimation(gameEngine, offset));
    }
}
