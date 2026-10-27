package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;

public interface CarriableLevelObject extends ActiveLevelObject {
    int CARRY_DROP_PIXELS = 13;
    int CARRY_DROP_SMALL_PIXELS = 15;
    int CARRY_HIT_KNOCK_X_VELOCITY_FIXED_POINT = 8;
    int CARRY_SIDE_OFFSET_PIXELS = 11;
    int CARRY_TUCK_SIDE_OFFSET_PIXELS = 4;
    int CARRY_TUCK_SIDE_SWAP_TICKS = 5;
    int KICK_HIT_COOLDOWN_TICKS = 16;
    int KICK_WALL_PROBE_X_VELOCITY_FIXED_POINT = 1;
    int KICK_X_VELOCITY_FIXED_POINT = 48;

    Offset NO_CARRY_ANCHOR_OFFSET = Offset.of(0, 0);

    LevelScenePlayer getCarrier();
    boolean isCarriable();
    boolean isCarried();
    void drawCarriedAt(PixelPosition anchor);
    void onCarried(LevelScenePlayer player);
    void onKicked(LevelScenePlayer player, boolean toTheRight);

    @Override
    default void onCarriedInto(final ActiveLevelObject other) {
        final LevelScenePlayer holder = getCarrier();
        if (holder == null) {
            return;
        }
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        final double otherCenterX = (otherBounds.left() + otherBounds.right()) / 2.0;
        final int knockAway = holder.getPosition().getX() < otherCenterX
            ? CARRY_HIT_KNOCK_X_VELOCITY_FIXED_POINT
            : -CARRY_HIT_KNOCK_X_VELOCITY_FIXED_POINT;

        other.onStruckByKickedObject(knockAway);
        onKilledByCarryContact(holder);
    }

    default void onKilledByCarryContact(final LevelScenePlayer carrier) {
        expire();
    }

    default Offset getCarryAnchorOffset() {
        return NO_CARRY_ANCHOR_OFFSET;
    }

    default PixelPosition carryAnchorFor(final LevelScenePlayer player) {
        return carryAnchorFrom(player, player.getPosition().getX(), player.getPosition().getY());
    }

    default PixelPosition carryAnchorFrom(
        final LevelScenePlayer player,
        final double playerX,
        final double playerY
    ) {
        final int turnAroundTicks = player.getRuntimeState().getTurnAroundFaceCounter();
        final boolean turningAround = turnAroundTicks > 0;
        final int sideOffset = turningAround
            ? CARRY_TUCK_SIDE_OFFSET_PIXELS
            : CARRY_SIDE_OFFSET_PIXELS;
        final boolean onFacingSide = !turningAround || turnAroundTicks < CARRY_TUCK_SIDE_SWAP_TICKS;
        final boolean facingRight = player.getOrientation().getHorizontal() == RIGHT;
        final boolean toTheRight = facingRight == onFacingSide;
        final Offset anchorOffset = getCarryAnchorOffset();
        return new PixelPosition(
            playerX + (toTheRight ? sideOffset : -sideOffset) + anchorOffset.x(),
            playerY + (player.isSmall() ? CARRY_DROP_SMALL_PIXELS : CARRY_DROP_PIXELS) + anchorOffset.y()
        );
    }
}
