package house.x1337.app.smb3.game.object.level.reward.animation;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;

/**
 * Flips the star between its two sprites, the normal one and the red one, every few ticks while it bounces around.
 */
public final class StarmanAnimator {
    static final int TICKS_PER_FRAME = 2;

    private final Texture[] frames;
    private int ticks;

    public StarmanAnimator(final Texture normal, final Texture red) {
        this.frames = new Texture[] {normal, red};
    }

    /** Advances the animation by one tick and shows the frame it is on. */
    public void update(final Geometry geometry) {
        ticks++;
        show(geometry);
    }

    public void show(final Geometry geometry) {
        final int frame = (ticks / TICKS_PER_FRAME) % frames.length;
        geometry.getMaterial().setTexture("ColorMap", frames[frame]);
    }
}
