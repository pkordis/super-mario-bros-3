package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;

import java.util.List;

import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A sliding player against the enemies: the stompable ones die, a winged one off the ground and an unstompable one
 * hurt the player.
 */
@TestInstance(PER_CLASS)
class EnemySlideKillTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private LevelScenePlayer player;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(16, 8));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(mock(StaticEnvironmentCollisionGrid.class));
        when(gameEngine.getActiveObjectGrid()).thenReturn(mock(ActiveObjectGrid.class));

        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(2);
        final KoopaTroopaAnimator koopaTroopaAnimator = mock(KoopaTroopaAnimator.class);
        when(koopaTroopaAnimator.walkFrameCount(any())).thenReturn(2);
        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(2);
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == Goomba.class) {
                final Goomba goomba = new Goomba(invocation.getArgument(1), invocation.getArgument(2));
                goomba.init();
                return goomba;
            }
            if (type == GoombaAnimator.class) {
                return goombaAnimator;
            }
            if (type == KoopaTroopaAnimator.class) {
                return koopaTroopaAnimator;
            }
            if (type == ParaGoombaAnimator.class) {
                return paraGoombaAnimator;
            }
            return mock(type);
        });

        final PlayerPosition position = new PlayerPosition();
        position.setDX(3.0);
        player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.isSliding()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A Goomba slid into is knocked away dead for 100 points")
    void aGoombaDies() {
        // Prepare
        final Goomba goomba = new Goomba(gameEngine, of(5, 5));
        goomba.init();

        // Execute
        goomba.onSlidInto(player);

        // Verify
        assertThat(goomba.isDefeated()).isTrue();
        assertThat(goomba.getPendingScoreReward()).isEqualTo(SCORE_100);
        verify(player, never()).getHurtBy(any());
    }

    @Test
    @DisplayName("A Koopa Troopa slid into is knocked away dead for 100 points")
    void aKoopaTroopaDies() {
        // Prepare
        final KoopaTroopa troopa = new KoopaTroopa(gameEngine, of(5, 5));
        troopa.init();

        // Execute
        troopa.onSlidInto(player);

        // Verify
        assertThat(troopa.getState()).isEqualTo(KILLED);
        assertThat(troopa.getPendingScoreReward()).isEqualTo(SCORE_100);
        verify(player, never()).getHurtBy(any());
    }

    @Test
    @DisplayName("A Para-Goomba on the ground is killed outright, not just de-winged")
    void aGroundedParaGoombaDies() {
        // Prepare
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, of(5, 5));
        paraGoomba.init();
        paraGoomba.setGrounded(true);

        // Execute
        paraGoomba.onSlidInto(player);

        // Verify
        assertThat(paraGoomba.isExpired()).as("Replaced").isTrue();
        assertThat(paraGoomba.getPendingScoreReward()).isEqualTo(SCORE_100);
        verify(player, never()).getHurtBy(any());
    }

    @Test
    @DisplayName("A Para-Goomba in the air hurts the sliding player")
    void anAirborneParaGoombaHurts() {
        // Prepare
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, of(5, 5));
        paraGoomba.init();
        paraGoomba.setGrounded(false);

        // Execute
        paraGoomba.onSlidInto(player);

        // Verify
        verify(player).getHurtBy(paraGoomba);
        assertThat(paraGoomba.isExpired()).isFalse();
    }

    @Test
    @DisplayName("An enemy that cannot be stomped is touched as usual, which hurts the player")
    void anUnstompableEnemyIsTouchedAsUsual() {
        // Prepare
        final EnemyLevelObject enemy = mock(EnemyLevelObject.class, CALLS_REAL_METHODS);
        when(enemy.canBeStomped()).thenReturn(false);

        // Execute
        enemy.onSlidInto(player);

        // Verify
        verify(enemy).onPlayerOverlap(player);
        verify(enemy, never()).onStruckByKickedObject(anyInt());
    }

    @Test
    @DisplayName("Contact with a sliding player goes to the slide, never to the stomp")
    void contactWithASlidingPlayerIsASlide() {
        // Prepare
        final AxisAlignedBoundingBox bounds = new AxisAlignedBoundingBox(0, 0, 16, 16);
        final ActiveLevelObject enemy = mock(ActiveLevelObject.class);
        when(enemy.getBounds()).thenReturn(bounds);
        when(enemy.intersects(any())).thenReturn(true);
        when(enemy.resolvesDirectionalPlayerCollision()).thenReturn(true);
        when(player.getObjectCollisionBounds()).thenReturn(bounds);
        final ActiveObjectGrid<ActiveLevelObject> grid = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        grid.insert(enemy);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        verify(enemy).onSlidInto(player);
        verify(enemy, never()).onCollisionFromAbove(player);
        verify(enemy, never()).onPlayerOverlap(player);
    }
}
