package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.reward.RewardLevelObject;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.game.player.factory.PlayerAnimatorFactory.contextForLevel;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_SIZE_TOGGLE;
import static java.lang.Math.clamp;
import static java.lang.Math.min;

/**
 * Player physics and rendering controller implementing SMB3 movement mechanics.
 *
 * <p>This is a direct port of the SMB3 disassembly physics simulation (JS reference)
 * into a jMonkeyEngine + dyn4j environment, following the injection pattern from
 * PixelLinePlatformer's Player class.
 *
 * <p>The player's appearance is initially a colored box (cyan) matching the JS
 * reference. Collision is resolved against tiles whose {@link TileType} category
 * is {@link TileType.Category#COLLIDING}.
 */
@Data
@Slf4j
@Prototype
public final class LevelScenePlayer implements LevelScenePlayerCapabilities {
    // Raccoon tail-attack hitbox, in sprite-pixel space (dasm prg000 Object_RespondToTailAttack /
    // Player_TailAttackXOff): offset to the side the player faces, 10 wide, 15 tall, covering the
    // lower body. The swing "kicks" (registers a hit) only on the two countdown frames $0C and $09.
    private static final double TAIL_ATTACK_X_OFFSET_RIGHT = 17;
    private static final double TAIL_ATTACK_X_OFFSET_LEFT = -10;
    private static final double TAIL_ATTACK_WIDTH = 10;
    private static final double TAIL_ATTACK_Y_OFFSET = 16;
    private static final double TAIL_ATTACK_HEIGHT = 15;
    private static final int TAIL_ATTACK_STRIKE_FRAME_EARLY = 12;
    private static final int TAIL_ATTACK_STRIKE_FRAME_LATE = 9;

    private final LevelScenePlayerAnimationContext animationContext;
    private final PlayerInputHandler inputHandler;
    private final PlayerRuntimeState runtimeState;
    private final PlayerPosition position;
    private final PlayerOrientation orientation;
    private final PlayerData playerData;
    private final GameEngine gameEngine;

    private PlayerVisibility visibility = FOREGROUND;
    private LevelSceneVerticalScroll verticalScroll;
    private PlayerMode mode;
    private Node node;

    public LevelScenePlayer(
        final GameEngine gameEngine,
        final PlayerData playerData
    ) {
        this.gameEngine = gameEngine;
        this.playerData = playerData;
        this.inputHandler = getBean(
            PlayerInputHandler.class,
            gameEngine
        );
        this.position = initializePosition();
        this.runtimeState = getBean(PlayerRuntimeState.class);
        this.verticalScroll = getBean(LevelSceneVerticalScroll.class, getLevelScene());
        this.animationContext = contextForLevel(this);
        this.orientation = new PlayerOrientation(RIGHT, SUSTAINED);
    }

    /**
     * The player's hitbox for collision against dynamic {@code ActiveLevelObject}s, in sprite-pixel
     * space. Horizontally it matches the solid-collision stop offsets (X spans +2..+14 large, +3..+13
     * small/ducking) so it never pokes past where the body rests against a wall; the bottom sits at
     * +32; the top at +6 when large and standing, or +16 when small or ducking.
     *
     * <p>This is the "hoist" point for object collision — compute it <b>once per tick</b> and reuse
     * it across every object test, rather than recomputing the player's state inside each object's
     * overlap check.
     *
     * @return the player's object-collision box for the current tick
     */
    public AxisAlignedBoundingBox getObjectCollisionBounds() {
        final boolean largeStanding = isLarge() && !runtimeState.isDucking();
        final double x = position.getX();
        final double y = position.getY();
        // Horizontal edges match the solid-collision stop offsets (large 2..14, small/ducking 3..13)
        // rather than the sprite-flush 1..15. The old box overreached the wall stop by 1px on each
        // side, so a player resting flush against a block still overlapped an item sitting in the
        // adjacent column by ~1px — collecting it without moving. Aligning to where the body actually
        // stops removes that phantom pixel (matches the ROM's tight Player_BoundBox: it collides only
        // on real overlap, not when the edges merely touch).
        final double left = x + (isLarge() ? 2 : 3);
        final double right = x + (isLarge() ? 14 : 13);
        return new AxisAlignedBoundingBox(left, y + (largeStanding ? 6 : 16), right, y + 32);
    }

    /**
     * Whether the tail swing is on one of its two "kick" frames this tick — the only frames on which
     * it registers a hit (dasm prg000 {@code Object_RespondToTailAttack}: counter {@code == $0C} or
     * {@code == $09}). Guarded by {@link #hasTail()} so a countdown left over after losing the suit
     * cannot strike.
     *
     * @return {@code true} if the tail hitbox should be tested against objects this tick
     */
    public boolean isTailAttackStriking() {
        if (!hasTail()) {
            return false;
        }
        final int countdown = runtimeState.getPlayerTailAttackCountdown();
        return countdown == TAIL_ATTACK_STRIKE_FRAME_EARLY || countdown == TAIL_ATTACK_STRIKE_FRAME_LATE;
    }

    /**
     * The tail attack's hitbox for this tick, in sprite-pixel space (dasm prg000
     * {@code Object_RespondToTailAttack}, offsets from {@code Player_TailAttackXOff}). It sits on the
     * side the player faces — just past the right edge ({@code +17}) when facing right, or just left
     * ({@code −10}) when facing left — is 10 wide and 15 tall, and covers the lower body ({@code +16}
     * below the sprite top). Only meaningful while {@link #isTailAttackStriking()}.
     *
     * @return the tail hitbox for the current tick
     */
    public AxisAlignedBoundingBox getTailAttackBounds() {
        final double x = position.getX();
        final double y = position.getY();
        final double left = x + (orientation.getHorizontal() == RIGHT
            ? TAIL_ATTACK_X_OFFSET_RIGHT
            : TAIL_ATTACK_X_OFFSET_LEFT);
        final double top = y + TAIL_ATTACK_Y_OFFSET;
        return new AxisAlignedBoundingBox(left, top, left + TAIL_ATTACK_WIDTH, top + TAIL_ATTACK_HEIGHT);
    }

    @Override
    public void setMode(final PlayerMode playerMode) {
        this.mode = playerMode;
        if (node != null) {
            rebuildGeometry(node);
            updateVisualPosition();
        }
    }

    @Override
    public void updateInCameraState(final CameraState cameraState) {
        // X follows the player node. Y is driven by the SMB3 vertical-scroll
        // model instead of the node so the camera stays locked at the bottom of
        // a horizontal level (World 1-1 = Level_FreeVertScroll mode 0) rather
        // than scrolling on every jump. Called once, at spawn.
        cameraState.setTarget(node);
        cameraState.setVerticalScrollProvider(verticalScroll::getInterpolatedCameraY);
    }

    @Override
    public void advanceAnimation() {
        animationContext.update(this);
    }

    @Override
    public void updateFrame() {
        // The small→Super grow transition owns the tick: advance only the grow
        // flicker (dasm prg029 PRG029_D224) and skip all physics/input.
        if (runtimeState.isTransitioning()) {
            tickModeTransition();
            return;
        }

        // Another player is mid-transition and has halted gameplay (dasm
        // Player_HaltGame): freeze this player in place. Snapshot so render
        // interpolation holds steady rather than easing toward a stale target.
        if (gameEngine.isGameplayHalted()) {
            position.snapshotPrevious();
            return;
        }

        final StaticEnvironmentCollisionGrid collisionGrid = gameEngine.getCollisionGrid();

        // Capture the pre-tick position for render interpolation. Done here
        // (rather than in the engine loop) so the engine never dereferences a
        // player's position — MapPlayer, which has none, simply no-ops.
        position.snapshotPrevious();

        handleSizeToggle();

        // Ducking is handled early (matching dasm/JS reference order) so
        // the flag is current for the height offset and collision probes.
        handleDucking(inputHandler);

        // Determine collision height offset from the ducking flag (dasm
        // prg008 PRG008_A736: Y=20 when small or ducking, Y=10 otherwise).
        final int heightOffset = determineHeightOffset();

        // Emergency exit (emexit) detection — checks for a solid non-one-way tile
        // above the player's head at horizontal center (X+8). Matches dasm PRG008_A77E.
        final boolean lowClearance = isLowClearance(collisionGrid, heightOffset);
        if (lowClearance) {
            position.setDX(0);
            position.incrementX();
            runtimeState.setLowClearanceGrace(4);
        } else if (runtimeState.getLowClearanceGrace() > 0) {
            runtimeState.setLowClearanceGrace(runtimeState.getLowClearanceGrace() - 1);
        }

        // Advance position
        position.addToX(clamp(position.getDX(), -4, 4));
        if (runtimeState.isInAir()) {
            position.addToY(min(4, position.getDY()));
        }

        // In low clearance, the NES zeroes Pad_Input (prg008 PRG008_A795)
        // which prevents new button presses (jump) but NOT held-direction
        // input (Pad_Holding). Horizontal movement (acceleration, facing)
        // still runs normally — the velocity just gets zeroed at the start
        // of each low-clearance frame so only the +1 slide moves position.
        // We consume the jump press to discard it without disabling the
        // directional input that handleHorizontalMovement reads.
        if (lowClearance) {
            inputHandler.consumePress(HANDLER_JUMP);
            inputHandler.consumePress(HANDLER_RUN);
        }
        handleHorizontalMovement(inputHandler);
        handlePowerMeterAndRunFlag(inputHandler);
        handleVerticalMovement(inputHandler);
        handleFlyTimeCountdown();

        // During low clearance, zero ALL velocity after movement runs.
        // handleHorizontalMovement updates facing and animation state
        // (reading held directions), but no velocity must carry over —
        // only the +1 per-frame slide moves position. Preserving any DX
        // causes jerky movement when the lowClearance check flickers at
        // tile boundaries (the preserved DX gets applied on frames where
        // lowClearance is momentarily false).
        if (lowClearance) {
            position.setDX(0);
        }

        // Collision detection — suppress horizontal wall correction during
        // low clearance and for a few frames after exiting. On exit, the
        // horizontal probes may detect the ceiling block edge as a wall
        // and snap the player backward, causing a visible camera jolt.
        final boolean suppressWalls = lowClearance || runtimeState.getLowClearanceGrace() > 0;
        final boolean hitSomething = collisionGrid.handleCollision(this, suppressWalls);

        // Refine the logical state after physics + collision
        refinePlayerState(inputHandler, hitSomething, lowClearance);

        if (hasTail()) {
            // Tail attack (raccoon/tanooki B press on ground). Big and small
            // Mario are tailless, so they never trigger it.
            handleTailAttack(inputHandler);
        }

        // Advance raccoon sprite animation (walk cycle, still/moving transitions)
        advanceAnimation();

        // Advance the vertical camera scroll (locked at the level bottom unless
        // flying/climbing — SMB3 Level_FreeVertScroll mode 0).
        updateVerticalScroll();

        // Sync visual
        updateVisualPosition();
    }

    private void handleSizeToggle() {
        if (inputHandler.consumePress(HANDLER_SIZE_TOGGLE)) {
            if (getMode() == SHRUNK) {
                setMode(NORMAL);
            } else if (getMode() == NORMAL) {
                setMode(RACCOON);
            } else {
                setMode(SHRUNK);
            }
        }
    }

    @Override
    public boolean isHaltingGameplay() {
        return runtimeState.isTransitioning();
    }

    @Override
    public void onRewardConsumption(final RewardLevelObject reward) {
        final PlayerMode mode = getMode();
        if (runtimeState.isTransitioning()) {
            return;
        }
        if (mode == SHRUNK) {
            turnToNormal();
        } else if (reward instanceof SuperLeaf && mode != RACCOON) {
            turnToRaccoon();
        }
    }

    @Override
    public void onTransitionComplete(final PlayerMode newPlayerMode) {
        setMode(newPlayerMode);
    }

    @Override
    public void interpolateVisualPosition(final double alpha) {
        if (getNode() == null || getLevelScene() == null) {
            return;
        }
        // Linearly interpolate between previous and current simulation positions
        final PlayerPosition interpolatedPosition = position
            .interpolateBetweenPreviousAndCurrent(alpha)
            .toTileUnitBased(getLevelScene().getDimensions());

        getNode()
            .setLocalTranslation(
                (float) interpolatedPosition.getX(),
                (float) interpolatedPosition.getY() - 2,
                getVisibility().getPlayerZ()
            );

        // Advance the camera's vertical scroll with the same alpha so the Y axis
        // interpolates in lockstep with the node (X follows the node directly).
        verticalScroll.interpolate(alpha);
    }

    @Override
    public void onLayerSwitch() {
        setVisibility(getVisibility().opposite());
        updateForegroundLayerBuckets();
    }
}
