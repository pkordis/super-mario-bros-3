package house.x1337.app.smb3.game.object.level.enemy.family;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;

public interface KoopaTroopaBody extends GroundWalkingEnemy {
    boolean isFacingRight();
    int getWalkFrameIndex();
    void setFacingRight(boolean facingRight);
    void setGrounded(boolean grounded);
    void setPosition(PixelPosition position);
    void setVelocity(FixedPointVelocity velocity);

    default boolean isSpriteMirrored() {
        return isFacingRight();
    }

    default WorldOffset spriteDrawOffset(final WorldOffset feetAnchor) {
        return feetAnchor.plus(mirrorCompensation(getSpriteDimensions().width()), -walkFrameBob(), 0f);
    }

    default float mirrorCompensation(final float quadWidth) {
        return isSpriteMirrored() ? quadWidth : 0f;
    }

    default WorldOffset positionWalkingSprite() {
        final WorldOffset feetAnchor = pixelToWorldOffset(getPosition());
        if (feetAnchor == null) {
            return null;
        }
        final Geometry spriteGeometry = getSpriteGeometry();
        spriteGeometry.setLocalScale(isSpriteMirrored() ? -1f : 1f, 1f, 1f);
        spriteDrawOffset(feetAnchor).applyTo(spriteGeometry);
        return feetAnchor;
    }

    default void stepWalkHorizontally() {
        final PixelPosition proposed = getPosition().steppedX(getVelocity());
        if (isWallAhead(proposed)) {
            setFacingRight(!isFacingRight());
            return;
        }
        setPosition(proposed);
    }

    @Override
    default void onSideCollisionWith(final ActiveLevelObject other) {
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        final double otherCenterX = (otherBounds.left() + otherBounds.right()) / 2.0;
        setFacingRight(otherCenterX < getPosition().x() + BODY_SIZE_PIXELS / 2.0);
    }

    default void faceClosestPlayer() {
        final PixelPosition position = getPosition();
        final double center = position.x() + BODY_SIZE_PIXELS / 2.0;
        setFacingRight(getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + BODY_SIZE_PIXELS / 2.0 >= center)
            .orElse(false));
    }

    default boolean isClosestPlayerOnTheLeft() {
        final PixelPosition position = getPosition();
        final double center = position.x() + BODY_SIZE_PIXELS / 2.0;
        return getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + BODY_SIZE_PIXELS / 2.0 < center)
            .orElse(false);
    }

    default void restOn(final double groundAlignedY) {
        setPosition(getPosition().withY(groundAlignedY));
        setVelocity(getVelocity().withY(0));
        setGrounded(true);
    }

    default void applyGravity() {
        setVelocity(getVelocity().falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT));
    }

    default float walkFrameBob() {
        return getWalkFrameIndex() % 2 == 0 ? 0f : PIXELS_TO_GAME_UNITS;
    }
}
