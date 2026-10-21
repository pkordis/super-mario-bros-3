package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.model.game.WorldOffset;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;

public interface KoopaTroopaFamilyEnemy extends GroundWalkingEnemy {
    int CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT = 16;
    int SHELL_FEET_THRESHOLD_TICKS = 80;
    int SHELL_FOOT_OUTWARD_OFFSET_PIXELS = 3;
    int SHELL_JITTER_PIXELS = 1;
    int SHELL_LANDING_X_DAMPING = 2;
    int SHELL_LANDING_Y_DAMPING = 4;
    int SHELL_MIN_BOUNCE_Y_VELOCITY_FIXED_POINT = -2;
    int SHELL_RIGHT_SPRITE_OFFSET_PIXELS = 8;
    int SHELL_SHAKE_THRESHOLD_TICKS = 96;
    int TAIL_STRIKE_HIT_COOLDOWN_TICKS = 12;
    int WAKE_UP_TICKS = 255;

    boolean isFacingRight();
    int getWalkFrameIndex();

    /**
     * Places the sprite for this tick: mirrored when the troopa faces right, nudged down a pixel on the
     * odd walk frame.
     *
     * <p>Bails out before touching anything when the sprite has not been built, which is what keeps
     * {@code motionUpdate()} free of the scene graph and therefore unit-testable.
     */
    default void positionSprite() {
        final WorldOffset feetAnchor = pixelToWorldOffset(getPosition());
        if (feetAnchor == null) {
            return;
        }
        final Geometry spriteGeometry = getSpriteGeometry();
        spriteGeometry.setLocalScale(isFacingRight() ? -1f : 1f, 1f, 1f);
        spriteDrawOffset(feetAnchor).applyTo(spriteGeometry);
    }

    /**
     * Where the quad is actually translated to, given the feet anchor the position maps to.
     *
     * <p>Split out from {@link #positionSprite()} because the mirror compensation is exactly the kind of
     * off-by-one-quad-width that silently misplaces a sprite by 16 px, and is worth being able to assert
     * on without a scene graph.
     *
     * @param feetAnchor the quad's unmirrored bottom-left corner, from {@code pixelToWorldOffset}
     * @return the translation to apply
     */
    default WorldOffset spriteDrawOffset(final WorldOffset feetAnchor) {
        return feetAnchor.plus(mirrorCompensation(), -walkFrameBob(), 0f);
    }

    /**
     * The troopa faces right by being mirrored rather than by swapping to a second set of art, which is
     * what the ROM does: {@code Troopa_Draw} keeps one set of patterns, swaps which body half gets which,
     * and sets {@code SPR_HFLIP} (dasm prg004.asm:4349, :5311).
     *
     * <p>A negative X scale mirrors the quad about its own origin, and jME puts that origin at the quad's
     * <b>bottom-left</b> corner - so the mirrored sprite would hang a full width to the <em>left</em> of
     * its anchor. Adding one quad width back puts it over the same column it started in. This is the
     * horizontal twin of the vertical flip a tail-struck {@link Goomba} uses, and it relies on the same
     * {@code FaceCullMode.Off} that {@code EnemyAnimator#buildSprite} already sets, since a negative
     * scale reverses the winding.
     */
    private float mirrorCompensation() {
        return isFacingRight() ? getSpriteDimensions().width() : 0f;
    }

    /** The odd walk frame is drawn one pixel lower. World Y grows upward, so the caller subtracts this. */
    private float walkFrameBob() {
        return getWalkFrameIndex() % 2 == 0 ? 0f : PIXELS_TO_GAME_UNITS;
    }
}
