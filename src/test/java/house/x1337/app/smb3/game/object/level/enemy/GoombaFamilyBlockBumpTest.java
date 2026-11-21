package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.SoundEffect.KOOPA_SHELL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A block bumped from below kills a Goomba or a Para-Goomba standing on it, for a flat 100 points, and throws it away
 * from the closest player upside down. A Para-Goomba dies outright, and rising into a bumped block kills it too.
 */
class GoombaFamilyBlockBumpTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 16;
    private static final int ROWS = 10;
    private static final int WALK_FRAME_COUNT = 2;
    private static final int FLOOR_ROW = 8;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final int COLUMN = 6;

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final Set<Coordinates> bumpedCells = new HashSet<>();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private ActiveObjectGrid<ActiveLevelObject> activeObjectGrid;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private GameEngine gameEngine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(any())).thenAnswer(invocation -> isSolid(invocation.getArgument(0)));
        when(collisionGrid.isWallOrCeilingSolidTile(any()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0)));
        when(collisionGrid.isBlockBumpActiveAt(any()))
            .thenAnswer(invocation -> bumpedCells.contains(invocation.<Coordinates>getArgument(0)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        activeObjectGrid = mock(ActiveObjectGrid.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getActiveObjectGrid()).thenReturn(activeObjectGrid);

        final GoombaAnimator goombaAnimator = mock(GoombaAnimator.class);
        when(goombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        final ParaGoombaAnimator paraGoombaAnimator = mock(ParaGoombaAnimator.class);
        when(paraGoombaAnimator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(SoundPlayer.class)).thenReturn(soundPlayer);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(GoombaAnimator.class)).thenReturn(goombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(paraGoombaAnimator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(Goomba.class), any(), any()))
            .thenAnswer(invocation -> {
                final Goomba goomba = new Goomba(gameEngine, invocation.getArgument(2));
                goomba.init();
                return goomba;
            });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A Goomba on a bumped block is killed for a flat 100 points, upside down, with the bump sound")
    void aGoombaOnABumpedBlockIsKilled() {
        // Prepare
        final Goomba goomba = goombaOnTheFloor();
        bumpedCells.add(of(COLUMN, FLOOR_ROW));

        // Execute
        goomba.motionUpdate();

        // Verify
        assertThat(goomba.isDefeated()).isTrue();
        assertThat(goomba.isHittable()).isFalse();
        assertThat(goomba.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(goomba.getWhamMotion().isUpsideDown()).as("flipped at once").isTrue();
        verify(soundPlayer).play(KOOPA_SHELL);
    }

    @Test
    @DisplayName("A bumped Goomba is thrown up at 3 px a frame, half a pixel a frame away from the closest player")
    void aBumpedGoombaIsThrownAwayFromThePlayer() {
        // Prepare
        final Goomba goomba = goombaOnTheFloor();
        final LevelScenePlayer player = playerAtPixelX(0);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
        bumpedCells.add(of(COLUMN, FLOOR_ROW));
        goomba.motionUpdate();
        final double x = goomba.getPosition().x();
        final double y = goomba.getPosition().y();

        // Execute
        goomba.motionUpdate();

        // Verify
        assertThat(goomba.getPosition().x() - x)
            .as("away from the player on the left")
            .isCloseTo(0.5, within(TOLERANCE));
        assertThat(goomba.getPosition().y() - y).as("up at once").isCloseTo(-3.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A Goomba beside a bumped block walks on")
    void aGoombaBesideABumpedBlockWalksOn() {
        // Prepare
        final Goomba goomba = goombaOnTheFloor();
        bumpedCells.add(of(COLUMN + 2, FLOOR_ROW));

        // Execute
        goomba.motionUpdate();

        // Verify
        assertThat(goomba.isDefeated()).isFalse();
    }

    @Test
    @DisplayName("A flattened Goomba is not bumped")
    void aFlattenedGoombaIsNotBumped() {
        // Prepare
        final Goomba goomba = goombaOnTheFloor();
        goomba.setSquished(true);
        goomba.setSquishTicksRemaining(16);
        bumpedCells.add(of(COLUMN, FLOOR_ROW));

        // Execute
        goomba.motionUpdate();

        // Verify
        assertThat(goomba.getWhamMotion()).isNull();
    }

    @Test
    @DisplayName("A Para-Goomba on a bumped block dies outright rather than losing its wings")
    void aParaGoombaOnABumpedBlockDiesOutright() {
        // Prepare
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, of(COLUMN, WALK_ROW));
        paraGoomba.init();
        bumpedCells.add(of(COLUMN, FLOOR_ROW));

        // Execute
        paraGoomba.motionUpdate();

        // Verify
        final Goomba replacement = spawnedGoomba();
        assertThat(replacement.getWhamMotion()).as("the replacement dies at once").isNotNull();
        assertThat(replacement.getWhamMotion().isUpsideDown()).isTrue();
        assertThat(replacement.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(paraGoomba.isDeWinged()).as("not the stomp's de-winging").isFalse();
        assertThat(paraGoomba.isExpired()).isTrue();
        assertThat(paraGoomba.isHittable()).isFalse();
    }

    @Test
    @DisplayName("A Para-Goomba rising into a bumped block is killed by it")
    void aParaGoombaRisingIntoABumpedBlockIsKilled() {
        // Prepare
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, of(COLUMN, WALK_ROW - 2));
        paraGoomba.init();
        paraGoomba.setVelocity(new FixedPointVelocity(0, -16));
        bumpedCells.add(of(COLUMN, WALK_ROW - 3));

        // Execute
        paraGoomba.motionUpdate();

        // Verify
        assertThat(paraGoomba.isExpired()).isTrue();
        assertThat(spawnedGoomba().getPendingScoreReward()).isEqualTo(SCORE_100);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Goomba goombaOnTheFloor() {
        final Goomba goomba = new Goomba(gameEngine, of(COLUMN, WALK_ROW));
        goomba.init();
        return goomba;
    }

    private Goomba spawnedGoomba() {
        final ArgumentCaptor<EnemyLevelObject> captor = ArgumentCaptor.forClass(EnemyLevelObject.class);
        verify(activeObjectGrid).spawnEnemy(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Goomba.class);
        return (Goomba) captor.getValue();
    }

    private static LevelScenePlayer playerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        return player;
    }

    private boolean isSolid(final Coordinates cell) {
        if (cell.y() < 0) {
            return false;
        }
        if (cell.y() >= ROWS || cell.x() < 0 || cell.x() >= COLUMNS) {
            return true;
        }
        return cell.y() == FLOOR_ROW;
    }
}
