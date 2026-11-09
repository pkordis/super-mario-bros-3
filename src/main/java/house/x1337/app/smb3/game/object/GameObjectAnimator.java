package house.x1337.app.smb3.game.object;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;

import java.util.List;

/**
 * A tile-bound animator: it holds a population of {@link AnimatableLevelObject}s sharing one animation
 * and paints their frames into the baked interactive-objects layer texture, advancing once per
 * simulation tick (hence {@link MotionManager}).
 *
 * <p>Discovery, per-family reset and the {@link LevelObjectType} lookup all live in
 * {@code Animator.Registry}.
 */
public interface GameObjectAnimator<A extends AnimatableLevelObject> extends MotionManager, Animator {
    void add(A animatableLevelObject);
    List<LevelObjectType> getSupportedTypes();

    /**
     * Re-declared abstract to resolve the clash between {@link Animator#reset()} (abstract) and
     * {@link MotionManager#reset()} (a no-op default): a tile-bound animator must genuinely drop its
     * population and layer geometry, so inheriting the do-nothing default would be wrong.
     */
    @Override
    void reset();

    void registerLevel(
        Geometry interactiveObjectsLayerGeometry,
        LevelSceneDimensions dimensions
    );

    /**
     * Stops the animation on the frame it shows, until the level is rebuilt: the background tiles stop cycling
     * their frames. The end-level card does this when the player touches it.
     *
     * <p>A repaint the animator makes for another reason, such as the P-switch substitution ending, still happens.
     */
    void freeze();

    /** @return whether {@link #freeze()} has stopped the animation */
    boolean isFrozen();
}
