package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VEL_THRESHOLD;
import static house.x1337.app.smb3.GameConstants.PLAYER_SPREAD_EAGLE_THRESHOLD;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static java.lang.Math.abs;

public interface LevelScenePlayerRuntimeStateAware
    extends
        LevelScenePlayerOrientationAware,
        LevelScenePlayerPositionAware,
        Player {
    PlayerRuntimeState getRuntimeState();

    default int determineHeightOffset() {
        return (isSmall() || getRuntimeState().isDucking()) ? 20 : 10;
    }

    default boolean isLowClearance(
        final StaticEnvironmentCollisionGrid collisionGrid,
        final int heightOffset
    ) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        // Probe at horizontal center (X+8) only — matching dasm PRG008_A77E which
        // checks a single fixed point above the player's head. Adding a right-edge
        // probe (X+14) at the same heightOffset falsely detects a normal rightward
        // wall collision as low-clearance, causing the player to slide through walls.
        final boolean tileAbove = collisionGrid.collidesAtOffset(this, Offset.of(8, heightOffset)) &&
            !collisionGrid.isOneWayTileFromPlayer(this, 8, heightOffset);
        return tileAbove && !runtimeState.isInAir();
    }

    /**
     * Refines the player state after collision resolution. Ground states are
     * determined by velocity; air states are set during jump initiation and
     * collision (landing / walking off ledge).
     *
     * <p>Ducking is now a separate flag on {@code PlayerRuntimeState} and does
     * not participate in the movement state machine. The animator is
     * responsible for rendering the duck frame when the flag is set.
     *
     * @param inputHandler
     * @param hitSomething true if the player collided with a horizontal wall this
     *                     frame (dasm prg008 PRG008_B4F3: INC Player_WalkAnimTicks
     *                     on wall hit keeps the walk animation running)
     * @param lowClearance true if the player is in low-clearance slide mode
     */
    default void refinePlayerState(
        final PlayerInputHandler inputHandler,
        final boolean hitSomething,
        final boolean lowClearance
    ) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();

        if (runtimeState.isInAir()) {
            if (runtimeState.getPlayerFlyTime() > 0 && isLarge()) {
                // dasm prg008: Player_FlyTime > 0 means the player is in
                // powered flight mode (raccoon/tanooki). The animation system
                // (Player_AnimTailWag) selects flying frames whenever FlyTime
                // is nonzero, independent of WagCount. WagCount only controls
                // the velocity cap physics, not the logical flight state.
                //
                // Small Mario also receives FlyTime on full-P launch (the dasm
                // grants it to all suits before the ability check), but it has
                // no flight Y-effects and uses JUMPING/FALLING states with the
                // PF_FASTJUMPFALLSMALL visual frame instead of FLYING.
                runtimeState.setTo(FLYING);
            } else if (position.getDY() < 0) {
                runtimeState.setTo(JUMPING);
            } else {
                runtimeState.setTo(FALLING);
            }
        } else {
            final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
            final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
            final boolean inputLeft = rawLeft && !rawRight;
            final boolean inputRight = rawRight && !rawLeft;
            if (isCurrentlySkidding(inputLeft, inputRight)) {
                runtimeState.setTo(SKIDDING);
            } else if (lowClearance && (inputLeft || inputRight)) {
                // During low clearance slide, show walk animation only when
                // a direction is held (dasm: WalkAnimTicks advances via
                // Player_GroundHControl which reads Pad_Holding).
                runtimeState.setTo(WALKING);
            } else if (hitSomething && (inputLeft || inputRight)) {
                // dasm prg008 PRG008_B4F3: when the player hits a wall while
                // pressing a direction, Player_WalkAnimTicks is incremented
                // which keeps the walk animation cycling. The player appears to
                // "walk in place" against the wall rather than going still.
                runtimeState.setTo(WALKING);
            } else if (abs(position.getDX()) < 0.01) {
                runtimeState.setTo(STILL);
            } else if (runtimeState.isRunning() && abs(position.getDX()) >= PLAYER_SPREAD_EAGLE_THRESHOLD) {
                // Spread-eagle: abs(XVel) >= $37 in the original (prg008.asm
                // Player_SetSpecialFrames). Full P-meter speed reached.
                runtimeState.setTo(POWER_RUNNING);
            } else if (runtimeState.isRunning()) {
                // B held, speed >= TOPRUNSPEED but below spread-eagle threshold.
                // Still uses walk animation frames (accelerating toward max).
                runtimeState.setTo(RUNNING);
            } else {
                runtimeState.setTo(WALKING);
            }
        }
    }

    default boolean isCurrentlySkidding(
        final boolean inputLeft,
        final boolean inputRight
    ) {
        if (getRuntimeState().isInAir()) {
            return false;
        }
        final double dx = getPosition().getDX();
        if (abs(dx) < PLAYER_SKID_VEL_THRESHOLD) {
            return false;
        }
        // Pressing opposite direction from current movement
        return (dx > 0 && inputLeft) || (dx < 0 && inputRight);
    }
}
