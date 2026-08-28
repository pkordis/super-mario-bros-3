package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.GameConstants;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_TOPPOWERSPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOPRUNSPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOPWALKSPEED;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.DOWN;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.min;

public interface LevelScenePlayerMoveCapable extends LevelScenePlayerDataAware {
    default void handleHorizontalMovement(final PlayerInputHandler inputHandler) {
        final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
        final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();

        // Cancel simultaneous L+R — impossible on a real NES D-pad; on
        // keyboard/gamepad we treat it as no directional input (dasm prg008:
        // original hardware cannot physically produce this combination).
        boolean inputLeft = rawLeft && !rawRight;
        boolean inputRight = rawRight && !rawLeft;

        // Suppress directional acceleration while ducking on the ground —
        // the player should decelerate to a stop via friction only. When
        // duck-jumping (ducking flag set + in air), air control is NOT
        // suppressed (dasm: horizontal control runs normally in air
        // regardless of Player_IsDucking).
        if (runtimeState.isDucking() && !runtimeState.isInAir()) {
            inputLeft = false;
            inputRight = false;
        }

        // Update facing orientation from pad input — in the original game the
        // sprite always faces the pressed direction, including during a skid
        // (dasm prg008 PRG008_AE11–AE24: Player_FlipBits is set directly from
        // Pad_Holding regardless of velocity direction).
        if (inputLeft) {
            setOrientationHorizontal(LEFT);
        } else if (inputRight) {
            setOrientationHorizontal(RIGHT);
        }

        // Determine top speed
        double topSpeed = PLAYER_TOPWALKSPEED;
        if (inputRun) {
            topSpeed = PLAYER_TOPRUNSPEED;
            if (getPlayerData().getPlayerPower() >= PMETER_LEVELS) {
                topSpeed = PLAYER_TOPPOWERSPEED;
            }
        }

        final double accelerationFriction = isLarge() ? 14.0 : 10.0;
        final double accelerationSkid = 32.0;
        final double accelerationNormal = 14.0;

        final int hitDir = inputLeft ? -1 : inputRight ? 1 : 0;
        if (hitDir == 0) {
            if (!runtimeState.isInAir()) {
                if (position.getDX() < 0) {
                    position.addToDX(accelerationFriction / 256.0);
                    if (position.getDX() > 0) {
                        position.setDX(0);
                    }
                } else if (position.getDX() > 0) {
                    position.subtractFromDX(accelerationFriction / 256.0);
                    if (position.getDX() < 0) {
                        position.setDX(0);
                    }
                }
            }
        } else {
            final double absDX = abs(position.getDX());
            if ((position.getDX() > 0 && hitDir < 0) || (position.getDX() < 0 && hitDir > 0)) {
                // Skidding
                position.addToDX(hitDir * accelerationSkid / 256.0);
            } else if (absDX < topSpeed) {
                position.addToDX(hitDir * accelerationNormal / 256.0);
                if (abs(position.getDX()) > topSpeed) {
                    position.setDX(hitDir * topSpeed);
                }
            } else if (absDX > topSpeed) {
                if (!runtimeState.isInAir()) {
                    position.subtractFromDX(hitDir * accelerationFriction / 256.0);
                    if (abs(position.getDX()) < topSpeed) {
                        position.setDX(hitDir * topSpeed);
                    }
                }
            }
        }
    }

    default void handleVerticalMovement(final PlayerInputHandler inputHandler) {
        final boolean jumpHit = inputHandler.consumePress(HANDLER_JUMP);
        final boolean jumpHeld = inputHandler.isActive(HANDLER_JUMP);
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();

        if (jumpHit) {
            if (!runtimeState.isInAir()) {
                final int dx = min(3, (int) floor(abs(position.getDX())));
                position.setDY(GameConstants.JUMP_FORCE[dx]);
                runtimeState.setTo(JUMPING);
                runtimeState.setPlayerWagCount(0);
                // Full P-meter launch grants flyTime to ALL suits (dasm
                // prg008 Player_JumpFlyFlutter: LDA #$80 / STA Player_FlyTime
                // runs before the PowerUp_Ability check). For large suits this
                // enables actual flight/wag Y-effects; for small Mario flyTime
                // > 0 is used only as a visual cue to show PF_FASTJUMPFALLSMALL.
                if (getPlayerData().getPlayerPower() >= PMETER_LEVELS
                    && runtimeState.getPlayerFlyTime() <= 0) {
                    runtimeState.setPlayerFlyTime(GameConstants.FLY_TIME);
                }
            } else if (isLarge()) {
                // Mid-air A press: tail wag
                runtimeState.setPlayerWagCount(GameConstants.WAG_COUNT);
            }
        }

        if (runtimeState.isInAir()) {
            if (position.getDY() < -2 && jumpHeld) {
                position.addToDY(GameConstants.GRAVITY_SLOW / GameConstants.TILE_SPRITE_SIZE);
            } else {
                position.addToDY(GameConstants.GRAVITY_FAST / GameConstants.TILE_SPRITE_SIZE);
            }

            // Raccoon tail wag / flight effects on Y velocity
            if (runtimeState.getPlayerWagCount() > 0) {
                runtimeState.setPlayerWagCount(runtimeState.getPlayerWagCount() - 1);
                if (isLarge() && position.getDY() > GameConstants.PLAYER_FLY_YVEL) {
                    final int flyTime = runtimeState.getPlayerFlyTime();
                    if (flyTime > 0) {
                        if (flyTime >= 0x0f) {
                            position.setDY(GameConstants.PLAYER_FLY_YVEL);
                        } else if ((flyTime & 0x08) != 0) {
                            position.setDY(GameConstants.PLAYER_FLY_APEX_YVEL);
                        } else {
                            position.setDY(0);
                        }
                    } else if (position.getDY() >= GameConstants.PLAYER_TAILWAG_YVEL) {
                        position.setDY(GameConstants.PLAYER_TAILWAG_YVEL);
                    }
                }
            }

            // Raccoon air drag (dasm prg008 PRG008_B082): when flying or
            // wagging, apply 1 raw unit/frame deceleration toward walk speed.
            // This prevents maintaining launch speed indefinitely during flight.
            if (isLarge() && (runtimeState.getPlayerFlyTime() > 0 || runtimeState.getPlayerWagCount() > 0)) {
                final double dx = position.getDX();
                final double absDx = abs(dx);
                if (absDx > PLAYER_TOPWALKSPEED) {
                    if (dx > 0) {
                        position.addToDX(-1.0 / GameConstants.TILE_SPRITE_SIZE);
                    } else {
                        position.addToDX(1.0 / GameConstants.TILE_SPRITE_SIZE);
                    }
                }
            }
        }

        // Track vertical orientation from the resolved DY for this frame.
        // Mirrors how playerOrientationHorizontal reflects the active movement
        // direction: UP when rising (DY < 0), DOWN when falling (DY > 0),
        // SUSTAINED when grounded. This is read by collision handling on the
        // Y axis in the same way horizontal orientation is read on the X axis.
        if (!runtimeState.isInAir()) {
            setOrientationVertical(SUSTAINED);
        } else if (position.getDY() < 0) {
            setOrientationVertical(UP);
        } else {
            setOrientationVertical(DOWN);
        }
    }
}
