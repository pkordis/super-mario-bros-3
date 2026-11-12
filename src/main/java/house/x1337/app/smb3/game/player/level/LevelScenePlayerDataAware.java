package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.event.PlayerPowerLost;
import house.x1337.app.smb3.model.event.PlayerPowerReached;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_RUN_SPEED;
import static house.x1337.app.smb3.GameConstants.PMETER_CHARGE_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_DRAIN_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_FULL_HOLD_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static java.lang.Math.abs;

/**
 * The player's P-meter and flight time. The meter becoming full publishes {@link PlayerPowerReached}, and a full meter
 * draining a step or emptied when its flight ends publishes {@link PlayerPowerLost}.
 */
public interface LevelScenePlayerDataAware extends LevelScenePlayerRuntimeStateAware, GameEngineAware {
    PlayerData getPlayerData();

    default void handleFlyTimeCountdown() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (runtimeState.getPlayerFlyTime() > 0) {
            runtimeState.setFlyTimeToggle(runtimeState.getFlyTimeToggle() ^ 1);
            if (runtimeState.getFlyTimeToggle() != 0) {
                runtimeState.setPlayerFlyTime(runtimeState.getPlayerFlyTime() - 1);
                if (runtimeState.getPlayerFlyTime() <= 0) {
                    final boolean wasFull = getPlayerData().getPlayerPower() >= PMETER_LEVELS;
                    getPlayerData().setPlayerPower(0);
                    getPlayerData().setPowerThrottle(0);
                    if (wasFull) {
                        getGameEngine().publish(new PlayerPowerLost());
                    }
                }
            }
        }
    }

    default void handlePowerMeterAndRunFlag(final PlayerInputHandler inputHandler) {
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        final PlayerData playerData = getPlayerData();

        // Run flag: set when on ground, holding B, speed >= PLAYER_TOP_RUN_SPEED
        final boolean running = inputRun && !runtimeState.isInAir()
            && abs(position.getDX()) >= PLAYER_TOP_RUN_SPEED - 0.001;
        runtimeState.setRunning(running);

        // P-meter update
        if (runtimeState.getPlayerFlyTime() <= 0) {
            final int throttle = playerData.getPowerThrottle();
            if (throttle > 0) {
                playerData.setPowerThrottle(throttle - 1);
            } else {
                final int power = playerData.getPlayerPower();
                if (running) {
                    if (power < PMETER_LEVELS) {
                        playerData.setPlayerPower(power + 1);
                        playerData.setPowerThrottle(PMETER_CHARGE_FRAMES);
                        if (power + 1 == PMETER_LEVELS) {
                            getGameEngine().publish(new PlayerPowerReached());
                        }
                    } else {
                        playerData.setPowerThrottle(PMETER_FULL_HOLD_FRAMES);
                    }
                } else {
                    if (power > 0) {
                        playerData.setPlayerPower(power - 1);
                        playerData.setPowerThrottle(PMETER_DRAIN_FRAMES);
                        if (power == PMETER_LEVELS) {
                            getGameEngine().publish(new PlayerPowerLost());
                        }
                    }
                }
            }
        }
    }
}
