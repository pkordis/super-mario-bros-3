package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
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
    private final LevelScenePlayerAnimationContext animationContext;
    private final PlayerInputHandler inputHandler;
    private final StaticEnvironmentCollisionGrid collisionGrid;
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
        this.collisionGrid = getLevelScene().toCollisionGrid(this);
        this.verticalScroll = getBean(LevelSceneVerticalScroll.class, getLevelScene());
        this.animationContext = contextForLevel(this);
        this.orientation = new PlayerOrientation(RIGHT, SUSTAINED);
    }

    /**
     * The player's hitbox for collision against dynamic {@code ActiveLevelObject}s, in sprite-pixel
     * space. Derived from the collision-probe extents in {@code CollisionOffsets}: X spans +1..+15;
     * the bottom sits at +32; the top at +6 when large and standing, or +16 when small or ducking.
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
        return new AxisAlignedBoundingBox(x + 1, y + (largeStanding ? 6 : 16), x + 15, y + 32);
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
        cameraState.setVerticalScrollProvider(verticalScroll::getCameraY);
    }

    @Override
    public void advanceAnimation() {
        animationContext.update(this);
    }

    @Override
    public void updateFrame() {
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
        final boolean hitSomething = collisionGrid.handleCollision(suppressWalls);

        // Refine the logical state after physics + collision
        refinePlayerState(inputHandler, hitSomething, lowClearance);

        if (getMode() == RACCOON) {
            // Tail attack (raccoon B press on ground)
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
            setMode(getMode() == RACCOON ? PlayerMode.SHRUNK : RACCOON);
        }
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
    }

    @Override
    public void onLayerSwitch() {
        setVisibility(getVisibility().opposite());
        updateForegroundLayerBuckets();
    }
}
