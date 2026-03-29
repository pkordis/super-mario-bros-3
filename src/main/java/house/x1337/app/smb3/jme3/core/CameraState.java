package house.x1337.app.smb3.jme3.core;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.engine.core.GameEngine;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.FRUSTUM;
import static java.lang.Math.round;

@Slf4j
@Getter
@Singleton
@RequiredArgsConstructor
public class CameraState extends BaseAppState {
    private final Target<Spatial> target = new Target<>();
    private final Clipping clipping = new Clipping();
    private final Vector3f position = new Vector3f();
    private Camera camera3D;

    @Override
    protected void initialize(final Application app) {
        camera3D = initializeCamera(app.getCamera());
        setId("CameraState");
        setClipping(new Vector2f(-1000, -1000), new Vector2f(1000, 1000));

        if (app instanceof final GameEngine gameEngine) {
            gameEngine.getFlyByCamera().setEnabled(false);
            gameEngine.getFlyByCamera().unregisterInput();
            log.info("Default fly-by camera removed");
        } else {
            throw new IllegalStateException("CameraState requires GameEngine instances");
        }
    }

    @Override
    public void update(float tpf) {
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

    private void setClipping(
        final Vector2f minimumClipping,
        final Vector2f maxClipping
    ) {
        clipping.setMinimum(minimumClipping);
        clipping.setMaximum(maxClipping);
    }

    private Camera initializeCamera(final Camera camera) {
        float aspect = (float) camera.getWidth() / camera.getHeight();
        camera.setParallelProjection(true);
        camera.setFrustum(-1000.0F, 1000.0F, -aspect * FRUSTUM, aspect * FRUSTUM, FRUSTUM, -FRUSTUM);
        camera.setLocation(new Vector3f(0.0F, 0.0F, 0.0F));
        log.info("""
                2D camera initialized:
                [ SET ] :CameraDistanceFrustum     >> {}""",
            FRUSTUM
        );
        return camera;
    }

    @Override
    protected void cleanup(Application app) {
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

        public void setValue(E value) {
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
