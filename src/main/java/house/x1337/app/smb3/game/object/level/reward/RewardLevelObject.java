package house.x1337.app.smb3.game.object.level.reward;

import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.util.GameRenderer;

public interface RewardLevelObject extends ActiveLevelObject, GameRenderer {
    ImageResource getImageResource();
    Reward getRewardType();
    boolean isCollected();
    boolean isExpired();
    void setExpired(boolean expired);

    @Override
    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(
            getPosition(),
            getImageResource().getDimensions()
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

    @Override
    default void expire() {
        setExpired(true);
    }

    @Override
    default boolean isKilledByRammedObject() {
        return false;
    }
}
