package house.x1337.app.smb3.game.object.level.reward.core;

import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.util.GameRenderer;

public interface RewardLevelObject extends ActiveLevelObject, GameRenderer {
    int POWER_UP_BOUND_BOX_HEIGHT = 8;
    int POWER_UP_BOUND_BOX_LEFT = 1;
    int POWER_UP_BOUND_BOX_TOP = 2;
    int POWER_UP_BOUND_BOX_WIDTH = 13;

    ImageResource getImageResource();
    Reward getRewardType();
    boolean isCollected();
    boolean isExpired();
    void setExpired(boolean expired);

    @Override
    default AxisAlignedBoundingBox getBounds() {
        final PixelPosition position = getPosition();
        final double inclusiveLeft = position.x() + POWER_UP_BOUND_BOX_LEFT;
        final double inclusiveTop = position.y() + POWER_UP_BOUND_BOX_TOP;
        return new AxisAlignedBoundingBox(
            inclusiveLeft - 1,
            inclusiveTop - 1,
            inclusiveLeft + POWER_UP_BOUND_BOX_WIDTH + 1,
            inclusiveTop + POWER_UP_BOUND_BOX_HEIGHT + 1
        );
    }

    default void motionUpdate() {
        // No motion by default
    }

    default boolean isCollectable() {
        return true;
    }

    default boolean detachesOnCollect() {
        return false;
    }

    default boolean isExemptFromDeletionOffScreen() {
        return false;
    }

    @Override
    default void expire() {
        setExpired(true);
    }

    @Override
    default boolean isKilledByRammedObject() {
        return false;
    }
}
