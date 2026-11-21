package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.block.animation.EmptyBlockBounceAnimation;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.EMPTY_BLOCK;

@Singleton
@RequiredArgsConstructor
public final class EmptyBlockBounceMotionManager implements BlockMotionManager {
    private final List<EmptyBlockBounceAnimation> activeBounces = new ArrayList<>();
    private final ImageResource emptyBlockTileResource = loadForLevelObjectType(EMPTY_BLOCK);

    @Override
    public void update() {
        // A bounce that ends here stays listed until postCollision, so it still counts as a bump for the whole tick
        // whatever order the managers run in.
        for (final EmptyBlockBounceAnimation anim : activeBounces) {
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
        activeBounces.removeIf(EmptyBlockBounceAnimation::isExpired);
    }

    public void spawnBounce(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        for (final EmptyBlockBounceAnimation existing : activeBounces) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activeBounces.add(getBean(
            EmptyBlockBounceAnimation.class,
            gameEngine,
            offset,
            emptyBlockTileResource
        ));
    }

    @Override
    public boolean isBlockBumpActiveAt(final Coordinates cell) {
        for (final EmptyBlockBounceAnimation bounce : activeBounces) {
            if (bounce.getOffset().equals(cell)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops any in-flight empty-block bounce animations when the level restarts, detaching each from the
     * scene before clearing so none lingers in the rebuilt level.
     */
    @Override
    public void reset() {
        activeBounces
            .stream()
            .filter(bounce -> !bounce.isExpired())
            .forEach(EmptyBlockBounceAnimation::detach);
        activeBounces.clear();
    }
}
