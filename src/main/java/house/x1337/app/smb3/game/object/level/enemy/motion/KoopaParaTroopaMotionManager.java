package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopa;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class KoopaParaTroopaMotionManager implements EnemyMotionManager<KoopaParaTroopa> {
    private final Class<KoopaParaTroopa> type = KoopaParaTroopa.class;
    private final List<KoopaParaTroopa> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
}
