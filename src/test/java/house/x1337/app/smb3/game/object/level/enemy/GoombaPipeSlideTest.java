package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY_IN_PIPE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A Goomba sliding out of a side pipe. The whole level is solid, so anything but the slide would stop it at once;
 * it starts on pixel (64, 77) and slides right.
 */
class GoombaPipeSlideTest {
    private static final int COLUMNS = 16;
    private static final int ROWS = 8;
    private static final int SLIDE_TICKS = 40;
    private static final int WALK_FRAME_COUNT = 2;
    private static final PixelPosition START = new PixelPosition(64, 77);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private Goomba goomba;

    @BeforeEach
    void prepare() {
        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(any())).thenReturn(true);
        when(collisionGrid.isWallOrCeilingSolidTile(any())).thenReturn(true);

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);
        when(gameEngine.getRootNode()).thenReturn(mock(Node.class));

        final GoombaAnimator animator = mock(GoombaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(GoombaAnimator.class)).thenReturn(animator);

        goomba = new Goomba(gameEngine, of(4, 5));
        goomba.init();
        goomba.slideOutOfAPipe(START, true);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The slide moves the Goomba 20 px sideways in 40 ticks, through solid ground and walls")
    void slidesTwentyPixelsThroughTheTerrain() {
        // Execute
        tick(SLIDE_TICKS);

        // Verify
        assertThat(goomba.getPosition().x()).isCloseTo(START.x() + 20, within(1e-9));
        assertThat(goomba.getPosition().y()).as("No gravity").isCloseTo(START.y(), within(1e-9));
        assertThat(goomba.isFacingRight()).as("No wall turned it").isTrue();
        assertThat(goomba.isSlidingOutOfAPipe()).isFalse();
    }

    @Test
    @DisplayName("During the slide the walk frame changes every 4 ticks, twice the normal rate")
    void animatesTwiceAsFast() {
        // Prepare
        final List<Integer> changes = new ArrayList<>();
        int frame = goomba.getWalkFrameIndex();

        // Execute
        for (int tick = 1; tick <= 12; tick++) {
            goomba.motionUpdate();
            if (goomba.getWalkFrameIndex() != frame) {
                frame = goomba.getWalkFrameIndex();
                changes.add(tick);
            }
        }

        // Verify
        assertThat(changes).containsExactly(4, 8, 12);
    }

    @Test
    @DisplayName("The sliding Goomba is drawn behind the pipe and bounces off nothing, then turns normal")
    void isBehindThePipeOnlyWhileSliding() {
        // Execute & Verify
        assertThat(goomba.getBaseDepth()).isEqualTo(Z_DEPTH_ENEMY_IN_PIPE);
        assertThat(goomba.getBaseDepth()).as("Behind the static environment").isLessThan(STATIC_ENVIRONMENT.getZ());
        assertThat(goomba.bouncesOffOtherObjects()).isFalse();
        tick(SLIDE_TICKS);
        assertThat(goomba.getBaseDepth()).isEqualTo(Z_DEPTH_ENEMY);
        assertThat(goomba.bouncesOffOtherObjects()).isTrue();
    }

    @Test
    @DisplayName("The sliding Goomba is drawn with the tile layers, so the pipe covers it, and with the sprites after")
    void sharesTheLayersBucketOnlyWhileSliding() {
        // Prepare
        final Geometry sprite = new Geometry("Goomba");
        goomba.setSpriteGeometry(sprite);

        // Execute & Verify
        tick(1);
        assertThat(sprite.getQueueBucket()).isEqualTo(Transparent);
        assertThat(sprite.getLocalTranslation().z).isEqualTo(Z_DEPTH_ENEMY_IN_PIPE);
        tick(SLIDE_TICKS - 1);
        assertThat(sprite.getQueueBucket()).isEqualTo(Translucent);
        assertThat(sprite.getLocalTranslation().z).isEqualTo(Z_DEPTH_ENEMY);
    }

    @Test
    @DisplayName("After the slide the Goomba falls and collides with the terrain again")
    void walksNormallyAfterTheSlide() {
        // Prepare
        when(collisionGrid.isGroundSolidTile(any())).thenReturn(false);
        when(collisionGrid.isWallOrCeilingSolidTile(any())).thenReturn(false);
        tick(SLIDE_TICKS);

        // Execute
        tick(4);

        // Verify
        assertThat(goomba.getPosition().y()).as("Gravity pulls it down").isGreaterThan(START.y());
    }

    @Test
    @DisplayName("A dispensed Goomba keeps the pipe's direction instead of turning to face the player")
    void doesNotFaceThePlayer() {
        // Prepare
        final PlayerPosition playerPosition = new PlayerPosition();
        playerPosition.setX(0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(playerPosition);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));

        // Execute
        goomba.spawnIntoScene();

        // Verify
        assertThat(goomba.isFacingRight()).isTrue();
    }

    private void tick(final int ticks) {
        for (int count = 0; count < ticks; count++) {
            goomba.motionUpdate();
        }
    }
}
