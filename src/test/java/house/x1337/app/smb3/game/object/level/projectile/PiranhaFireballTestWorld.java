package house.x1337.app.smb3.game.object.level.projectile;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import lombok.Getter;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A level for piranha fireball tests: a 256x240 view at the origin, no players until a test adds one, an
 * animator that hands out bare quads, and a bean factory that builds real fireballs, so the manager is
 * tested on real state.
 */
@Getter
final class PiranhaFireballTestWorld implements AutoCloseable {
    static final int COLUMNS = 64;
    static final int ROWS = 30;
    static final double SCREEN_LEFT = 0;
    static final double SCREEN_TOP = 0;
    static final double SCREEN_WIDTH = 256;
    static final double SCREEN_HEIGHT = 240;

    private final GameEngine gameEngine = mock(GameEngine.class);
    private final CameraState cameraState = mock(CameraState.class);
    private final Node rootNode = new Node("root");
    private final PiranhaFireballAnimator animator = mock(PiranhaFireballAnimator.class);
    private final List<Player> players = new ArrayList<>();
    private final PiranhaFireballMotionManager manager = new PiranhaFireballMotionManager();
    private final MockedStatic<StaticBeanFactory> staticBeanFactory;

    PiranhaFireballTestWorld() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getRootNode()).thenReturn(rootNode);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getAllPlayers()).thenReturn(players);
        viewAt(SCREEN_LEFT, SCREEN_TOP);

        when(animator.buildSprite(any())).thenAnswer(invocation -> new Geometry("Fireball", new Quad(0.5f, 1f)));

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(SoundPlayer.class)).thenReturn(mock(SoundPlayer.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PiranhaFireballAnimator.class))
            .thenReturn(animator);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(PiranhaFireball.class), any()))
            .thenAnswer(invocation -> new PiranhaFireball(
                invocation.getArgument(1)
            ));
    }

    @Override
    public void close() {
        staticBeanFactory.close();
    }

    /** Moves the camera so its view's top-left is at the given sprite pixels. */
    void viewAt(final double left, final double top) {
        when(cameraState.getActiveObjectRegion(0))
            .thenReturn(new AxisAlignedBoundingBox(left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT));
    }

    PiranhaFireball spawn(final double x, final double y, final int xVelocity, final int yVelocity) {
        manager.spawn(gameEngine, new PixelPosition(x, y), new FixedPointVelocity(xVelocity, yVelocity));
        return manager.getActiveFireballs().stream()
            .filter(fireball -> fireball.getPosition().x() == x && fireball.getPosition().y() == y)
            .findFirst()
            .orElseThrow();
    }

    void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            manager.update();
            manager.postCollision();
        }
    }

    /** A large, standing, foreground player whose reference position is the given sprite pixels. */
    LevelScenePlayer playerAt(final double x, final double y) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        when(player.getVisibility()).thenReturn(FOREGROUND);
        when(player.isSmall()).thenReturn(false);
        players.add(player);
        return player;
    }
}
