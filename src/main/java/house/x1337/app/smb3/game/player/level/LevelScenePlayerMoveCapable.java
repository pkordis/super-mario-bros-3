package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.GameConstants;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.GRAVITY_FAST;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_POWER_SPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_RUN_SPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOP_WALK_SPEED;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.DOWN;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.enumeration.SoundEffect.JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.TURN_AROUND_FACE_TICKS;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.min;

public interface LevelScenePlayerMoveCapable extends LevelScenePlayerDataAware {
    default void handleHorizontalMovement(final PlayerInputHandler inputHandler) {
        final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
        final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerOrientation orientation = getOrientation();
        final PlayerPosition position = getPosition();

        // Cancel simultaneous L+R — a D-pad cannot physically produce this combination; on
        // keyboard/gamepad we treat it as no directional input.
        boolean inputLeft = rawLeft && !rawRight;
        boolean inputRight = rawRight && !rawLeft;

        // Suppress directional acceleration while ducking on the ground —
        // the player should decelerate to a stop via friction only. When
        // duck-jumping (ducking flag set + in air), air control is NOT
        // suppressed (horizontal control runs normally in air
        // regardless of the ducking flag).
        if (runtimeState.isDucking() && !runtimeState.isInAir()) {
            inputLeft = false;
            inputRight = false;
        }

        // Update facing orientation from pad input — the sprite always faces the pressed direction,
        // including during a skid (the facing is set directly from the held direction regardless of
        // velocity direction).
        //
        // Except during a tail attack, which skips the facing update outright for the whole countdown,
        // before the pad is even read. The swing owns the facing while it runs: the tail animation mirrors
        // the sprite partway through and mirrors it back at the end, and a held direction overwriting the
        // facing in between would both cancel that spin and drag the tail's hitbox onto the wrong side
        // mid-swing. Acceleration is deliberately left running — the skip covers the facing store only.
        final boolean tailAttacking = runtimeState.getPlayerTailAttackCountdown() > 0;
        if (!tailAttacking) {
            final PlayerOrientationHorizontal facingBefore = orientation.getHorizontal();
            if (inputLeft) {
                orientation.setHorizontal(LEFT);
            } else if (inputRight) {
                orientation.setHorizontal(RIGHT);
            }
            // Turning around with something in your hands briefly faces the camera (while carrying, a
            // facing that differs from last tick's arms the turn-around face timer). Armed here because
            // this is the only place the facing is written, so "changed since last tick" and "changed
            // by this store" are the same question.
            if (runtimeState.isCarrying() && orientation.getHorizontal() != facingBefore) {
                runtimeState.setTurnAroundFaceCounter(TURN_AROUND_FACE_TICKS);
            }
        }

        // Determine top speed
        double topSpeed = PLAYER_TOP_WALK_SPEED;
        if (inputRun) {
            topSpeed = PLAYER_TOP_RUN_SPEED;
            if (getPlayerData().getPlayerPower() >= PMETER_LEVELS) {
                topSpeed = PLAYER_TOP_POWER_SPEED;
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
        final PlayerOrientation orientation = getOrientation();
        final PlayerPosition position = getPosition();

        if (jumpHit) {
            if (!runtimeState.isInAir()) {
                final int dx = min(3, (int) floor(abs(position.getDX())));
                position.setDY(GameConstants.JUMP_FORCE[dx]);
                runtimeState.setTo(JUMPING);
                runtimeState.setPlayerWagCount(0);
                getSoundPlayer().play(JUMP);
                // Full P-meter launch grants flyTime to ALL suits (the flight
                // time is set before the suit's abilities are checked). For large suits this
                // enables actual flight/wag Y-effects; for small Mario flyTime
                // > 0 is used only as a visual cue to show the fast jump/fall pose.
                if (getPlayerData().getPlayerPower() >= PMETER_LEVELS
                    && runtimeState.getPlayerFlyTime() <= 0) {
                    runtimeState.setPlayerFlyTime(GameConstants.FLY_TIME);
                }
            } else if (hasTail()) {
                // Mid-air A press: tail wag (raccoon/tanooki only; big Mario is
                // tailless and small Mario cannot, so both skip this).
                runtimeState.setPlayerWagCount(GameConstants.WAG_COUNT);
            }
        }

        if (runtimeState.isInAir()) {
            if (position.getDY() < -2 && jumpHeld) {
                position.addToDY(GRAVITY_SLOW / TILE_SPRITE_SIZE);
            } else {
                position.addToDY(GRAVITY_FAST / TILE_SPRITE_SIZE);
            }

            // Raccoon tail wag / flight effects on Y velocity
            if (runtimeState.getPlayerWagCount() > 0) {
                runtimeState.setPlayerWagCount(runtimeState.getPlayerWagCount() - 1);
                if (hasTail() && position.getDY() > GameConstants.PLAYER_FLY_Y_VELOCITY) {
                    final int flyTime = runtimeState.getPlayerFlyTime();
                    if (flyTime > 0) {
                        if (flyTime >= 15) {
                            position.setDY(GameConstants.PLAYER_FLY_Y_VELOCITY);
                        } else if ((flyTime & 8) != 0) {
                            position.setDY(GameConstants.PLAYER_FLY_APEX_Y_VELOCITY);
                        } else {
                            position.setDY(0);
                        }
                    } else if (position.getDY() >= GameConstants.PLAYER_TAIL_WAG_Y_VELOCITY) {
                        position.setDY(GameConstants.PLAYER_TAIL_WAG_Y_VELOCITY);
                    }
                }
            }

            // Raccoon air drag: when flying or
            // wagging, apply 1 raw unit/frame deceleration toward walk speed.
            // This prevents maintaining launch speed indefinitely during flight.
            if (hasTail() && (runtimeState.getPlayerFlyTime() > 0 || runtimeState.getPlayerWagCount() > 0)) {
                final double dx = position.getDX();
                final double absDx = abs(dx);
                if (absDx > PLAYER_TOP_WALK_SPEED) {
                    if (dx > 0) {
                        position.addToDX(-1.0 / TILE_SPRITE_SIZE);
                    } else {
                        position.addToDX(1.0 / TILE_SPRITE_SIZE);
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
            orientation.setVertical(SUSTAINED);
        } else if (position.getDY() < 0) {
            orientation.setVertical(UP);
        } else {
            orientation.setVertical(DOWN);
        }
    }
}
