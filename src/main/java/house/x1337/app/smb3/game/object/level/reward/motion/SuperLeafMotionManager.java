package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class SuperLeafMotionManager implements RewardLevelObjectMotionManager<SuperLeaf> {
    private final Class<SuperLeaf> type = SuperLeaf.class;
    private final List<SuperLeaf> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
}
