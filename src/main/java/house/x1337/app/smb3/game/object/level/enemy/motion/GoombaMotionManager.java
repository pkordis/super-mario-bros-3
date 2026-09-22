package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.enemy.EnemyMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.Goomba;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Singleton
@RequiredArgsConstructor
public final class GoombaMotionManager implements EnemyMotionManager<Goomba> {
    private final Class<Goomba> type = Goomba.class;
    private final List<Goomba> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
}
