package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.KICK_POSE_TICKS;

public interface LevelScenePlayerKickCapable extends LevelScenePlayerRuntimeStateAware{
    default void kickCarriedObject(final CarriableLevelObject carriable) {
        armKickPose();
        carriable.onKicked(asLevelScenePlayer(), getOrientation().getHorizontal() == RIGHT);
    }

    default void kickOnContact(final CarriableLevelObject carriable) {
        armKickPose();
        kickTowards(carriable);
    }

    default void kickWithoutPose(final CarriableLevelObject carriable) {
        kickTowards(carriable);
    }

    default void tickKickPose() {
        getRuntimeState().decrementKick();
    }

    private void armKickPose() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        runtimeState.setKickCountdown(KICK_POSE_TICKS);
        runtimeState.setPlayerTailAttackCountdown(0);
    }

    private void kickTowards(final CarriableLevelObject carriable) {
        final boolean toTheRight = carriable.getPosition().x() > getPosition().getX();
        getOrientation().setHorizontal(toTheRight ? RIGHT : LEFT);
        carriable.onKicked(asLevelScenePlayer(), toTheRight);
    }
}
