package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.block.Block;
import house.x1337.app.smb3.game.object.level.block.animation.BrickBlockBounceAnimation;
import house.x1337.app.smb3.game.object.level.block.animation.BrickBlockBreakAnimation;
import house.x1337.app.smb3.model.game.Offset;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Singleton
public final class BrickBlockBreakMotionManager implements MotionManager<Block> {
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

    public void spawnBounce(
        final GameEngine gameEngine,
        final Offset offset,
        final GameObjectAnimatorSingleTiled<?> animator
    ) {
        // Don't spawn if a bounce is already active at this tile
        for (final BrickBlockBounceAnimation existing : activeBounces) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activeBounces.add(new BrickBlockBounceAnimation(gameEngine, offset, animator));
    }

    @Override
    public boolean isBlockBumpActiveAt(final Offset cell) {
        for (final BrickBlockBounceAnimation bounce : activeBounces) {
            if (!bounce.isExpired() && bounce.getOffset().equals(cell)) {
                return true;
            }
        }
        return false;
    }
}
