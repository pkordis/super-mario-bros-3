package house.x1337.app.smb3.game.object.level.block.animation.management;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimationManager;
import house.x1337.app.smb3.game.object.level.block.animation.EmptyBlockBounceAnimation;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.EMPTY_BLOCK;


@Singleton
@RequiredArgsConstructor
public final class EmptyBlockBounceAnimationManager implements AnimationManager {
    private final List<EmptyBlockBounceAnimation> activeBounces = new ArrayList<>();
    private final ImageResource emptyBlockTileResource = loadForLevelObjectType(EMPTY_BLOCK);

    @Override
    public void update() {
        final Iterator<EmptyBlockBounceAnimation> iterator = activeBounces.iterator();
        while (iterator.hasNext()) {
            final EmptyBlockBounceAnimation anim = iterator.next();
            anim.tick();
            if (anim.isExpired()) {
                anim.detach();
                iterator.remove();
            }
        }
    }

    public void spawnBounce(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        for (final EmptyBlockBounceAnimation existing : activeBounces) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activeBounces.add(new EmptyBlockBounceAnimation(
            gameEngine,
            offset,
            emptyBlockTileResource
        ));
    }
}
