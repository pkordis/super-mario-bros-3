package house.x1337.app.smb3.game.engine;

import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.GameContext;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.factory.PlayerFactory;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.BLACK;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_HEIGHT;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_WIDTH;
import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.PlayerIdentityType.MARIO;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACOON;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class GameEngine extends GameEngineCapabilities {
    private final CameraState cameraState;

    @Setter
    private LevelScene levelScene;
    private GameContext gameContext = LEVEL_SCENE;
    private Player player;

    @Override
    public void start() {
        final AppSettings settings = new AppSettings(true);
//        final GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
//        final DisplayMode displayMode = device.getDisplayMode();
//        settings.setResolution(displayMode.getWidth(), displayMode.getHeight());
//        settings.setFullscreen(true);
        settings.setResolution(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        settings.setFullscreen(false);
        settings.setGammaCorrection(false);
        settings.setVSync(false);
        // Disable jme3's Sync throttler so our FixedRateTimer is the sole rate limiter.
        // Sync.sync(fps) is a no-op when fps <= 0.
        settings.setFrameRate(-1);
        setSettings(settings);
        setShowSettings(false);

        super.start();
    }

    @Override
    public void stop() {
        if (context != null) {
            super.stop();
            final GameEngineStopped event = new GameEngineStopped();
            publish(event);
        }
    }

    @Override
    public void requestClose(final boolean esc) {
        stop();
    }

    @Override
    public void simpleInitApp() {
        // Replace jme3's variable-timestep NanoTimer with a fixed-rate timer so
        // that every update tick always receives tpf = 1 / TARGET_FPS, giving
        // deterministic camera movement and physics regardless of wall-clock jitter.
        setTimer(new FixedRateTimer());

//        setDisplayFps(true);
//        setDisplayStatView(true);

        stateManager.attach(cameraState);

        viewPort.setBackgroundColor(BLACK);

        // Camera target node - the camera follows the player
        final Node cameraTarget = new Node("CameraTarget");
        rootNode.attachChild(cameraTarget);
        cameraState.setTarget(cameraTarget);

        // Render the level tile layers
        renderLevelTiles(cameraTarget);

        // Create and attach the players
        player = PlayerFactory.spawn(
            MARIO.identity(),
            onSpawn -> {
                onSpawn.setMode(RACOON);
                onSpawn.renderUpdate();
                onSpawn.updateInCameraState(cameraState);
            },
            this
        );
    }

    @Override
    public void simpleUpdate(final float tpf) {
        if (player != null) {
            player.updateFrame();
        }
    }
}
