package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_TOPRUNSPEED;
import static house.x1337.app.smb3.GameConstants.PMETER_CHARGE_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_DRAIN_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_FULL_HOLD_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static java.lang.Math.abs;

public interface LevelScenePlayerDataAware extends LevelScenePlayerRuntimeStateAware {
    PlayerData getPlayerData();

    default void handleFlyTimeCountdown() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (runtimeState.getPlayerFlyTime() > 0) {
            runtimeState.setFlyTimeToggle(runtimeState.getFlyTimeToggle() ^ 1);
            if (runtimeState.getFlyTimeToggle() != 0) {
                runtimeState.setPlayerFlyTime(runtimeState.getPlayerFlyTime() - 1);
                if (runtimeState.getPlayerFlyTime() <= 0) {
                    getPlayerData().setPlayerPower(0);
                    getPlayerData().setPlayerPowerThrottle(0);
                }
            }
        }
    }

    default void handlePowerMeterAndRunFlag(final PlayerInputHandler inputHandler) {
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        final PlayerData playerData = getPlayerData();

        // Player_RunFlag: set when on ground, holding B, speed >= TOPRUNSPEED
        final boolean running = inputRun && !runtimeState.isInAir()
            && abs(position.getDX()) >= PLAYER_TOPRUNSPEED - 0.001;
        runtimeState.setRunning(running);

        // P-meter update
        if (runtimeState.getPlayerFlyTime() <= 0) {
            final int throttle = playerData.getPlayerPowerThrottle();
            if (throttle > 0) {
                playerData.setPlayerPowerThrottle(throttle - 1);
            } else {
                final int power = playerData.getPlayerPower();
                if (running) {
                    if (power < PMETER_LEVELS) {
                        playerData.setPlayerPower(power + 1);
                        playerData.setPlayerPowerThrottle(PMETER_CHARGE_FRAMES);
                    } else {
                        playerData.setPlayerPowerThrottle(PMETER_FULL_HOLD_FRAMES);
                    }
                } else {
                    if (power > 0) {
                        playerData.setPlayerPower(power - 1);
                        playerData.setPlayerPowerThrottle(PMETER_DRAIN_FRAMES);
                    }
                }
            }
        }
    }
}
