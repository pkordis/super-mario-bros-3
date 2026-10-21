package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.model.game.WorldOffset;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * What every Koopa Troopa shares, red or green: how it is drawn, and where its sprite sits relative to
 * the position it tracks. The red troopa's ledge guard - the one thing that differs
 * ({@code ObjNorm_RedTroopa} vs {@code ObjNorm_GroundTroop}, dasm prg004.asm:69-70) - is not here.
 *
 * <p><b>The tracked position is not the sprite top.</b> {@code Objects_Y} for a troopa is the top of its
 * shell, and the head is drawn above that at negative offsets - the dasm says so by name at
 * prg000.asm:3782. Everything below follows from that: the 16x32 sprite reaches a tile above the tracked
 * Y while its feet stay on the same line a Goomba's do, which is why
 * {@link GroundWalkingEnemy#pixelToWorldOffset} needs no special case for it.
 */
public interface KoopaTroopaFamilyEnemy extends GroundWalkingEnemy {
    /**
     * {@code Troopa_YOffByFrame = {$06, $05}} (dasm prg004.asm:4268): the body is drawn 6 px above
     * {@code Objects_Y} on the even frame and 5 px above on the odd one. That one pixel is the walk bob,
     * and it is a draw-time offset in the ROM rather than two differently-padded images - so it stays
     * one here too, leaving both frames agreeing on where the feet are.
     */
    int WALK_FRAME_BOB_PIXELS = 1;

    float BOB_GAME_UNITS = (float) WALK_FRAME_BOB_PIXELS / TILE_SPRITE_SIZE;

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
        return getWalkFrameIndex() % 2 == 0 ? 0f : BOB_GAME_UNITS;
    }
}
