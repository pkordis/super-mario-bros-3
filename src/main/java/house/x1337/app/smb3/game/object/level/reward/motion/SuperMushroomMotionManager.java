package house.x1337.app.smb3.game.object.level.reward.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.reward.RewardLevelObjectMotionManager;
import house.x1337.app.smb3.game.object.level.reward.SuperMushroom;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class SuperMushroomMotionManager implements RewardLevelObjectMotionManager<SuperMushroom> {
    private final Class<SuperMushroom> type = SuperMushroom.class;
    private final List<SuperMushroom> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
}
