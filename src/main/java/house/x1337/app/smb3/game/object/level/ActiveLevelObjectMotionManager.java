package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

import java.util.Iterator;
import java.util.List;

public interface ActiveLevelObjectMotionManager<E extends ActiveLevelObject> extends MotionManager {
    int DELETE_PAST_LEFT_EDGE_PIXELS = 48;
    int DELETE_PAST_RIGHT_EDGE_PIXELS = 32;
    int SPAWN_LOOK_AHEAD_LEFT_PIXELS = 32;
    int SPAWN_LOOK_AHEAD_RIGHT_PIXELS = 16;

    List<E> getActiveInstances();
    List<ScorePopupAnimation> getActiveScorePopups();
    Class<E> getType();

    default void tickScorePopups() {
        final Iterator<ScorePopupAnimation> iterator = getActiveScorePopups().iterator();
        while (iterator.hasNext()) {
            final ScorePopupAnimation scorePopup = iterator.next();
            scorePopup.tick();
            if (scorePopup.isExpired()) {
                scorePopup.detach();
                iterator.remove();
            }
        }
    }

    default boolean isPastTheDeletionEdge(
        final ActiveLevelObject instance,
        final AxisAlignedBoundingBox screen
    ) {
        final double x = instance.getPosition().x();
        return x <= screen.left() - DELETE_PAST_LEFT_EDGE_PIXELS
            || x > screen.right() + DELETE_PAST_RIGHT_EDGE_PIXELS;
    }
}
