package house.x1337.app.smb3.game.engine;

import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.GameContext;
import house.x1337.app.smb3.game.hud.HeadsUpDisplay;
import house.x1337.app.smb3.game.hud.factory.HeadsUpDisplayFactory;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.factory.PlayerFactory;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static house.x1337.app.smb3.GameConstants.BLACK;
import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;
import static house.x1337.app.smb3.GameConstants.SIMULATION_DT;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_HEIGHT;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_WIDTH;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.PlayerIdentityType.MARIO;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class GameEngine extends GameEngineCapabilities {
    private final List<? extends MotionManager> animationManagers = getBean(MotionManager.Registry.class).getAll();
    private final CameraState cameraState;
    private final PlayerData playerData;

    @Setter
    private LevelScene levelScene;
    private GameContext gameContext = LEVEL_SCENE;
    private HeadsUpDisplay headsUpDisplay;
    private Player player;
    private FrameCaptureState frameCaptureState;

    /**
     * Accumulates real elapsed time between render frames. When it exceeds
     * {@link house.x1337.app.smb3.GameConstants#SIMULATION_DT}, one simulation
     * tick is consumed. This decouples game-logic rate (60 Hz) from render rate.
     */
    private double simulationAccumulator = 0.0;

    /**
     * All players currently active in the scene. Today a scene runs a single player, but
     * callers must treat this as a collection so that planned multi-player levels need no
     * change beyond this method. This is the one place the single-player assumption lives —
     * world objects (e.g. {@code SuperLeaf}) resolve collisions against every player here
     * rather than holding a hard-wired reference to one.
     *
     * @return an immutable view of the scene's players
     */
    public List<Player> getPlayers() {
        return List.of(player);
    }

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
        // Replace jme3's variable-timestep NanoTimer with a fixed-rate timer
        // that ticks at TARGET_FPS. The simulation loop inside simpleUpdate
        // accumulates the elapsed time and steps game logic at SIMULATION_FPS,
        // decoupling render rate from game speed.
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
        cameraState.setLevelSceneBounds(
            levelScene.getDimensions().columns(),
            levelScene.getDimensions().rows()
        );

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

        // Frame-capture state — toggled by pressing 9.
        // Uses a post viewport so the readback fires after all main viewports
        // (game scene + HUD) have fully rendered including their Translucent
        // bucket, which is where the player sprite lives.
        frameCaptureState = new FrameCaptureState();
        stateManager.attach(frameCaptureState);
        registerCaptureTrigger();
    }

    private void registerCaptureTrigger() {
        getInputManager().addMapping(
            PlayerInputHandler.HANDLER_CAPTURE_TOGGLE,
            new KeyTrigger(KeyInput.KEY_9)
        );
        getInputManager().addListener(
            (ActionListener) (name, isPressed, tpf) -> {
                if (isPressed && PlayerInputHandler.HANDLER_CAPTURE_TOGGLE.equals(name)) {
                    frameCaptureState.toggleCapture();
                }
            },
            PlayerInputHandler.HANDLER_CAPTURE_TOGGLE
        );
    }

    @Override
    public void simpleUpdate(final float timePerFrame) {
        simulationAccumulator += timePerFrame;

        // jME3 fully runs simpleInitApp() — which spawns the player, builds its
        // node and loads its assets — before it ever calls simpleUpdate(). So
        // the player (and, for a level, its position/node/assets) is guaranteed
        // present here; no null checks are needed. MapPlayer's update methods
        // are no-ops and it snapshots nothing, so the same path serves both.
        while (simulationAccumulator >= SIMULATION_DT) {
            simulationAccumulator -= SIMULATION_DT;
            player.updateFrame();
            playerData.getPlayerTimer().tick();
            animationManagers.forEach(MotionManager::update);
        }

        // Interpolate the player's visual position between the previous and
        // current simulation states so that rendering at rates above 60 Hz
        // produces smooth, jitter-free movement.
        final double alpha = simulationAccumulator / SIMULATION_DT;
        player.interpolateVisualPosition(alpha);

        headsUpDisplay.update(timePerFrame);
    }
}
