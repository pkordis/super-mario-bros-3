package house.x1337.app.smb3.game.engine;

import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.GameContext;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.hud.HeadsUpDisplay;
import house.x1337.app.smb3.game.hud.factory.HeadsUpDisplayFactory;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.factory.PlayerFactory;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.BLACK;
import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;
import static house.x1337.app.smb3.GameConstants.SIMULATION_DT;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_HEIGHT;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_WIDTH;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.PlayerIdentityType.MARIO;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class GameEngine extends GameEngineCapabilities {
    private final List<? extends MotionManager<?>> motionManagers = getBean(MotionManager.Registry.class).getAll();

    /**
     * The single, scene-wide broadphase for dynamic {@link ActiveLevelObject}s. Owned here so all
     * active-object managers share one grid: each clears nothing and simply inserts its live
     * objects during its {@code update()} (the engine clears the grid once per tick beforehand),
     * and {@link #resolveActiveObjectCollisions()} runs one collision pass over the union. Cell
     * size is one tile.
     */
    private final ActiveObjectGrid<ActiveLevelObject> activeObjectGrid = new ActiveObjectGrid<>(TILE_SPRITE_SIZE);

    private final CameraState cameraState;
    private final PlayerData playerData;
    private final List<Player> allPlayers = new ArrayList<>();

    private LevelScene levelScene;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private GameContext gameContext = LEVEL_SCENE;
    private HeadsUpDisplay headsUpDisplay;
    private FrameCaptureState frameCaptureState;

    @Getter(PRIVATE)
    private Player playerOne;

    /**
     * Accumulates real elapsed time between render frames. When it exceeds
     * {@link house.x1337.app.smb3.GameConstants#SIMULATION_DT}, one simulation
     * tick is consumed. This decouples game-logic rate (60 Hz) from render rate.
     */
    private double simulationAccumulator = 0.0;

    /**
     * Runs the scene-wide player↔object collision pass for this tick. Each player's hitbox is
     * computed once (hoisted out of the per-object loop), used to gather nearby objects from the
     * shared broadphase, then narrowphase-tested; survivors get {@code onCollisionWith}. Managers
     * react to the outcome in {@link MotionManager#postCollision()}.
     *
     * <p>Runs after every manager's {@code update()} has inserted its live objects, so the grid
     * holds the union of all active objects before any query.
     */
    private void resolveActiveObjectCollisions() {
        if (gameContext == LEVEL_SCENE) {
            activeObjectGrid.resolveActiveObjectCollisions(checkedCast(getAllPlayers()));
        }
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

    /**
     * Reports whether a solid block at the given cell is currently playing its hit-from-below bounce
     * this tick. Active objects resting on a block poll this against the cell at their feet to know
     * they were just bumped — the project's stand-in for the ROM's transient
     * {@code TILEA_BLOCKBUMP_CLEAR} tile (dasm {@code Object_InteractWithWorld} @ PRG001_A97C).
     *
     * @param cell the tile cell to test
     * @return {@code true} if any motion manager reports a live block-bump at {@code cell}
     */
    public boolean isBlockBumpActiveAt(final Offset cell) {
        for (final MotionManager<?> motionManager : motionManagers) {
            if (motionManager.isBlockBumpActiveAt(cell)) {
                return true;
            }
        }
        return false;
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
        playerOne = PlayerFactory.spawn(
            playerData,
            onSpawn -> {
                onSpawn.setMode(SHRUNK);
                onSpawn.renderPlayer();
                onSpawn.updateInCameraState(cameraState);
            },
            this
        );
        allPlayers.add(playerOne);
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
            allPlayers.forEach(Player::updateFrame);
            playerData.getPlayerTimer().tick();

            // Active-object tick, split into phases so a single scene-wide broadphase can serve
            // every manager: (1) clear the shared grid; (2) each manager ticks its objects and
            // inserts the live ones; (3) one collision pass over the union dispatches onCollisionWith;
            // (4) managers react to this tick's collisions (e.g. spawn a score caption) on the same
            // frame the collision was detected.
            activeObjectGrid.clear();
            motionManagers.forEach(MotionManager::update);
            resolveActiveObjectCollisions();
            motionManagers.forEach(MotionManager::postCollision);
        }

        // Interpolate the player's visual position between the previous and
        // current simulation states so that rendering at rates above 60 Hz
        // produces smooth, jitter-free movement.
        final double alpha = simulationAccumulator / SIMULATION_DT;
        allPlayers.forEach(player -> player.interpolateVisualPosition(alpha));

        headsUpDisplay.update(timePerFrame);
    }

    public void setupLevel(final LevelScene levelScene) {
        this.levelScene = levelScene;
        this.collisionGrid = levelScene.toCollisionGrid(this);
    }
}
