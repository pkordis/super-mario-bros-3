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
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import static house.x1337.app.smb3.GameConstants.HUD_VIEWPORT_BOTTOM;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.game.player.PlayerData.CARD_SLOTS;

/**
 * Fixed HUD (status bar) rendered at the bottom of the game window.
 *
 * <p>The status bar begins 192 px down the screen and never scrolls with the
 * level. The HUD uses its own orthographic camera and viewport so it
 * remains fixed regardless of where the game camera scrolls.
 *
 * <p>The HUD is rendered as a single texture generated programmatically by
 * {@link HeadsUpDisplayRenderer} at 1:1 resolution (240×48 px) and scaled by
 * {@code TILE_SCALE} (4×) to fill the viewport region. This produces
 * pixel-perfect rendering without tile-based composition.
 *
 * <p>The HUD scene graph is independent of the main game scene — nothing from
 * the game renders into the HUD region and vice versa.
 *
 * <p>The status bar updates the P-meter, coins, lives, score, and time each frame.
 */
@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public final class HeadsUpDisplay implements HeadsUpDisplayRenderer {
    private final GameEngine gameEngine;
    private final PlayerData playerData;
    private final PlayerData.State playerDataSnapshot = new PlayerData.State(-1, -1, -1, -1, -1, null);

    @Value("classpath:/hud/hud_base.png")
    private ImageResource baseImage;

    private Camera hudCamera;
    private Node hudRoot;
    private ViewPort hudViewPort;
    private boolean dirty = true;
    private boolean lastPowerFlashLit;
    private boolean lastPowerMeterFull;
    private int hiddenCardSlots;
    private int lastPowerMeter = -1;
    private int maxPowerTick;

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

        log.info("HUD viewport initialized (bottom {}% of window)", (int) (HUD_VIEWPORT_BOTTOM * 100));
    }

    /**
     * Takes this HUD off the screen for good: its viewport is removed from the engine and stops rendering. Called
     * when a level build replaces this HUD with a new one.
     */
    public void dispose() {
        gameEngine.getRenderManager().removeMainView(hudViewPort);
    }

    @Override
    public boolean isCardSlotHidden(final int slot) {
        return (hiddenCardSlots & 1 << slot) != 0;
    }

    /**
     * Called once per frame from the game loop. Syncs HUD state from the
     * current player/game data and re-renders the HUD texture when dirty.
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
        final Player player = gameEngine.getAllPlayers().getFirst();
        if (player == null) {
            return;
        }

        dirty = !playerDataSnapshot.equals(playerData);
        playerDataSnapshot.updateFrom(playerData);

        // The level completion can leave any card slot empty for a while
        int hidden = 0;
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            if (gameEngine.getLevelCompletion().isCardSlotHidden(slot)) {
                hidden |= 1 << slot;
            }
        }
        if (hidden != hiddenCardSlots) {
            hiddenCardSlots = hidden;
            dirty = true;
        }

        // P-meter: convert from playerPower (0–7) to display level
        if (player instanceof LevelScenePlayer levelPlayer) {
            final int power = levelPlayer.getPlayerData().getPlayerPower();
            final boolean full = power >= PMETER_LEVELS;

            // [P] flash animation:
            // When at max power, decrement tick each frame. Bit 3 determines
            // lit vs dark: (tick & 8) == 0 → lit, otherwise → dark.
            if (full) {
                maxPowerTick = (maxPowerTick - 1) & 255;
                final boolean flashLit = (maxPowerTick & 8) == 0;
                playerData.setPowerMeterFull(flashLit);

                if (flashLit != lastPowerFlashLit) {
                    lastPowerFlashLit = flashLit;
                    dirty = true;
                }
            } else {
                playerData.setPowerMeterFull(false);
            }

            if (power != lastPowerMeter || full != lastPowerMeterFull) {
                playerData.setPowerMeter(power);
                lastPowerMeter = power;
                lastPowerMeterFull = full;
                dirty = true;
            }
        }
    }
}
