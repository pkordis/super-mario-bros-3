package house.x1337.app.smb3.game.camera;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.LevelScene;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.FRUSTUM;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.clamp;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Vertical camera scroll for horizontal levels, porting the SMB3
 * {@code Level_FreeVertScroll == 0} behaviour used by World 1-1
 * (dasm prg008 Player_DoScrolling @ PRG008_B208 / PRG008_B246 / PRG008_B258).
 *
 * <p>Mode-0 semantics, straight from the disassembly comment: "Screen locked at
 * $EF (lowest point) unless flying or climbing a vine". The camera rests locked
 * at the bottom of the level and does <em>not</em> track the player vertically
 * during normal walking and jumping — this is what the original does and what a
 * naive "camera follows the player node" implementation gets wrong (the view
 * scrolls on every jump).
 *
 * <p>Free vertical scrolling is enabled only while an override is active
 * (raccoon P-meter flight, {@code Player_FlyTime > 0}, or climbing a vine) and
 * it persists afterwards until the scroll has eased all the way back down to the
 * bottom (dasm PRG008_B246: once {@code Vert_Scroll == $EF} it stays there
 * unless an override occurs).
 *
 * <p>While free-scrolling the player is kept within a vertical dead-zone band
 * (dasm screen-Y {@code $30..$58} on the 240-line screen, PRG008_B258): upward
 * scrolling is capped at 3&nbsp;px/frame ("Minimum vertical scroll delta is -3",
 * PRG008_B274) while the downward return is unclamped, matching the original.
 *
 * <p>All values are in game-units (tiles, Y-up) — the same space the camera and
 * tiles live in. The disassembly's pixel constants are converted at
 * {@code 16 px == 1 tile} and expressed as fractions of the visible height so
 * they stay correct regardless of the frustum size.
 */
@Getter
@Prototype
public final class LevelSceneVerticalScroll {
    /** dasm PRG008_B274 "Minimum vertical scroll delta is -3": 3 NES px/frame. */
    private static final float MAX_UP_PER_TICK = 3.0f / TILE_SPRITE_SIZE;
    private static final float BAND_TOP_FRACTION = 3.0f / 15;
    private static final float BAND_BOTTOM_FRACTION = 5.5f / 15; // 0.367
    private static final float EPSILON = 1.0e-4f;

    private final float minCameraY;
    private final float maxCameraY;
    private final float halfViewHeight;

    /** Highest the player may sit above the camera centre before it scrolls up. */
    private final float bandHighOffset;
    /** Lowest the player may sit above the camera centre before it scrolls down. */
    private final float bandLowOffset;

    private float cameraY;

    public LevelSceneVerticalScroll(final LevelScene levelScene) {
        final int rows = levelScene.getDimensions().rows();
        final float halfViewHeight = FRUSTUM;
        final float viewHeight = 2.0f * halfViewHeight;
        final float minY;
        final float maxY;
        if (rows <= halfViewHeight * 2.0f) {
            minY = rows / 2.0f;
            maxY = rows / 2.0f;
        } else {
            minY = halfViewHeight;
            maxY = rows - halfViewHeight;
        }
        this.minCameraY = minY;
        this.maxCameraY = max(minCameraY, maxY);
        this.halfViewHeight = halfViewHeight;
        this.bandHighOffset = halfViewHeight - BAND_TOP_FRACTION * viewHeight;
        this.bandLowOffset = halfViewHeight - BAND_BOTTOM_FRACTION * viewHeight;
        this.cameraY = minCameraY;
    }

    /**
     * Advances the scroll by one simulation tick and returns the resulting
     * camera centre Y (game-units, Y-up).
     *
     * @param playerCenter      the player's world Y position
     * @param freeScrollOverride {@code true} while flying or climbing — the dasm
     *                           override that unlocks vertical scrolling
     * @return the new camera centre Y
     */
    public float update(final float playerCenter, final boolean freeScrollOverride) {
        // dasm PRG008_B246: scrolling stays locked at the bottom unless an
        // override is active OR the scroll has not yet eased back down to it.
        final boolean freeScrolling = freeScrollOverride || cameraY > minCameraY + EPSILON;
        if (!freeScrolling) {
            cameraY = minCameraY;
            return cameraY;
        }

        // dasm PRG008_B258: keep the player inside the vertical dead-zone band.
        float desiredY = getDesiredY(playerCenter);
        cameraY = clamp(desiredY, minCameraY, maxCameraY);
        return cameraY;
    }

    private float getDesiredY(float playerCenter) {
        final float offset = playerCenter - cameraY; // how far above centre the player sits
        float desiredY = cameraY;
        if (offset > bandHighOffset) {
            desiredY = playerCenter - bandHighOffset; // player too high → scroll up
        } else if (offset < bandLowOffset) {
            desiredY = playerCenter - bandLowOffset; // player too low → scroll down
        }

        // Upward scroll is capped at 3 px/frame; the downward return is not.
        if (desiredY > cameraY) {
            desiredY = min(desiredY, cameraY + MAX_UP_PER_TICK);
        }
        return desiredY;
    }
}
