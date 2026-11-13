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
import house.x1337.app.smb3.game.engine.fade.FadeOverlayState;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.hud.HeadsUpDisplay;
import house.x1337.app.smb3.game.hud.factory.HeadsUpDisplayFactory;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.completion.LevelCompletionMessageOverlayState;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.factory.PlayerFactory;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.player.level.death.PlayerDeathSequence;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.sound.LevelMusic;
import house.x1337.app.smb3.game.sound.OpenAlSoftConfiguration;
import house.x1337.app.smb3.game.sound.PowerMeterSound;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.jme3.core.DepthOrderedGeometryComparator;
import house.x1337.app.smb3.model.event.GameEngineStopped;
import house.x1337.app.smb3.model.event.GameplayHalted;
import house.x1337.app.smb3.model.event.GameplayResumed;
import house.x1337.app.smb3.model.event.PlayerPowerLost;
import house.x1337.app.smb3.service.GameSettingsService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_CAPTURE_TOGGLE;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class GameEngine extends GameEngineCapabilities {
    private final List<? extends MotionManager> motionManagers = getBean(MotionManager.Registry.class).getAll();
    private final FadingEngine fadingEngine = getBean(FadingEngine.class);
    private final LevelFrameCounter levelFrameCounter = new LevelFrameCounter();
    private final LevelCompletion levelCompletion = getBean(LevelCompletion.class, this);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = getBean(PowerSwitchTimeWindow.class);
    private final GameSettingsService gameSettingsService = getBean(GameSettingsService.class);

    private final CameraState cameraState;
    private final PlayerData playerData;
    private final List<Player> allPlayers = new ArrayList<>();

    @Setter
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

    /** Whether gameplay was halted on the last tick, so its changes can be announced. */
    private boolean gameplayHalted;

    /**
     * @return whether gameplay is halted: by a player, or by a blocking screen fade, which leaves nothing else
     *         running while the screen fades in or out. While halted the countdown timer and the whole
     *         active-object pipeline are frozen, and non-halting players freeze themselves in
     *         {@code updateFrame}; only the halting player's own transition (e.g. the grow flicker) advances.
     *         Objects, the camera and the timer all stop while gameplay is halted. The P-Switch window stops only
     *         for a blocking fade: a player's halt leaves it counting.
     */
    public boolean isGameplayHalted() {
        if (fadingEngine.isHaltingGameplay()) {
            return true;
        }
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

        // Before super.start(), which opens the audio device: OpenAL reads its settings only as it loads.
        getBean(OpenAlSoftConfiguration.class).apply();
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

        // A death ends when its music and its body are both gone (PlayerDeathStarted, SoundPlayComplete and, for a
        // throe, PlayerDeathFallEnded -> PlayerDied). The sequence is a singleton that outlives this engine, and
        // destroy() purges the bus, so each engine subscribes it once here rather than in @PostConstruct.
        getBean(PlayerDeathSequence.class).subscribe();

        // The sound player is a singleton too: it plays through whichever engine attached itself last. The level
        // music and the P-meter sound subscribe here for the same reason as the death sequence above, and so does
        // this engine's level completion.
        getBean(SoundPlayer.class).attach(this);
        getBean(LevelMusic.class).subscribe();
        getBean(PowerMeterSound.class).subscribe();
        levelCompletion.subscribe();

        // The fading engine is a singleton too, so a fade left running (or the screen held black) by a previous engine
        // must not carry into this one.
        fadingEngine.reset();

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
        // this sequence from scratch (a player death re-enters the level fresh: the engine stays up and
        // the level is rebuilt in place).
        buildLevel();

        // Post views render in the order they are created, and four are stacked over the finished frame: the
        // foremost sprites, then the level completion's messages, then the fade (which must darken them both), then
        // frame capture (which must record the fade). The foremost overlay is built here, at once; the three app
        // states build theirs when they initialise, in the order they are attached.
        getBean(ForemostSpriteOverlay.class).prepare(this);
        stateManager.attach(getBean(LevelCompletionMessageOverlayState.class, levelCompletion));
        stateManager.attach(getBean(FadeOverlayState.class, fadingEngine));

        // Frame-capture state — toggled by pressing 9.
        // Uses a post viewport so the readback fires after all main viewports
        // (game scene + HUD) have fully rendered including their Translucent
        // bucket, which is where the player sprite lives.
        frameCaptureState = new FrameCaptureState();
        stateManager.attach(frameCaptureState);
        registerCaptureTrigger();
    }

    /**
     * Builds (or rebuilds) the level from its start, as {@link #buildLevel(LevelRunStart)} with
     * {@link LevelRunStart#FRESH}.
     */
    public void buildLevel() {
        buildLevel(LevelRunStart.FRESH);
    }

    /**
     * Builds (or rebuilds) everything that belongs to one run of the level: the baked tile layers and
     * their camera target, the collision/active-object grids and placed enemies, the player at its
     * spawn in the start's mode, the HUD, and, when the start asks for it, a restarted countdown timer.
     * Everything else set up in {@link #simpleInitApp()} — the fixed-rate timer, the viewports, the
     * camera-state and frame-capture app states, the input mappings, the player identity. Is one-time
     * and is deliberately left untouched so this can run again on a death-restart without duplicating
     * app lifecycle state.
     *
     * @param start the player's power-up and whether the timer starts over
     */
    public void buildLevel(final LevelRunStart start) {
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

        // A run that starts over closes any P-Switch window. A run carried through a pipe keeps it open with the
        // time it has left, so the new scene shows the substitution and plays its music from the first frame.
        if (!start.isCarryOver()) {
            powerSwitchTimeWindow.reset();
        }

        // (Re)build the collision/active-object grids and (re)spawn the authored enemies. On a restart
        // this rebuilds the grids fresh and spawnPlacedEnemies first resets the enemy managers, so no
        // enemy is duplicated.
        setupLevel(levelScene);

        // Every run starts its level frame counter phases over, with the end-level card uncollected and every sound
        // effect heard again, after a completion has muted them.
        levelFrameCounter.reset();
        levelCompletion.reset();
        getBean(SoundPlayer.class).unmuteEffects();

        // Retire the previous player's input listener before the respawn attaches a new one. The key
        // mappings are shared and stay (PlayerInputHandler.init registers them once per engine); only
        // the per-player listener must be swapped, or a dead player's handler keeps receiving input.
        // Input events are dispatched on this thread, between frames, so none can arrive mid-swap.
        final PlayerInputHandler previousInput = playerOne instanceof final LevelScenePlayer previousLevelPlayer
            ? previousLevelPlayer.getInputHandler()
            : null;
        if (previousInput != null) {
            previousInput.detachInput();
        }

        // Create and attach the player at its spawn. A fresh LevelScenePlayer re-reads the spawn point
        // and builds a fresh vertical scroll, so the camera returns to the level start.
        playerData.setIdentity(MARIO.identity());
        // A fresh start begins with an empty P-meter; a run carried through a pipe keeps its charge
        if (!start.isCarryOver() && playerData.resetPowerMeter()) {
            publish(new PlayerPowerLost());
        }
        playerOne = PlayerFactory.spawn(
            playerData,
            onSpawn -> {
                onSpawn.setMode(start.mode());
                onSpawn.renderPlayer();
                onSpawn.updateInCameraState(cameraState);
            },
            this
        );
        allPlayers.clear();
        allPlayers.add(playerOne);

        // Keys held through the rebuild stay held for the new player
        if (previousInput != null && playerOne instanceof final LevelScenePlayer spawnedLevelPlayer) {
            spawnedLevelPlayer.getInputHandler().carryOverFrom(previousInput);
        }
        // Each HUD has its own viewport, so the one it replaces must stop rendering
        if (headsUpDisplay != null) {
            headsUpDisplay.dispose();
        }
        headsUpDisplay = HeadsUpDisplayFactory.create(this);
        if (start.restartTimer()) {
            playerData.getPlayerTimer().clear();
            playerData.getPlayerTimer().setInitialTime(300);
            // A fresh start takes the pace saved last; a run carried through a pipe keeps the pace it started with
            playerData.getPlayerTimer().setPace(gameSettingsService.getSettings().getTimerPace());
            playerData.getPlayerTimer().start();
        }

        // After the window has been closed or carried over, as the music follows it
        getBean(LevelMusic.class).start(levelScene, start);
    }

    private void registerCaptureTrigger() {
        getInputManager().addMapping(
            HANDLER_CAPTURE_TOGGLE,
            new KeyTrigger(KeyInput.KEY_9)
        );
        getInputManager().addListener(
            (ActionListener) (name, isPressed, tpf) -> {
                if (isPressed && HANDLER_CAPTURE_TOGGLE.equals(name)) {
                    frameCaptureState.toggleCapture();
                }
            },
            HANDLER_CAPTURE_TOGGLE
        );
    }

    @Override
    public void simpleUpdate(final float timePerFrame) {
        // A jingle that has ended is reported before this frame's ticks, which react to it
        getBean(SoundPlayer.class).update(timePerFrame);
        simulationAccumulator += timePerFrame;

        // jME3 fully runs simpleInitApp() — which spawns the player, builds its
        // node and loads its assets — before it ever calls simpleUpdate(). So
        // the player (and, for a level, its position/node/assets) is guaranteed
        // present here; no null checks are needed. MapPlayer's update methods
        // are no-ops and it snapshots nothing, so the same path serves both.
        while (simulationAccumulator >= SIMULATION_DT) {
            simulationAccumulator -= SIMULATION_DT;
            // The level frame counter counts every frame, halted or not.
            levelFrameCounter.tick();
            // First, and outside the halt branch below: a blocking fade halts gameplay, so a fade ticked from
            // anything the halt skips would never finish.
            fadingEngine.tick();
            allPlayers.forEach(Player::updateFrame);

            if (gameContext == LEVEL_SCENE) {
                // While any player is halting gameplay (e.g. the small→Super grow
                // transition), freeze the countdown timer and the whole active-object
                // pipeline EXCEPT the rising score captions, which keep animating (the
                // "1000" over a mushroom collected at the start of the grow — the sole
                // object that advances through the freeze). The halting player advanced its own transition inside
                // updateFrame above; every other player froze itself. The camera
                // follows the (now-stationary) player node and the un-advanced
                // vertical scroll, so it holds still too.
                final boolean halted = isGameplayHalted();
                announceHaltChange(halted);
                if (halted) {
                    motionManagers.forEach(MotionManager::updateWhileHalted);
                    // The P-Switch window keeps counting while a player halts gameplay (a pipe, a grow, a death),
                    // and stops only while a blocking fade does, as between two scenes.
                    if (!fadingEngine.isHaltingGameplay()) {
                        collisionGrid.countDownPowerSwitchTimeWindow();
                    }
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
                levelCompletion.spawnCardComingIntoView();
                levelCompletion.tick();
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

    /** Publishes {@link GameplayHalted} or {@link GameplayResumed} on the tick gameplay stops or starts again. */
    private void announceHaltChange(final boolean halted) {
        if (halted == gameplayHalted) {
            return;
        }
        gameplayHalted = halted;
        if (halted) {
            publish(new GameplayHalted());
        } else {
            publish(new GameplayResumed());
        }
    }
}
