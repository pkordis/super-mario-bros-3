package house.x1337.app.smb3.jme3.core;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.FRUSTUM;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

@Slf4j
@Getter
@Prototype
@RequiredArgsConstructor
public class CameraState extends BaseAppState {
    private final Target<Spatial> target = new Target<>();
    private final Clipping clipping = new Clipping();
    private final Vector3f position = new Vector3f();
    private Camera camera3D;
    private int pendingColumns;
    private int pendingRows;
    private float lastAspect;

    @Override
    protected void initialize(final Application app) {
        setId("CameraState");
        camera3D = initializeCamera(app.getCamera());

        if (app instanceof final GameEngine gameEngine) {
            gameEngine.getFlyByCamera().setEnabled(false);
            gameEngine.getFlyByCamera().unregisterInput();
            log.info("Default fly-by camera removed");
        } else {
            throw new IllegalStateException("CameraState requires GameEngine instances");
        }

        if (pendingColumns > 0 && pendingRows > 0) {
            setLevelSceneBounds(pendingColumns, pendingRows);
        }
    }

    @Override
    public void update(final float timePerFrame) {
        // Reapply the orthographic frustum every frame to counteract jME3's
        // automatic resize (which adjusts the frustum when the AwtPanel
        // dimensions change, shrinking the visible area).
        final float aspect = (float) camera3D.getWidth() / camera3D.getHeight();
        if (aspect != lastAspect) {
            lastAspect = aspect;
            camera3D.setFrustum(
                -1000.0f,
                1000.0f,
                -aspect * FRUSTUM,
                aspect * FRUSTUM,
                FRUSTUM,
                -FRUSTUM
            );
            if (pendingColumns > 0 && pendingRows > 0) {
                setLevelSceneBounds(pendingColumns, pendingRows);
            }
        }

        final Vector3f translation = target.getLocalTranslation();
        final Vector2f positionVector = clipping.clamp(translation.x, translation.y);

        // Snap directly to the nearest screen-pixel boundary - no lag.
        // 1 screen pixel = (2 × frustum) / viewportHeight game-units.
        final float pixelSize = (2.0f * FRUSTUM) / camera3D.getHeight();
        position.set(
            round(positionVector.x / pixelSize) * pixelSize,
            round(positionVector.y / pixelSize) * pixelSize,
            FRUSTUM
        );
        camera3D.setLocation(position);
    }

    public void setTarget(final Spatial spatial) {
        target.setValue(spatial);
    }

    /**
     * Constrains the camera so it can never scroll beyond the level
     * boundaries. The viewport edges are clamped to [0, levelWidth] on X
     * and [0, levelHeight] on Y, meaning the level's lower-left corner
     * always aligns with (0,0) and no black area is ever visible.
     *
     * <p>When the level is smaller than the viewport in a given axis, the
     * camera is locked to the center of that axis so the level content is
     * centered and no scrolling is allowed.
     *
     * @param columns level width in game-units (tile columns)
     * @param rows    level height in game-units (tile rows)
     */
    public void setLevelSceneBounds(final int columns, final int rows) {
        pendingColumns = columns;
        pendingRows = rows;
        if (camera3D == null) {
            return;
        }
        final float aspect = (float) camera3D.getWidth() / camera3D.getHeight();
        final float halfViewWidth = aspect * FRUSTUM;
        final float halfViewHeight = FRUSTUM;

        final float minX;
        final float maxX;
        if (columns <= halfViewWidth * 2) {
            // Level narrower than viewport — lock to center
            minX = columns / 2.0f;
            maxX = columns / 2.0f;
        } else {
            minX = halfViewWidth;
            maxX = columns - halfViewWidth;
        }

        final float minY;
        final float maxY;
        if (rows <= halfViewHeight * 2) {
            // Level shorter than viewport — lock to center
            minY = rows / 2.0f;
            maxY = rows / 2.0f;
        } else {
            minY = halfViewHeight;
            maxY = rows - halfViewHeight;
        }

        clipping.setMinimum(new Vector2f(minX, minY));
        clipping.setMaximum(new Vector2f(maxX, maxY));

        log.info("""
            Camera bounds set to level dimensions:
            [ SET ] :LevelSize       >> {}×{} tiles
            [ SET ] :ClippingMin     >> ({}, {})
            [ SET ] :ClippingMax     >> ({}, {})""",
            columns, rows, minX, minY, maxX, maxY
        );
    }

    private Camera initializeCamera(final Camera camera) {
        final float aspect = (float) camera.getWidth() / camera.getHeight();
        camera.setParallelProjection(true);
        camera.setFrustum(
            -1000.0f,
            1000.0f,
            -aspect * FRUSTUM,
            aspect * FRUSTUM,
            FRUSTUM,
            -FRUSTUM
        );
        camera.setLocation(new Vector3f(0.0f, 0.0f, 0.0f));
        log.info("""
            2D camera initialized:
            [ SET ] :CameraDistanceFrustum     >> {}""",
            FRUSTUM
        );
        return camera;
    }

    @Override
    protected void cleanup(final Application app) {
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @NoArgsConstructor
    static class Target<E extends Spatial> {
        @Getter
        private E value;
        private Vector3f aux = new Vector3f();

        public void setValue(final E value) {
            if (value == null && this.value != null) {
                this.aux = this.value.getLocalTranslation().clone();
            }
            this.value = value;
        }

        public Vector3f getLocalTranslation() {
            return this.value == null ? aux : value.getLocalTranslation();
        }
    }
}
