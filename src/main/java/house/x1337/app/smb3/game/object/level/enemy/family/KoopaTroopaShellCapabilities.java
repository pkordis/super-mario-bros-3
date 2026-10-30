package house.x1337.app.smb3.game.object.level.enemy.family;

import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.KillTallyAware;
import house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.HELD;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KICKED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static java.lang.Integer.signum;
import static java.lang.Math.round;

/**
 * Everything a shell does: going into it and waking out of it, resting and bouncing, being picked up,
 * carried and kicked, rolling, and the kills it deals and takes on the way.
 */
public interface KoopaTroopaShellCapabilities extends GroundWalkingEnemy, CarriableLevelObject, KillTallyAware {
    int CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT = 16;
    int CRUSH_LAUNCH_Y_VELOCITY_FIXED_POINT = -64;
    int SHELL_CHAIN_KNOCK_X_VELOCITY_FIXED_POINT = 16;
    int SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT = EnemyKnockMotion.SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT;
    int SHELL_LANDING_X_DAMPING = 2;
    int SHELL_LANDING_Y_DAMPING = 4;
    int SHELL_MIN_BOUNCE_Y_VELOCITY_FIXED_POINT = -2;
    int SHELL_ROLL_RE_DROP_Y_VELOCITY_FIXED_POINT = 13;
    int SHELL_ROLL_X_VELOCITY_FIXED_POINT = 24;
    int SHELL_SHAKE_THRESHOLD_TICKS = 96;
    int SHELL_SPIN_BEATS = 4;
    int SHELL_SPIN_TICKS_PER_BEAT = 2;
    int WAKE_UP_THROTTLE_MASK_HELD = 3;
    int WAKE_UP_THROTTLE_MASK_SHELLED = 1;
    int WAKE_UP_TICKS = 255;

    KoopaTroopaState getState();
    boolean isFacingRight();
    boolean isUpsideDown();
    int getHitCooldownTicks();
    int getSpinTicks();
    int getWakeUpThrottlePhase();
    int getWakeUpTicks();
    void positionSprite();
    void refreshShellFrame();
    void setCarrier(LevelScenePlayer carrier);
    void setFacingRight(boolean facingRight);
    void setGrounded(boolean grounded);
    void setHitCooldownTicks(int hitCooldownTicks);
    void setKillTally(int killTally);
    void setLedgeTurned(boolean ledgeTurned);
    void setPosition(PixelPosition position);
    void setSpinTicks(int spinTicks);
    void setState(KoopaTroopaState state);
    void setUpsideDown(boolean upsideDown);
    void setVelocity(FixedPointVelocity velocity);
    void setWakeUpThrottlePhase(int wakeUpThrottlePhase);
    void setWakeUpTicks(int wakeUpTicks);
    void showGeometryForState();

    default void enterShell() {
        endCarry();
        setState(SHELLED);
        setWakeUpTicks(WAKE_UP_TICKS);
        setWakeUpThrottlePhase(0);
        setLedgeTurned(false);
        setKillTally(0);
        setSpinTicks(0);
        showGeometryForState();
        refreshShellFrame();
        positionSprite();
    }

    default void wakeUp() {
        endCarry();
        setState(NORMAL);
        setUpsideDown(false);
        faceClosestPlayer();
        showGeometryForState();
        positionSprite();
    }

    default void runWakeUpClock() {
        final KoopaTroopaState state = getState();
        if ((state != SHELLED && state != HELD) || getWakeUpTicks() == 0) {
            return;
        }
        setWakeUpThrottlePhase((getWakeUpThrottlePhase() + 1) & WAKE_UP_THROTTLE_MASK_HELD);
        final int throttleMask = state == HELD
            ? WAKE_UP_THROTTLE_MASK_HELD
            : WAKE_UP_THROTTLE_MASK_SHELLED;
        if (getWakeUpTicks() >= SHELL_SHAKE_THRESHOLD_TICKS && (getWakeUpThrottlePhase() & throttleMask) != 0) {
            return;
        }
        setWakeUpTicks(getWakeUpTicks() - 1);
    }

    default void moveShellHorizontally() {
        final FixedPointVelocity velocity = getVelocity();
        if (velocity.stationaryX()) {
            return;
        }
        final PixelPosition proposed = getPosition().steppedX(velocity);
        if (isWallAhead(proposed)) {
            setFacingRight(!isFacingRight());
            setVelocity(velocity.negatedX());
            return;
        }
        setPosition(proposed);
    }

    default void moveShellVertically() {
        final FixedPointVelocity velocity = getVelocity();
        final PixelPosition proposed = getPosition().steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            final int impactYVelocityFixedPoint = velocity.y();
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            restOn((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
            dampenLanding(impactYVelocityFixedPoint);
            return;
        }
        if (velocity.rising() && isCeilingAbove(proposed)) {
            final int headRow = proposed.row();
            setPosition(getPosition().withY((double) (headRow + 1) * TILE_SPRITE_SIZE));
            setVelocity(velocity.withY(CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT));
            setGrounded(false);
            return;
        }
        setPosition(proposed);
        setGrounded(false);
    }

    private void dampenLanding(final int impactYVelocityFixedPoint) {
        setVelocity(getVelocity().withX(getVelocity().x() / SHELL_LANDING_X_DAMPING));
        final int reboundYVelocity = -(impactYVelocityFixedPoint / SHELL_LANDING_Y_DAMPING);
        if (reboundYVelocity < SHELL_MIN_BOUNCE_Y_VELOCITY_FIXED_POINT) {
            setVelocity(getVelocity().withY(reboundYVelocity));
            setGrounded(false);
        }
    }

    default void holdKickOrHurt(final LevelScenePlayer levelScenePlayer) {
        if (!isCarriable()) {
            hurt(levelScenePlayer);
            return;
        }
        if (!levelScenePlayer.canPickUpCarriable()) {
            return;
        }
        if (!levelScenePlayer.isCarryButtonHeld()) {
            levelScenePlayer.kickOnContact(this);
            return;
        }
        levelScenePlayer.pickUp(this);
    }

    @Override
    default boolean isCarriable() {
        return getState() == SHELLED && getHitCooldownTicks() == 0;
    }

    @Override
    default boolean isCarried() {
        return getState() == HELD;
    }

    @Override
    default boolean isCarriedHazard() {
        return getState() == HELD && getHitCooldownTicks() == 0;
    }

    @Override
    default void onCarried(final LevelScenePlayer levelScenePlayer) {
        setCarrier(levelScenePlayer);
        setState(HELD);
        setVelocity(STILL);
        setGrounded(false);
        showGeometryForState();
        setPosition(carryAnchorFor(levelScenePlayer));
        positionSprite();
    }

    default void tickHeld() {
        runWakeUpClock();
        if (getWakeUpTicks() == 0) {
            breakFreeOfTheHands();
            return;
        }
        final LevelScenePlayer carrier = getCarrier();
        if (carrier == null) {
            // Defensive only: nothing clears the carrier without leaving this state, and the ROM has no
            // equivalent branch because a held object's holder is whichever player is being drawn. Falling
            // back to a resting shell keeps the object reachable rather than stranding it mid-carry.
            endCarry();
            setState(SHELLED);
            setVelocity(STILL);
            setGrounded(false);
            showGeometryForState();
            positionSprite();
            return;
        }
        if (!carrier.isCarryButtonHeld()) {
            carrier.releaseCarriedObject();
            return;
        }
        setPosition(carryAnchorFor(carrier));
    }

    default void endCarry() {
        final LevelScenePlayer holder = getCarrier();
        setCarrier(null);
        if (holder == null || holder.getRuntimeState().getCarriedObject() != this) {
            return;
        }
        // The turn-around pose is deliberately left running. PRG008_AF2F reaches the decrement-and-draw
        // on Player_PipeFace alone, before it tests Player_IsHolding, so a player who lets go two ticks
        // into the pose keeps facing the camera for the remaining five.
        holder.getRuntimeState().setCarriedObject(null);
    }

    @Override
    default void onKilledByCarryContact(final LevelScenePlayer carrier) {
        final int inheritedXVelocity = carrier == null ? 0 : playerXVelocityFixedPoint(carrier);
        setPendingScoreReward(SCORE_100);
        onStruckByKickedObject(inheritedXVelocity);
    }

    private void breakFreeOfTheHands() {
        if (isPressedIntoTheWorld()) {
            endCarry();
            crushUpward();
            return;
        }
        // wakeUp() ends the carry itself, as every exit from the held state does.
        wakeUp();
    }

    private boolean isPressedIntoTheWorld() {
        final PixelPosition position = getPosition();
        final StaticEnvironmentCollisionGrid collisionGrid = getCollisionGrid();
        final int centerColumn = position.plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int midRow = position.plusY(BODY_SIZE_PIXELS / 2.0).row();
        return collisionGrid.isWallOrCeilingSolidTile(position.column(), midRow)
            || collisionGrid.isWallOrCeilingSolidTile(position.plusX(BODY_SIZE_PIXELS - 1).column(), midRow)
            || collisionGrid.isWallOrCeilingSolidTile(centerColumn, position.row())
            || collisionGrid.isGroundSolidTile(centerColumn, position.plusY(BODY_SIZE_PIXELS).row());
    }

    @Override
    default void onKicked(
        final LevelScenePlayer levelScenePlayer,
        final boolean toTheRight
    ) {
        final boolean outOfTheHands = getState() == HELD;
        setHitCooldownTicks(KICK_HIT_COOLDOWN_TICKS);
        setKillTally(0);
        setSpinTicks(0);
        endCarry();

        if (outOfTheHands && isWallBesideKick(toTheRight)) {
            crushUpward();
            return;
        }

        setVelocity(new FixedPointVelocity(kickXVelocity(levelScenePlayer, toTheRight), 0));
        setState(KICKED);
        setGrounded(false);
        showGeometryForState();
        refreshShellFrame();
        positionSprite();
    }

    @Override
    default boolean isKickedHazard() {
        return getState() == KICKED;
    }

    default boolean isRolling() {
        return getState() == KICKED;
    }

    default int getSpinBeat() {
        return getSpinTicks() / SHELL_SPIN_TICKS_PER_BEAT % SHELL_SPIN_BEATS;
    }

    default boolean isPlayerChasingThisShell(final LevelScenePlayer levelScenePlayer) {
        final int playerXVelocity = playerXVelocityFixedPoint(levelScenePlayer);
        if (playerXVelocity == 0 || signum(playerXVelocity) != signum(getVelocity().x())) {
            return false;
        }
        final boolean shellIsToTheRight = getPosition().x() > levelScenePlayer.getPosition().getX();
        return shellIsToTheRight == (playerXVelocity > 0);
    }

    default void tickKicked() {
        moveKickedHorizontally();
        final boolean landed = moveKickedVertically();
        applyGravity();
        if (landed) {
            setVelocity(getVelocity().withY(SHELL_ROLL_RE_DROP_Y_VELOCITY_FIXED_POINT));
        }
        advanceSpinFrame();
        positionSprite();
    }

    default int rollLaunchXVelocity() {
        return isClosestPlayerOnTheLeft()
            ? -SHELL_ROLL_X_VELOCITY_FIXED_POINT
            : SHELL_ROLL_X_VELOCITY_FIXED_POINT;
    }

    private void moveKickedHorizontally() {
        final FixedPointVelocity velocity = getVelocity();
        if (velocity.stationaryX()) {
            return;
        }
        final PixelPosition proposed = getPosition().steppedX(velocity);
        if (isWallAhead(proposed)) {
            bumpOffBlocks(proposed);
            // Object_AboutFace (dasm prg000.asm:2567): the facing bit and the velocity both reverse.
            setFacingRight(!isFacingRight());
            setVelocity(velocity.negatedX());
            return;
        }
        setPosition(proposed);
    }

    private boolean moveKickedVertically() {
        final PixelPosition proposed = getPosition().steppedY(getVelocity());
        if (!getVelocity().descending() || !isGroundBelow(proposed)) {
            setPosition(proposed);
            setGrounded(false);
            return false;
        }
        if (getVelocity().stationaryX()) {
            setVelocity(getVelocity().withX(rollLaunchXVelocity()));
        }
        final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
        setPosition(getPosition().withY((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS));
        setGrounded(true);
        return true;
    }

    private void bumpOffBlocks(final PixelPosition proposed) {
        final PixelPosition position = getPosition();
        final double probeX = getVelocity().movingRight()
            ? proposed.x() + BODY_SIZE_PIXELS - 1
            : proposed.x();
        final double probeY = position.y() + BODY_SIZE_PIXELS / 2.0;
        final StaticEnvironmentCollisionGrid collisionGrid = getCollisionGrid();
        collisionGrid
            .findClosestPlayerTo(position.x(), position.y())
            .ifPresent(player -> collisionGrid.resolveTailAttack(player, probeX, probeY));
    }

    private void advanceSpinFrame() {
        final int previousBeat = getSpinBeat();
        setSpinTicks(getSpinTicks() + 1);
        if (getSpinBeat() != previousBeat) {
            refreshShellFrame();
        }
    }

    private boolean isWallBesideKick(final boolean toTheRight) {
        setVelocity(getVelocity().withX(toTheRight
            ? KICK_WALL_PROBE_X_VELOCITY_FIXED_POINT
            : -KICK_WALL_PROBE_X_VELOCITY_FIXED_POINT));
        return isWallAhead(getPosition());
    }

    private int kickXVelocity(
        final LevelScenePlayer levelScenePlayer,
        final boolean toTheRight
    ) {
        final int base = toTheRight ? KICK_X_VELOCITY_FIXED_POINT : -KICK_X_VELOCITY_FIXED_POINT;
        final int playerXVelocity = playerXVelocityFixedPoint(levelScenePlayer);
        if (signum(base) != signum(playerXVelocity)) {
            return base;
        }
        return base + (playerXVelocity >> 1);
    }

    @Override
    default void onKickedInto(final ActiveLevelObject other) {
        if (getState() != KICKED) {
            return;
        }
        // ASL on a zero X velocity leaves the carry clear, so a shell that has just landed and not yet
        // re-launched knocks rightward - which is what the ROM's BCC/BCS pair picks in that case too.
        final int knockAway = getVelocity().movingLeft()
            ? -SHELL_CHAIN_KNOCK_X_VELOCITY_FIXED_POINT
            : SHELL_CHAIN_KNOCK_X_VELOCITY_FIXED_POINT;
        final boolean mutualKick = other.isKickedHazard();
        final int knockBack = other.getVelocity().movingLeft()
            ? SHELL_CHAIN_KNOCK_X_VELOCITY_FIXED_POINT
            : -SHELL_CHAIN_KNOCK_X_VELOCITY_FIXED_POINT;

        other.onStruckByKickedObject(knockAway);
        if (mutualKick) {
            onStruckByKickedObject(knockBack);
            return;
        }
        setPendingScoreReward(resolveKillReward());
        setKillTally(nextKillTally());
    }

    @Override
    default void onStruckByKickedObject(final int knockXVelocityFixedPoint) {
        if (getState() == KILLED) {
            return;
        }
        if (getState() == KICKED) {
            setPendingScoreReward(resolveKillReward());
        }
        endCarry();
        setState(KILLED);
        setKillTally(0);
        setVelocity(new FixedPointVelocity(knockXVelocityFixedPoint, SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT));
        setGrounded(false);
        showGeometryForState();
        positionSprite();
    }

    default void tickKilled() {
        setPosition(getPosition().stepped(getVelocity()));
        applyGravity();
        positionSprite();
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

    /**
     * Killed on the spot for 100 points and thrown straight up - a shell kicked into a wall it is already
     * touching, or one that wakes up inside the terrain while held.
     */
    private void crushUpward() {
        setPendingScoreReward(SCORE_100);
        setState(KILLED);
        setVelocity(new FixedPointVelocity(0, CRUSH_LAUNCH_Y_VELOCITY_FIXED_POINT));
        setGrounded(false);
        showGeometryForState();
        positionSprite();
    }

    private int playerXVelocityFixedPoint(final LevelScenePlayer levelScenePlayer) {
        return (int) round(levelScenePlayer.getPosition().getDX() * TILE_SPRITE_SIZE);
    }
}
