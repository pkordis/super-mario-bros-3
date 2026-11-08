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
     * Stops the animation on the frame it shows, until the level is rebuilt. The port of setting bit 7 of
     * {@code Level_TimerEn}, which makes {@code PRG030_8DF4} skip the background tiles' bank cycling
     * (prg030.asm:2216-2217): the end-level card sets it when the player touches it (prg002.asm:6228-6230).
     *
     * <p>A repaint the animator makes for another reason, such as the P-switch substitution ending, still happens.
     */
    void freeze();

    /** @return whether {@link #freeze()} has stopped the animation */
    boolean isFrozen();
}
