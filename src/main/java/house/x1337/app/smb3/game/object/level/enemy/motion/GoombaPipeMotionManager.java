package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.enemy.GoombaPipe;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class GoombaPipeMotionManager implements EnemyMotionManager<GoombaPipe> {
    private final Class<GoombaPipe> type = GoombaPipe.class;
    private final List<GoombaPipe> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();

    @Override
    public boolean isExemptFromDeletionOffScreen(final GoombaPipe instance) {
        return true;
    }
}
