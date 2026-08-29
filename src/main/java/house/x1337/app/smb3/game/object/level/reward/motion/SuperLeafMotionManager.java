package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
@RequiredArgsConstructor
public final class SuperLeafMotionManager implements MotionManager {
    private final List<SuperLeaf> activeLeaves = new ArrayList<>();

    @Override
    public void update() {
        final Iterator<SuperLeaf> iterator = activeLeaves.iterator();
        while (iterator.hasNext()) {
            final SuperLeaf leaf = iterator.next();
            leaf.tick();
            if (!leaf.isExpired()) {
                final LevelScenePlayer collidingPlayer = leaf.findCollidingPlayer();
                if (collidingPlayer != null) {
                    leaf.onCollisionWith(collidingPlayer);
                }
            }
            if (leaf.isExpired()) {
                leaf.detach();
                iterator.remove();
            }
        }
    }

    public void spawnLeaf(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        activeLeaves.add(getBean(SuperLeaf.class, gameEngine, offset));
    }
}
