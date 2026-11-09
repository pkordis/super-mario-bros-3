package house.x1337.app.smb3.game.camera;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.FRUSTUM;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.clamp;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Vertical camera scroll for horizontal levels, with the bottom-locked
 * behaviour used by World 1-1.
 *
 * <p>The screen stays locked at its lowest point unless the player is flying
 * or climbing a vine. The camera rests locked at the bottom of the level and
 * does <em>not</em> track the player vertically during normal walking and
 * jumping — this is what a naive "camera follows the player node"
 * implementation gets wrong (the view scrolls on every jump).
 *
 * <p>Free vertical scrolling is enabled only while an override is active
 * (raccoon P-meter flight with flight time remaining, or climbing a vine) and
 * it persists afterwards until the scroll has eased all the way back down to the
 * bottom. Once there it stays locked unless an override occurs.
 *
 * <p>While free-scrolling the player is kept within a vertical dead-zone band
 * (screen-Y {@code 48..88} on the 240-line screen): upward scrolling is capped
 * at 3&nbsp;px/frame while the downward return is unclamped.
 *
 * <p>All values are in game-units (tiles, Y-up) — the same space the camera and
 * tiles live in. Pixel constants are converted at {@code 16 px == 1 tile} and
 * expressed as fractions of the visible height so they stay correct regardless
 * of the frustum size.
 */
@Getter
@Prototype
public final class LevelSceneVerticalScroll {
    /** Upward scrolling is capped at 3 px/frame. */
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
    private float previousCameraY;
    private float interpolatedCameraY;

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
        this.previousCameraY = minCameraY;
        this.interpolatedCameraY = minCameraY;
    }

    /**
     * Advances the scroll by one simulation tick and returns the resulting
     * camera centre Y (game-units, Y-up).
     *
     * @param playerCenter      the player's world Y position
     * @param freeScrollOverride {@code true} while flying or climbing — the
     *                           override that unlocks vertical scrolling
     * @return the new camera centre Y
     */
    public float update(final float playerCenter, final boolean freeScrollOverride) {
        // Remember the pre-tick value so the render loop can interpolate the
        // camera centre between ticks (see interpolate) — mirroring the way the
        // player node's visual position is interpolated. Without this the camera
        // Y would step once per simulation tick while the node moves smoothly
        // every render frame, making the player jitter vertically whenever the
        // camera actually scrolls (raccoon flight / falling).
        previousCameraY = cameraY;

        // Scrolling stays locked at the bottom unless an
        // override is active OR the scroll has not yet eased back down to it.
        final boolean freeScrolling = freeScrollOverride || cameraY > minCameraY + EPSILON;
        if (!freeScrolling) {
            cameraY = minCameraY;
            return cameraY;
        }

        // Keep the player inside the vertical dead-zone band.
        float desiredY = getDesiredY(playerCenter);
        cameraY = clamp(desiredY, minCameraY, maxCameraY);
        return cameraY;
    }

    /**
     * Interpolates the camera centre Y between the previous and current
     * simulation ticks, in lockstep with the player node's visual interpolation.
     * Called once per render frame with the same {@code alpha} the node uses, so
     * both camera axes advance together and the view stays smooth above 60&nbsp;Hz.
     *
     * @param alpha fraction into the current tick, in {@code [0, 1]}
     * @return the interpolated camera centre Y (game-units, Y-up)
     */
    public float interpolate(final double alpha) {
        interpolatedCameraY = previousCameraY + (float) ((cameraY - previousCameraY) * alpha);
        return interpolatedCameraY;
    }

    /**
     * Puts the camera straight at {@code centreY}, with no easing in from where it was, as a level junction sets
     * the vertical scroll outright.
     *
     * <p>The centre is first moved, as little as needed, so that {@code playerY} sits inside the dead-zone band:
     * once the camera is above the bottom lock the band applies on the next {@link #update}, and a player outside it
     * would make the view jump on that tick. The result is then kept inside the level. When that leaves the camera
     * above the bottom, it scrolls freely until it is back down, as after any other free scroll.
     *
     * @param centreY the camera centre wanted, in game units (Y-up)
     * @param playerY where the player's top will be when this scroll next updates, in game units (Y-up)
     * @return the new camera centre Y
     */
    public float snapTo(final float centreY, final float playerY) {
        final float keepsPlayerInBand = clamp(centreY, playerY - bandHighOffset, playerY - bandLowOffset);
        cameraY = clamp(keepsPlayerInBand, minCameraY, maxCameraY);
        previousCameraY = cameraY;
        interpolatedCameraY = cameraY;
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
