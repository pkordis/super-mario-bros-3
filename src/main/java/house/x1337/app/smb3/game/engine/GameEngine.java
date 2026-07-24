package house.x1337.app.smb3.game.engine;

import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.GameContext;
import house.x1337.app.smb3.game.hud.HeadsUpDisplay;
import house.x1337.app.smb3.game.hud.factory.HeadsUpDisplayFactory;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.factory.PlayerFactory;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.BLACK;
import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_HEIGHT;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_WIDTH;
import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.PlayerIdentityType.MARIO;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class GameEngine extends GameEngineCapabilities {
    private final CameraState cameraState;
    private final PlayerData playerData;

    @Setter
    private LevelScene levelScene;
    private GameContext gameContext = LEVEL_SCENE;
    private HeadsUpDisplay headsUpDisplay;
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

        setDisplayFps(false);
        setDisplayStatView(false);

        stateManager.attach(cameraState);
        viewPort.setBackgroundColor(BLACK);

        // Restrict the game viewport to the upper portion of the window,
        // leaving the bottom strip for the fixed HUD region.
        viewPort.getCamera().setViewPort(0f, 1f, HUD_VIEWPORT_BOTTOM, 1f);

        // Create the HUD viewport - a separate camera that renders a solid
        // colored strip at the bottom of the screen, independent of camera
        // scrolling. This mirrors the NES IRQ-driven status bar (scanline 192+).


        // Camera target node - the camera follows the player
        final Node cameraTarget = new Node("CameraTarget");
        rootNode.attachChild(cameraTarget);
        cameraState.setTarget(cameraTarget);

        // Render the level tile layers
        renderLevelTiles(cameraTarget);

        // Constrain camera to level boundaries — the viewport can never
        // scroll past the tile area, eliminating the black background at edges.
        cameraState.setLevelSceneBounds(levelScene.getColumns(), levelScene.getRows());

        // Create and attach the players
        playerData.setIdentity(MARIO.identity());
        player = PlayerFactory.spawn(
            playerData,
            onSpawn -> {
                onSpawn.setMode(RACCOON);
                onSpawn.renderPlayer();
                onSpawn.updateInCameraState(cameraState);
            },
            this
        );
        headsUpDisplay = HeadsUpDisplayFactory.create(this);
        playerData.getPlayerTimer().setInitialTime(300);
        playerData.getPlayerTimer().start();
    }

    @Override
    public void simpleUpdate(final float timePerFrame) {
        if (player != null) {
            player.updateFrame();
        }
        headsUpDisplay.update(timePerFrame);
    }
}
