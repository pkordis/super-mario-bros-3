package house.x1337.app.smb3.game.object.level.enemy.family;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState;
import house.x1337.app.smb3.game.object.level.enemy.Goomba;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static com.jme3.scene.Spatial.CullHint.Always;
import static com.jme3.scene.Spatial.CullHint.Inherit;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_BEHIND_PLAYER;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_CARRIED_IN_FRONT_OF_PLAYER;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.HELD;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KICKED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator.SHELL_DIMENSIONS;

public interface KoopaTroopaFamilyEnemy extends KoopaTroopaShellCapabilities {
    int[] FEET_FLICKER_MASKS = {2, 3, 4, 8, 16, 16};
    int SHAKE_PHASE_MASK = 7;
    int SHELL_FEET_THRESHOLD_TICKS = 80;
    int SHELL_FOOT_OUTWARD_OFFSET_PIXELS = 3;
    int SHELL_JITTER_PIXELS = 1;
    int SHELL_RIGHT_SPRITE_OFFSET_PIXELS = 8;
    int TAIL_STRIKE_HIT_COOLDOWN_TICKS = 12;

    int getWalkFrameIndex();
    Geometry getShellGeometry();
    Geometry getLeftShellFootGeometry();
    Geometry getRightShellFootGeometry();

    default boolean isShellShowing() {
        return getState() != NORMAL;
    }

    default boolean isShellShaking() {
        return (getState() == SHELLED || getState() == HELD) && getWakeUpTicks() < SHELL_SHAKE_THRESHOLD_TICKS;
    }

    default boolean isWakingFeetShowing() {
        final KoopaTroopaState state = getState();
        final int wakeUpTicks = getWakeUpTicks();
        if (!isShellShowing() || state == KILLED || state == KICKED || wakeUpTicks >= SHELL_FEET_THRESHOLD_TICKS) {
            return false;
        }
        return (wakeUpTicks & FEET_FLICKER_MASKS[wakeUpTicks >> 4]) != 0;
    }

    default int getShellJitterPixels() {
        if (!isShellShaking()) {
            return 0;
        }
        final int phase = getWakeUpTicks() & SHAKE_PHASE_MASK;
        return phase != 0 && phase % 2 == 0 ? SHELL_JITTER_PIXELS : 0;
    }

    /** Only the walker is mirrored by its facing; a shell's art is the same whichever way it travels. */
    default boolean isSpriteMirrored() {
        return !isShellShowing() && isFacingRight();
    }

    /**
     * Places the sprite for this tick - the walker, or the shell and its feet.
     *
     * <p>Bails out before touching anything when the sprites have not been built, which is what keeps
     * {@code motionUpdate()} free of the scene graph and therefore unit-testable.
     */
    @Override
    default void positionSprite() {
        positionSpriteAt(getPosition());
    }

    @Override
    default void drawCarriedAt(final PixelPosition anchor) {
        positionSpriteAt(anchor);
    }

    /**
     * Shows the walker or the shell, whichever the state calls for. The feet are driven per frame by
     * {@link #positionSprite()}, so they are hidden outright here.
     */
    @Override
    default void showGeometryForState() {
        final boolean shelled = isShellShowing();
        setVisible(getSpriteGeometry(), !shelled);
        setVisible(getShellGeometry(), shelled);
        setVisible(getLeftShellFootGeometry(), false);
        setVisible(getRightShellFootGeometry(), false);
    }

    /**
     * Where the walking quad is actually translated to, given the feet anchor the position maps to:
     * mirrored when the Koopa Troopa faces right, nudged down a pixel on the odd walk frame.
     *
     * <p>Split out from {@link #positionSprite()} because the mirror compensation is exactly the kind of
     * off-by-one-quad-width that silently misplaces a sprite by 16 px, and is worth being able to assert
     * on without a scene graph.
     *
     * @param feetAnchor the quad's unmirrored bottom-left corner, from {@code pixelToWorldOffset}
     * @return the translation to apply
     */
    default WorldOffset spriteDrawOffset(final WorldOffset feetAnchor) {
        return feetAnchor.plus(mirrorCompensation(getSpriteDimensions().width()), -walkFrameBob(), 0f);
    }

    /**
     * Where the shell quad is translated to: shifted for the mirror, jittered while it shakes, lifted a
     * tile when it is upside down (the flip turns the quad about its bottom edge), and drawn in front of
     * or behind its holder while carried.
     *
     * @param feetAnchor the quad's unmirrored bottom-left corner, from {@code pixelToWorldOffset}
     * @return the translation to apply
     */
    default WorldOffset shellDrawOffset(final WorldOffset feetAnchor) {
        final float invertedLift = isUpsideDown() ? TILE_SIZE_GAME_UNITS : 0f;
        return WorldOffset.of(
            feetAnchor.x()
                + mirrorCompensation(SHELL_DIMENSIONS.width())
                - getShellJitterPixels() * PIXELS_TO_GAME_UNITS,
            feetAnchor.y() + invertedLift,
            carriedDepthOr(feetAnchor.z(), 0f)
        );
    }

    /**
     * The Koopa Troopa faces right by being mirrored rather than by swapping to a second set of art.
     *
     * <p>A negative X scale mirrors the quad about its own origin, and jME puts that origin at the quad's
     * <b>bottom-left</b> corner - so the mirrored sprite would hang a full width to the <em>left</em> of
     * its anchor. Adding one quad width back puts it over the same column it started in. This is the
     * horizontal twin of the vertical flip a tail-struck {@link Goomba} uses, and it relies on the same
     * {@code FaceCullMode.Off} that {@code EnemyAnimator#buildSprite} already sets, since a negative
     * scale reverses the winding.
     *
     * @param quadWidth the width of the quad being placed, in game units
     * @return the X correction to add to the anchor, or zero when the quad is not mirrored
     */
    default float mirrorCompensation(final float quadWidth) {
        return isSpriteMirrored() ? quadWidth : 0f;
    }

    private void positionSpriteAt(final PixelPosition drawPosition) {
        if (!isShellShowing()) {
            positionWalkingSprite();
            return;
        }
        final Geometry shellGeometry = getShellGeometry();
        if (shellGeometry == null) {
            return;
        }
        final WorldOffset feetAnchor = pixelToWorldOffset(drawPosition);
        if (feetAnchor == null) {
            return;
        }
        final WorldOffset shellOffset = shellDrawOffset(feetAnchor);
        shellGeometry.setLocalScale(isSpriteMirrored() ? -1f : 1f, isUpsideDown() ? -1f : 1f, 1f);
        shellOffset.applyTo(shellGeometry);

        positionWakingFoot(getLeftShellFootGeometry(), shellOffset, true);
        positionWakingFoot(getRightShellFootGeometry(), shellOffset, false);
    }

    private void positionWalkingSprite() {
        final WorldOffset feetAnchor = pixelToWorldOffset(getPosition());
        if (feetAnchor == null) {
            return;
        }
        final Geometry spriteGeometry = getSpriteGeometry();
        spriteGeometry.setLocalScale(isSpriteMirrored() ? -1f : 1f, 1f, 1f);
        spriteDrawOffset(feetAnchor).applyTo(spriteGeometry);
    }

    /**
     * Hangs one foot three pixels past its side of the shell, inverted with it, and shows it only on the
     * ticks the flicker says. Drawn just behind this shell's own depth, never at a depth shared with
     * other enemies, so the feet go wherever their shell goes in the draw order.
     */
    private void positionWakingFoot(
        final Geometry footGeometry,
        final WorldOffset shellOffset,
        final boolean leftFoot
    ) {
        if (footGeometry == null) {
            return;
        }
        footGeometry.setCullHint(isWakingFeetShowing() ? Inherit : Always);
        final int outwardPixels = leftFoot
            ? -SHELL_FOOT_OUTWARD_OFFSET_PIXELS
            : SHELL_RIGHT_SPRITE_OFFSET_PIXELS + SHELL_FOOT_OUTWARD_OFFSET_PIXELS;
        footGeometry.setLocalScale(1f, isUpsideDown() ? -1f : 1f, 1f);
        WorldOffset
            .of(
                shellOffset.x() + outwardPixels * PIXELS_TO_GAME_UNITS,
                shellOffset.y(),
                carriedDepthOr(depthBehindBody(), Z_OFFSET_CARRIED_DETAIL_BEHIND_ITS_OBJECT)
            )
            .applyTo(footGeometry);
    }

    private float carriedDepthOr(final float ownDepth, final float detailStep) {
        final LevelScenePlayer carrier = getCarrier();
        if (getState() != HELD || carrier == null) {
            return ownDepth;
        }
        final float holderDepth = carrier.getVisibility().getPlayerZ();
        final float carriedDepth = carrier.getRuntimeState().isTurningAroundWhileCarrying()
            ? holderDepth + Z_OFFSET_CARRIED_IN_FRONT_OF_PLAYER
            : holderDepth - Z_OFFSET_CARRIED_BEHIND_PLAYER;
        return carriedDepth - detailStep;
    }

    /** The odd walk frame is drawn one pixel lower. World Y grows upward, so the caller subtracts this. */
    private float walkFrameBob() {
        return getWalkFrameIndex() % 2 == 0 ? 0f : PIXELS_TO_GAME_UNITS;
    }

    private void setVisible(final Geometry geometry, final boolean visible) {
        if (geometry != null) {
            geometry.setCullHint(visible ? Inherit : Always);
        }
    }
}
