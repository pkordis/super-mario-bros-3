package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;

import java.util.Iterator;
import java.util.List;

public interface ActiveLevelObjectMotionManager<E extends ActiveLevelObject> extends MotionManager {
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
}
