package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.reward.Starman;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class StarmanMotionManager implements RewardLevelObjectMotionManager<Starman> {
    private final Class<Starman> type = Starman.class;
    private final List<Starman> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
}
