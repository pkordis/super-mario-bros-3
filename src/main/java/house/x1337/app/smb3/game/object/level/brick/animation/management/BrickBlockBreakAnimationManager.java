package house.x1337.app.smb3.game.object.level.brick.animation.management;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimationManager;
import house.x1337.app.smb3.game.object.level.brick.animation.BrickBlockBounceAnimation;
import house.x1337.app.smb3.game.object.level.brick.animation.BrickBlockBreakAnimation;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Singleton that owns every in-flight brick animation (both break and bounce).
 * Call {@link #update()} once per game-tick from {@code GameEngine.simpleUpdate()}.
 */
@Singleton
@RequiredArgsConstructor
public final class BrickBlockBreakAnimationManager implements AnimationManager {
    private final List<BrickBlockBreakAnimation> activeBreaks = new ArrayList<>();
    private final List<BrickBlockBounceAnimation> activeBounces = new ArrayList<>();
    private final BrickBlockAnimator brickBlockAnimator;

    /**
     * Advances all active brick animations (breaks and bounces) and the tile shimmer
     * animator by one game-tick. Removes completed animations.
     */
    @Override
    public void update() {
        brickBlockAnimator.update();

        // Update break animations
        final Iterator<BrickBlockBreakAnimation> breakIterator = activeBreaks.iterator();
        while (breakIterator.hasNext()) {
            final BrickBlockBreakAnimation anim = breakIterator.next();
            anim.tick();
            if (anim.isExpired()) {
                anim.detach();
                breakIterator.remove();
            }
        }

        // Update bounce animations
        final Iterator<BrickBlockBounceAnimation> bounceIterator = activeBounces.iterator();
        while (bounceIterator.hasNext()) {
            final BrickBlockBounceAnimation anim = bounceIterator.next();
            anim.tick();
            if (anim.isExpired()) {
                anim.detach();
                bounceIterator.remove();
            }
        }
    }

    /**
     * Spawns a brick-break animation (four flying fragments) for large Mario.
     *
     * @param gameEngine the game engine
     * @param offset     the tile offset where the brick was hit
     */
    public void spawnBreak(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        for (final BrickBlockBreakAnimation existing : activeBreaks) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activeBreaks.add(new BrickBlockBreakAnimation(gameEngine, offset));
    }

    /**
     * Spawns a brick-bounce animation (10-frame Y displacement) for small Mario.
     *
     * <p>Ported from dasm {@code prg001.asm ObjNorm_BounceDU}: when small Mario hits a brick
     * from below, the brick bounces in place using the {@code Bouncer_PUpVel} velocity table
     * over 10 frames, then returns to rest. The brick is not destroyed.
     *
     * @param gameEngine the game engine
     * @param offset     the tile offset where the brick was hit
     */
    public void spawnBounce(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        // Don't spawn if a bounce is already active at this tile
        for (final BrickBlockBounceAnimation existing : activeBounces) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activeBounces.add(new BrickBlockBounceAnimation(gameEngine, offset, brickBlockAnimator));
    }
}
