package house.x1337.app.smb3.game.hud;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.PlayerTimer;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;

/**
 * Fixed HUD (status bar) rendered at the bottom of the game window.
 *
 * <p>This mirrors the NES IRQ-driven status bar that begins at scan-line 192
 * (prg031.asm: the name table scroll is fixed at that point to always display
 * the status bar). The HUD uses its own orthographic camera and viewport so it
 * remains fixed regardless of where the game camera scrolls.
 *
 * <p>The HUD is rendered as a single texture generated programmatically by
 * {@link HeadsUpDisplayRenderer} at 1:1 NES resolution (240×48 px) and scaled by
 * {@code TILE_SCALE} (4×) to fill the viewport region. This produces
 * pixel-perfect rendering without tile-based composition.
 *
 * <p>The HUD scene graph is independent of the main game scene — nothing from
 * the game renders into the HUD region and vice versa.
 *
 * <p>From prg026.asm {@code StatusBar_UpdateValues}: the status bar updates
 * P-meter, coins, lives, score, and time each frame.
 */
@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class HeadsUpDisplay implements HeadsUpDisplayRenderer {
    private final GameEngine gameEngine;
    private final PlayerData playerData;

    @Value("classpath:/font/hud/hud_base.png")
    private ImageResource baseImage;

    private ViewPort hudViewPort;
    private Camera hudCamera;
    private Node hudRoot;
    private PlayerTimer playerTimer;

    /** Tracks whether the HUD needs re-rendering (dirty flag). */
    private boolean dirty = true;

    /** Snapshot of last rendered P-meter value for dirty detection. */
    private int lastPMeter = -1;

    /** Snapshot of last rendered P-meter full state. */
    private boolean lastPMeterFull;

    /**
     * [P] flash tick counter. Decremented every frame when P-meter is full.
     * From prg026.asm {@code MaxPower_Tick}: the flash state is determined by
     * bit 3 of this counter ({@code tick & 0x08}). When bit 3 is clear, [P]
     * shows lit; when set, [P] shows dark. This produces an 8-frame-on /
     * 8-frame-off flash cycle (16-frame period).
     */
    private int maxPowerTick;

    /** Snapshot of last rendered [P] flash state for dirty detection. */
    private boolean lastPFlashLit;

    /**
     * Initializes the HUD viewport, camera, and scene graph. Must be called
     * from the jME3 render thread (inside {@code simpleInitApp} or via
     * {@code enqueue}).
     */
    @PostConstruct
    void init() {
        hudCamera = gameEngine.getCamera().clone();
        hudCamera.setViewPort(0f, 1f, 0f, HUD_VIEWPORT_BOTTOM);
        hudCamera.setParallelProjection(true);
        // Simple orthographic frustum covering a unit quad — the quad is sized to fill the area.
        hudCamera.setFrustum(-1f, 1f, -0.5f, 0.5f, 0.5f, -0.5f);
        hudCamera.setLocation(new Vector3f(0.5f, 0.5f, 0f));

        hudRoot = new Node("HudRoot");

        // Perform the initial render of the HUD texture
        renderToGeometry(hudRoot, playerData);

        hudViewPort = gameEngine.getRenderManager().createMainView("HUD", hudCamera);
        hudViewPort.attachScene(hudRoot);
        hudViewPort.setBackgroundColor(new ColorRGBA(0f, 0f, 0f, 1f));
        hudViewPort.setClearFlags(true, true, true);
        hudRoot.updateGeometricState();

        playerTimer = playerData.getPlayerTimer();

        log.info("HUD viewport initialized (bottom {}% of window)", (int) (HUD_VIEWPORT_BOTTOM * 100));
    }

    /**
     * Called once per frame from the game loop. Syncs HUD state from the
     * current player/game data and re-renders the HUD texture when dirty.
     *
     * <p>Mirrors prg026.asm {@code StatusBar_UpdateValues} which calls:
     * {@code StatusBar_Fill_PowerMT}, {@code StatusBar_Fill_Coins},
     * {@code StatusBar_Fill_Lives}, {@code StatusBar_Fill_Score},
     * {@code StatusBar_Fill_Time}.
     *
     * @param timePerFrame time per frame in seconds
     */
    public void update(final float timePerFrame) {
        syncStateFromPlayer();

        // Only re-render the texture when state actually changed
        if (dirty) {
            renderToGeometry(hudRoot, playerData);
            dirty = false;
        }

        hudRoot.updateLogicalState(timePerFrame);
        hudRoot.updateGeometricState();
    }

    /**
     * Pulls current game values into the HUD state model.
     * Sets the dirty flag if any value changed.
     */
    private void syncStateFromPlayer() {
        final Player player = gameEngine.getPlayer();
        if (player == null) {
            return;
        }

        // Tick the countdown timer each frame
        dirty |= playerTimer.tick();

        // P-meter: convert from playerPower (0–7) to display level
        if (player instanceof LevelScenePlayer levelPlayer) {
            final int power = levelPlayer.getPlayerData().getPlayerPower();
            final boolean full = power >= PMETER_LEVELS;

            // [P] flash animation (prg026.asm MaxPower_Tick):
            // When at max power, decrement tick each frame. Bit 3 determines
            // lit vs dark: (tick & 0x08) == 0 → lit, otherwise → dark.
            if (full) {
                maxPowerTick = (maxPowerTick - 1) & 0xFF;
                final boolean flashLit = (maxPowerTick & 0x08) == 0;
                playerData.setPMeterFull(flashLit);

                if (flashLit != lastPFlashLit) {
                    lastPFlashLit = flashLit;
                    dirty = true;
                }
            } else {
                playerData.setPMeterFull(false);
            }

            if (power != lastPMeter || full != lastPMeterFull) {
                playerData.setPMeter(power);
                lastPMeter = power;
                lastPMeterFull = full;
                dirty = true;
            }
        }

        // Future: sync lives, coins, score, timer, world from game state
        // and set dirty = true when any value changes.
    }
}
