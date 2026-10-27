package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.enemy.ParaGoomba;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the Para-Goomba population, exactly as {@link GoombaMotionManager} drives the ground
 * Goombas: the interface supplies the whole per-tick pass, so nothing is needed here but the lists.
 *
 * <p>The animator's per-mode texture cache is cleared per level by
 * {@code EnemySpawner#resetEnemies}, which resets every {@code EnemyAnimator} through the registry -
 * so there is deliberately no {@code reset()} override here either.
 */
@Getter
@Singleton
@RequiredArgsConstructor
public final class ParaGoombaMotionManager implements EnemyMotionManager<ParaGoomba> {
    private final Class<ParaGoomba> type = ParaGoomba.class;
    private final List<ParaGoomba> activeInstances = new ArrayList<>();
    private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
}
