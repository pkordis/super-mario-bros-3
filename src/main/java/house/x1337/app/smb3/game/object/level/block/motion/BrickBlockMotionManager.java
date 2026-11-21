package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.animation.BrickBlockBounceAnimation;
import house.x1337.app.smb3.game.object.level.block.animation.BrickBlockBreakAnimation;
import house.x1337.app.smb3.game.object.level.block.animation.NoteBlockBounceAnimation;
import house.x1337.app.smb3.model.game.Coordinates;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
public final class BrickBlockMotionManager implements BlockMotionManager {
    private final List<BrickBlockBreakAnimation> activeBreaks = new ArrayList<>();
    private final List<BrickBlockBounceAnimation> activeBounces = new ArrayList<>();

    @Override
    public void update() {
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

        // Update bounce animations. A bounce that ends here stays listed until postCollision, so it still counts as
        // a bump for the whole tick whatever order the managers run in.
        for (final BrickBlockBounceAnimation anim : activeBounces) {
            if (anim.isExpired()) {
                continue;
            }
            anim.tick();
            if (anim.isExpired()) {
                anim.detach();
            }
        }
    }

    @Override
    public void postCollision() {
        activeBounces.removeIf(BrickBlockBounceAnimation::isExpired);
    }

    public void spawnBreak(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        for (final BrickBlockBreakAnimation existing : activeBreaks) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activeBreaks.add(getBean(BrickBlockBreakAnimation.class, gameEngine, offset));
    }

    public void spawnBounce(
        final GameEngine gameEngine,
        final Coordinates offset,
        final GameObjectAnimatorSingleTiled<?> animator
    ) {
        // Don't spawn if a bounce is already active at this tile
        for (final BrickBlockBounceAnimation existing : activeBounces) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        final BrickBlockBounceAnimation animation = getBean(BrickBlockBounceAnimation.class, gameEngine, offset);
        animation.configure(animator);
        activeBounces.add(animation);
    }

    @Override
    public boolean isBlockBumpActiveAt(final Coordinates cell) {
        for (final BrickBlockBounceAnimation bounce : activeBounces) {
            if (bounce.getOffset().equals(cell)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops any in-flight brick break or bounce animations when the level restarts, detaching each from
     * the scene before clearing so none lingers in the rebuilt level.
     */
    @Override
    public void reset() {
        activeBreaks.forEach(BrickBlockBreakAnimation::detach);
        activeBreaks.clear();
        activeBounces
            .stream()
            .filter(bounce -> !bounce.isExpired())
            .forEach(BrickBlockBounceAnimation::detach);
        activeBounces.clear();
    }
}
