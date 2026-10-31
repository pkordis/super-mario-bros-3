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
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.player.level.death.DeathMusicStandIn;
import house.x1337.app.smb3.game.player.level.death.PlayerDeathSequence;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.jme3.core.DepthOrderedGeometryComparator;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;
import static house.x1337.app.smb3.GameConstants.BLACK;
import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;
import static house.x1337.app.smb3.GameConstants.SIMULATION_DT;
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
    private final List<? extends MotionManager> motionManagers = getBean(MotionManager.Registry.class).getAll();

    private final CameraState cameraState;
    private final PlayerData playerData;
    private final List<Player> allPlayers = new ArrayList<>();

    private LevelScene levelScene;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private ActiveObjectGrid<ActiveLevelObject> activeObjectGrid;

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
     * @return whether any player is currently halting gameplay (dasm
     *         {@code Player_HaltGame}, prg008 PRG008_A1B4). While halted the
     *         countdown timer and the whole active-object pipeline are frozen,
     *         and non-halting players freeze themselves in {@code updateFrame};
     *         only the halting player's own transition (e.g. the grow flicker)
     *         advances. Mirrors the ROM, where ~150 object/camera/timer sites
     *         early-out on {@code Player_HaltGame}.
     */
    public boolean isGameplayHalted() {
        for (final Player player : allPlayers) {
            if (player.isHaltingGameplay()) {
                return true;
            }
        }
        return false;
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

        // The pit death ends when the death music ends (PlayerDeathStarted -> PlayerDeathMusicFinished ->
        // PlayerDied). Both pieces are singletons that outlive this engine, and destroy() purges the bus,
        // so each engine subscribes them once here rather than in @PostConstruct.
        getBean(DeathMusicStandIn.class).subscribe();
        getBean(PlayerDeathSequence.class).subscribe();

        // Make the Z_DEPTH_* ladder decide what covers what, instead of jME's camera-distance sort -
        // which, for a 2D scene, is really a distance-from-screen-centre sort and reorders two sprites
        // of the same object as it moves. Both buckets, because the foreground layers are moved between
        // them while the player is in the background (LevelScenePlayerRenderer#updateForegroundLayerBuckets).
        viewPort.getQueue().setGeometryComparator(Transparent, new DepthOrderedGeometryComparator());
        viewPort.getQueue().setGeometryComparator(Translucent, new DepthOrderedGeometryComparator());

        // Restrict the game viewport to the upper portion of the window,
        // leaving the bottom strip for the fixed HUD region.
        viewPort.getCamera().setViewPort(0f, 1f, HUD_VIEWPORT_BOTTOM, 1f);

        // Build the level, player, HUD and timer. Extracted so the death-restart can replay exactly
        // this sequence from scratch (dasm: a player death drops back to the map and re-enters the
        // level fresh — here the engine stays up and the level is rebuilt in place).
        buildLevel();

        // Frame-capture state — toggled by pressing 9.
        // Uses a post viewport so the readback fires after all main viewports
        // (game scene + HUD) have fully rendered including their Translucent
        // bucket, which is where the player sprite lives.
        frameCaptureState = new FrameCaptureState();
        stateManager.attach(frameCaptureState);
        registerCaptureTrigger();
    }

    /**
     * Builds (or rebuilds) everything that belongs to one run of the level: the baked tile layers and
     * their camera target, the collision/active-object grids and placed enemies, the player at its
     * spawn in SHRUNK mode, the HUD, and the countdown timer. Everything else set up in
     * {@link #simpleInitApp()} — the fixed-rate timer, the viewports, the camera-state and
     * frame-capture app states, the input mappings, the player identity — is one-time and is
     * deliberately left untouched so this can run again on a death-restart without duplicating app
     * lifecycle state.
     */
    public void buildLevel() {
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

        // Clear every manager's leftover population BEFORE rebuilding, so a restart does not carry the
        // previous run's rewards, score captions or effect animations into the fresh scene. Done before
        // setupLevel because setupLevel -> spawnPlacedEnemies repopulates the enemy managers (and resets
        // them itself first); resetting after it would wipe the enemies just spawned.
        motionManagers.forEach(MotionManager::reset);

        // (Re)build the collision/active-object grids and (re)spawn the authored enemies. On a restart
        // this rebuilds the grids fresh and spawnPlacedEnemies first resets the enemy managers, so no
        // enemy is duplicated.
        setupLevel(levelScene);

        // Retire the previous player's input listener before the respawn attaches a new one. The key
        // mappings are shared and stay (PlayerInputHandler.init registers them once per engine); only
        // the per-player listener must be swapped, or a dead player's handler keeps receiving input.
        if (playerOne instanceof final LevelScenePlayer previousLevelPlayer) {
            previousLevelPlayer.getInputHandler().detachInput();
        }

        // Create and attach the player at its spawn, shrunk. A fresh LevelScenePlayer re-reads the
        // spawn point and builds a fresh vertical scroll, so the camera returns to the level start.
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
        allPlayers.clear();
        allPlayers.add(playerOne);
        headsUpDisplay = HeadsUpDisplayFactory.create(this);
        playerData.getPlayerTimer().clear();
        playerData.getPlayerTimer().setInitialTime(300);
        playerData.getPlayerTimer().start();
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

            if (gameContext == LEVEL_SCENE) {
                // While any player is halting gameplay (dasm Player_HaltGame — e.g.
                // the small→Super grow transition), freeze the countdown timer and
                // the whole active-object pipeline EXCEPT the rising score captions,
                // which keep animating (the "1000" over a mushroom collected at the
                // start of the grow — the sole object the ROM advances through the
                // freeze). The halting player advanced its own transition inside
                // updateFrame above; every other player froze itself. The camera
                // follows the (now-stationary) player node and the un-advanced
                // vertical scroll, so it holds still too.
                if (isGameplayHalted()) {
                    motionManagers.forEach(MotionManager::updateWhileHalted);
                    continue;
                }

                playerData.getPlayerTimer().tick();

                // Active-object tick, split into phases so a single scene-wide broadphase can serve
                // every manager: (1) clear the shared grid and spawn the placed enemies the scroll's
                // look-ahead has reached; (2) each manager ticks its objects and
                // inserts the live ones; (3) overlapping walkers turn away from each other;
                // (4) one collision pass over the union dispatches onCollisionWith; (5) managers react
                // to this tick's collisions (e.g. spawn a score caption) on the same frame the
                // collision was detected.
                //
                // Phase 3 sits after every insert because an object-vs-object test needs the whole
                // scene present; asking mid-move would only see the managers that ran first.
                activeObjectGrid.clear();
                activeObjectGrid.spawnEnemiesComingIntoView();
                motionManagers.forEach(MotionManager::update);
                activeObjectGrid.resolveObjectToObjectBumps();
                activeObjectGrid.resolveKickedObjectKills();
                activeObjectGrid.resolveCarriedObjectKills();
                activeObjectGrid.resolveActiveObjectCollisions(checkedCast(getAllPlayers()));
                motionManagers.forEach(MotionManager::postCollision);
                levelScene.tick();
                collisionGrid.tick();
            }
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
        this.activeObjectGrid = levelScene.toActiveObjectGrid(this);
        activeObjectGrid.spawnPlacedEnemies();
        levelScene.reset();
    }
}
