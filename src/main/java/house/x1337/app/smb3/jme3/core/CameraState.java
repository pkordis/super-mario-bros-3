package house.x1337.app.smb3.jme3.core;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.DoubleSupplier;

import static house.x1337.app.smb3.GameConstants.FRUSTUM;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
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
    private DoubleSupplier verticalScrollProvider;

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
        // Use the viewport sub-region dimensions (not full window) so that
        // the frustum matches the actual visible rectangle on screen.
        final float viewportWidth = (camera3D.getViewPortRight() - camera3D.getViewPortLeft()) * camera3D.getWidth();
        final float viewportHeight = (camera3D.getViewPortTop() - camera3D.getViewPortBottom()) * camera3D.getHeight();
        final float aspect = viewportWidth / viewportHeight;
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
        // X always follows the target node. Y may be driven independently by a
        // vertical-scroll provider (see setVerticalScrollProvider) so that
        // horizontal levels can lock the camera to the bottom instead of
        // tracking the player node on every jump (SMB3 Level_FreeVertScroll
        // mode 0). When no provider is set the camera follows the target on
        // both axes, as the world map does.
        final float targetY = verticalScrollProvider != null
            ? (float) verticalScrollProvider.getAsDouble()
            : translation.y;
        final Vector2f positionVector = clipping.clamp(translation.x, targetY);

        // Snap directly to the nearest screen-pixel boundary - no lag.
        // 1 screen pixel = (2 × frustum) / viewportHeight game-units.
        final float pixelSize = (2.0f * FRUSTUM) / viewportHeight;
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
     * Installs an optional provider for the camera's vertical position. When
     * set, the camera's Y is taken from the provider each frame instead of the
     * target node's Y (X still follows the target). This lets level scenes drive
     * vertical scrolling from a {@code LevelVerticalScroll} model — locking the
     * view to the bottom unless the player is flying/climbing — while the world
     * map keeps following the target node on both axes by leaving it {@code null}.
     *
     * @param verticalScrollProvider supplier of the camera centre Y in
     *                               game-units, or {@code null} to follow the target
     */
    public void setVerticalScrollProvider(final DoubleSupplier verticalScrollProvider) {
        this.verticalScrollProvider = verticalScrollProvider;
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
    public void setLevelSceneBounds(
        final int columns,
        final int rows
    ) {
        pendingColumns = columns;
        pendingRows = rows;
        if (camera3D == null) {
            return;
        }
        final float vpWidth = (camera3D.getViewPortRight() - camera3D.getViewPortLeft()) * camera3D.getWidth();
        final float vpHeight = (camera3D.getViewPortTop() - camera3D.getViewPortBottom()) * camera3D.getHeight();
        final float aspect = vpWidth / vpHeight;
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

    /**
     * The region of the level currently worth simulating for dynamic objects — the visible camera
     * frustum expanded by {@code marginPixels} on every side — expressed in sprite-pixel space.
     * Objects whose bounds fall outside this window can skip their per-tick update and collision
     * work, mirroring the ROM's bounded object-slot activation where off-screen objects lie dormant.
     * The margin keeps objects live slightly beyond the edge so they are already moving when they
     * scroll into view.
     *
     * <p>The camera lives in game-units with Y up; this converts its half-extents to sprite-pixel
     * space (Y down, one tile = {@code TILE_SPRITE_SIZE} px) using the same
     * {@code pixelY = (rows - gameY) * 16} mapping as {@code PlayerPosition.toTileUnitBased}. Level
     * height comes from {@code pendingRows}, set by {@link #setLevelSceneBounds(int, int)}.
     *
     * @param marginPixels activation margin added to each edge, in sprite-pixels
     * @return the activation window as an {@link AxisAlignedBoundingBox}, or an empty box at the origin before the
     *         camera is initialized
     */
    public AxisAlignedBoundingBox getActiveObjectRegion(final int marginPixels) {
        if (camera3D == null) {
            return new AxisAlignedBoundingBox(0, 0, 0, 0);
        }
        final double halfWidthUnits = camera3D.getFrustumRight();
        final double halfHeightUnits = camera3D.getFrustumTop();

        final double leftPixels = (position.x - halfWidthUnits) * TILE_SPRITE_SIZE - marginPixels;
        final double rightPixels = (position.x + halfWidthUnits) * TILE_SPRITE_SIZE + marginPixels;
        // Y inverts: the top of the screen (largest game-unit Y) is the smallest sprite-pixel Y.
        final double topPixels = (pendingRows - (position.y + halfHeightUnits)) * TILE_SPRITE_SIZE - marginPixels;
        final double bottomPixels = (pendingRows - (position.y - halfHeightUnits)) * TILE_SPRITE_SIZE + marginPixels;

        return new AxisAlignedBoundingBox(leftPixels, topPixels, rightPixels, bottomPixels);
    }

    private Camera initializeCamera(final Camera camera) {
        final float vpWidth = (camera.getViewPortRight() - camera.getViewPortLeft()) * camera.getWidth();
        final float vpHeight = (camera.getViewPortTop() - camera.getViewPortBottom()) * camera.getHeight();
        final float aspect = vpWidth / vpHeight;
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
