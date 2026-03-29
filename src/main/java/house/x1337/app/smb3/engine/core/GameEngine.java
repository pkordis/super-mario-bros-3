package house.x1337.app.smb3.engine.core;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.engine.CameraNavigationControl;
import house.x1337.app.smb3.engine.FixedRateTimer;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.input.CameraInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.*;
import static house.x1337.app.smb3.enumeration.GameEventType.GAME_ENGINE_STOPPED;

@Slf4j
@Prototype
@RequiredArgsConstructor
public final class GameEngine extends SimpleApplication implements GameEngineCapabilities {
    private final CameraState cameraState;

    @Getter
    @Setter
    private LevelScene levelScene;

//    @Setter
//    private volatile Runnable onStopCallback;

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
            publishEvent(GAME_ENGINE_STOPPED);
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

        setDisplayFps(true);
        setDisplayStatView(true);

        stateManager.attach(cameraState);

        viewPort.setBackgroundColor(BLACK);

        // Camera target node - the Camera2DRenderer follows this spatial
        final Node cameraTarget = new Node("CameraTarget");
        rootNode.attachChild(cameraTarget);
        cameraState.setTarget(cameraTarget);

        // Input handler — event-driven, only sets boolean states on key press/release
        final CameraInputHandler cameraInputHandler = new CameraInputHandler();
        cameraInputHandler.install(inputManager);
        cameraInputHandler.setExitAction(this::stop);

        // Navigation control — attached to camera target; only processes movement
        // when at least one directional key is active (no per-frame cost when idle)
        final CameraNavigationControl navigationControl = new CameraNavigationControl(cameraInputHandler);
        cameraTarget.addControl(navigationControl);

        renderLevelTiles(cameraTarget);
    }
}
