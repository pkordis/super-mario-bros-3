package house.x1337.app.smb3.engine;

import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
import house.x1337.app.smb3.GameConstants;
import house.x1337.app.smb3.input.BooleanKeyHandler;
import house.x1337.app.smb3.input.CameraInputHandler;

// TODO: not needed
public final class CameraNavigationControl extends AbstractControl {

    /** Normal scroll speed in screen pixels per frame. */
    private static final int BASE_SPEED_PPF = 8;
    /** Fast scroll speed (Z held) in screen pixels per frame. */
    private static final int ACCEL_SPEED_PPF = 16;

    /**
     * One screen pixel expressed in game-units.
     * With the correct frustum ({@code VIEWPORT_TILES_Y / 2}):
     * {@code pixelSize = 1 / TILE_SIZE} (exact power-of-two float).
     */
    private static final float PIXEL_SIZE = 1.0F / GameConstants.TILE_SIZE;

    private final CameraInputHandler inputHandler;
    private final Vector3f velocity = new Vector3f();

    public CameraNavigationControl(final CameraInputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    @Override
    protected void controlUpdate(final float tpf) {
        if (!inputHandler.isMoving()) {
            return;
        }

        velocity.set(0, 0, 0);

        final BooleanKeyHandler up    = inputHandler.getHandler(CameraInputHandler.HANDLER_UP);
        final BooleanKeyHandler down  = inputHandler.getHandler(CameraInputHandler.HANDLER_DOWN);
        final BooleanKeyHandler left  = inputHandler.getHandler(CameraInputHandler.HANDLER_LEFT);
        final BooleanKeyHandler right = inputHandler.getHandler(CameraInputHandler.HANDLER_RIGHT);
        final BooleanKeyHandler accel = inputHandler.getHandler(CameraInputHandler.HANDLER_ACCELERATE);

        if (up    != null && up.isActive())    { velocity.y += 1; }
        if (down  != null && down.isActive())  { velocity.y -= 1; }
        if (left  != null && left.isActive())  { velocity.x -= 1; }
        if (right != null && right.isActive()) { velocity.x += 1; }

        if (velocity.lengthSquared() > 0) {
            final float ppf = (accel != null && accel.isActive()) ? ACCEL_SPEED_PPF : BASE_SPEED_PPF;
            // normalizeLocal then scale by (ppf pixels × pixelSize game-units/pixel)
            // → camera moves exactly ppf screen pixels this frame, no tpf involved
            velocity.normalizeLocal().multLocal(ppf * PIXEL_SIZE);
            spatial.move(velocity);
        }
    }

    @Override
    protected void controlRender(final RenderManager rm, final ViewPort vp) {
        // No rendering logic needed.
    }
}
