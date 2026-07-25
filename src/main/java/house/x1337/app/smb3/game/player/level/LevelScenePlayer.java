package house.x1337.app.smb3.game.player.level;

import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.PlayerData;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.FLY_TIME;
import static house.x1337.app.smb3.GameConstants.GRAVITY_FAST;
import static house.x1337.app.smb3.GameConstants.GRAVITY_SLOW;
import static house.x1337.app.smb3.GameConstants.JUMP_FORCE;
import static house.x1337.app.smb3.GameConstants.PLAYER_FLY_APEX_YVEL;
import static house.x1337.app.smb3.GameConstants.PLAYER_FLY_YVEL;
import static house.x1337.app.smb3.GameConstants.PLAYER_SKID_VEL_THRESHOLD;
import static house.x1337.app.smb3.GameConstants.PLAYER_SPREAD_EAGLE_THRESHOLD;
import static house.x1337.app.smb3.GameConstants.PLAYER_TAILWAG_YVEL;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOPPOWERSPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOPRUNSPEED;
import static house.x1337.app.smb3.GameConstants.PLAYER_TOPWALKSPEED;
import static house.x1337.app.smb3.GameConstants.PMETER_CHARGE_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_DRAIN_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_FULL_HOLD_FRAMES;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.GameConstants.WAG_COUNT;
import static house.x1337.app.smb3.enumeration.PlayerOrientation.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientation.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerState.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerState.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerState.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerState.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerState.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerState.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerState.STILL;
import static house.x1337.app.smb3.enumeration.PlayerState.WALKING;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_SIZE_TOGGLE;
import static java.lang.Math.abs;
import static java.lang.Math.clamp;
import static java.lang.Math.floor;
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
@Slf4j
@Getter
@Prototype
public final class LevelScenePlayer extends LevelScenePlayerCapabilities {
    // P-meter / flight state
    private boolean isRunning;
    private int playerFlyTime;
    private int playerWagCount;
    private int flyTimeToggle;

    // Tail attack state (dasm: Player_TailAttack, set to $12 on B press,
    // auto-decrements to 0)
    private int playerTailAttack;

    // Frames remaining to suppress wall correction after exiting low
    // clearance. The horizontal probes can detect the ceiling block's
    // edge as a wall on the first frames after the player clears it,
    // causing a snap that jerks the camera.
    private int lowClearanceGrace;

    public LevelScenePlayer(
        final GameEngine gameEngine,
        final PlayerData playerData
    ) {
        super(gameEngine, playerData);
        setPlayerOrientation(RIGHT);
    }

    // -------------------------------------------------------------------------
    // Per-frame tick — direct port of the JS tick() function
    // -------------------------------------------------------------------------

    /**
     * Advances the player physics by one frame. Must be called once per
     * fixed-rate tick from the game engine's update loop.
     */
    @Override
    public void updateFrame() {
        handleSizeToggle();

        // Ducking is handled early (matching dasm/JS reference order) so
        // the flag is current for the height offset and collision probes.
        handleDucking();

        // Determine collision height offset from the ducking flag (dasm
        // prg008 PRG008_A736: Y=20 when small or ducking, Y=10 otherwise).
        final int heightOffset = (isSmall() || state.isDucking()) ? 20 : 10;

        // Emergency exit (emexit) detection — checks for a solid non-one-way tile
        // above the player's head at horizontal center (X+8). Matches dasm PRG008_A77E.
        final boolean lowClearance = isLowClearance(heightOffset);
        if (lowClearance) {
            position.setDX(0);
            position.incrementX();
            lowClearanceGrace = 4;
        } else if (lowClearanceGrace > 0) {
            lowClearanceGrace--;
        }

        // Advance position
        position.addToX(clamp(position.getDX(), -4, 4));
        if (state.isInAir()) {
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
        handleHorizontalMovement();
        handlePowerMeterAndRunFlag();
        handleVerticalMovement();
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
        final boolean suppressWalls = lowClearance || lowClearanceGrace > 0;
        final boolean hitWall = collisionGrid.handleCollision(heightOffset, suppressWalls);

        // Refine the logical state after physics + collision
        refinePlayerState(hitWall, lowClearance);

        // Tail attack (raccoon B press on ground)
        handleTailAttack();

        // Advance raccoon sprite animation (walk cycle, still/moving transitions)
        advanceAnimation();

        // Sync visual
        updateVisualPosition();
    }

    private boolean isLowClearance(final int heightOffset) {
        // Probe at horizontal center (X+8) only — matching dasm PRG008_A77E which
        // checks a single fixed point above the player's head. Adding a right-edge
        // probe (X+14) at the same heightOffset falsely detects a normal rightward
        // wall collision as low-clearance, causing the player to slide through walls.
        final boolean tileAbove = collisionGrid.collidesAtOffset(8, heightOffset) &&
            !collisionGrid.isOneWayTileFromPlayer(8, heightOffset);
        return tileAbove && !state.isInAir();
    }

    // -------------------------------------------------------------------------
    // Horizontal movement — 3 speed tiers (ASM: prg008 Player_GroundHControl)
    // -------------------------------------------------------------------------

    private void handleHorizontalMovement() {
        final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
        final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);

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
        if (state.isDucking() && !state.isInAir()) {
            inputLeft = false;
            inputRight = false;
        }

        // Update facing orientation from pad input — in the original game the
        // sprite always faces the pressed direction, including during a skid
        // (dasm prg008 PRG008_AE11–AE24: Player_FlipBits is set directly from
        // Pad_Holding regardless of velocity direction).
        if (inputLeft) {
            setPlayerOrientation(LEFT);
        } else if (inputRight) {
            setPlayerOrientation(RIGHT);
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
            if (!state.isInAir()) {
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
                if (!state.isInAir()) {
                    position.subtractFromDX(hitDir * accelerationFriction / 256.0);
                    if (abs(position.getDX()) < topSpeed) {
                        position.setDX(hitDir * topSpeed);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // P-meter and run flag
    // -------------------------------------------------------------------------

    private void handlePowerMeterAndRunFlag() {
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);
        final PlayerData pd = getPlayerData();

        // Player_RunFlag: set when on ground, holding B, speed >= TOPRUNSPEED
        isRunning = inputRun && !state.isInAir()
                && abs(position.getDX()) >= PLAYER_TOPRUNSPEED - 0.001;

        // P-meter update
        if (playerFlyTime <= 0) {
            final int throttle = pd.getPlayerPowerThrottle();
            if (throttle > 0) {
                pd.setPlayerPowerThrottle(throttle - 1);
            } else {
                final int power = pd.getPlayerPower();
                if (isRunning) {
                    if (power < PMETER_LEVELS) {
                        pd.setPlayerPower(power + 1);
                        pd.setPlayerPowerThrottle(PMETER_CHARGE_FRAMES);
                    } else {
                        pd.setPlayerPowerThrottle(PMETER_FULL_HOLD_FRAMES);
                    }
                } else {
                    if (power > 0) {
                        pd.setPlayerPower(power - 1);
                        pd.setPlayerPowerThrottle(PMETER_DRAIN_FRAMES);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Vertical movement — jump, gravity, raccoon flight/tail wag
    // -------------------------------------------------------------------------

    private void handleVerticalMovement() {
        final boolean jumpHit = inputHandler.consumePress(HANDLER_JUMP);
        final boolean jumpHeld = inputHandler.isActive(HANDLER_JUMP);

        if (jumpHit) {
            if (!state.isInAir()) {
                final int dx = min(3, (int) floor(abs(position.getDX())));
                position.setDY(JUMP_FORCE[dx]);
                state.setTo(JUMPING);
                playerWagCount = 0;
                // Full P-meter launch grants flight (raccoon mode)
                if (isLarge() && getPlayerData().getPlayerPower() >= PMETER_LEVELS
                        && playerFlyTime <= 0) {
                    playerFlyTime = FLY_TIME;
                }
            } else if (isLarge()) {
                // Mid-air A press: tail wag
                playerWagCount = WAG_COUNT;
            }
        }

        if (state.isInAir()) {
            if (position.getDY() < -2 && jumpHeld) {
                position.addToDY(GRAVITY_SLOW / 16.0);
            } else {
                position.addToDY(GRAVITY_FAST / 16.0);
            }

            // Raccoon tail wag / flight effects on Y velocity
            if (playerWagCount > 0) {
                playerWagCount--;
                if (isLarge() && position.getDY() > PLAYER_FLY_YVEL) {
                    if (playerFlyTime > 0) {
                        if (playerFlyTime >= 0x0f) {
                            position.setDY(PLAYER_FLY_YVEL);
                        } else if ((playerFlyTime & 0x08) != 0) {
                            position.setDY(PLAYER_FLY_APEX_YVEL);
                        } else {
                            position.setDY(0);
                        }
                    } else if (position.getDY() >= PLAYER_TAILWAG_YVEL) {
                        position.setDY(PLAYER_TAILWAG_YVEL);
                    }
                }
            }

            // Raccoon air drag (dasm prg008 PRG008_B082): when flying or
            // wagging, apply 1 raw unit/frame deceleration toward walk speed.
            // This prevents maintaining launch speed indefinitely during flight.
            if (isLarge() && (playerFlyTime > 0 || playerWagCount > 0)) {
                final double dx = position.getDX();
                final double absDx = abs(dx);
                if (absDx > PLAYER_TOPWALKSPEED) {
                    if (dx > 0) {
                        position.addToDX(-1.0 / 16.0);
                    } else {
                        position.addToDX(1.0 / 16.0);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // FlyTime countdown
    // -------------------------------------------------------------------------

    private void handleFlyTimeCountdown() {
        if (playerFlyTime > 0) {
            flyTimeToggle ^= 1;
            if (flyTimeToggle != 0) {
                playerFlyTime--;
                if (playerFlyTime <= 0) {
                    getPlayerData().setPlayerPower(0);
                    getPlayerData().setPlayerPowerThrottle(0);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Size toggle, layer toggle & ducking
    // -------------------------------------------------------------------------

    private void handleSizeToggle() {
        if (inputHandler.consumePress(HANDLER_SIZE_TOGGLE)) {
            setMode(this.getMode() == PlayerMode.RACCOON ? PlayerMode.SHRUNK : PlayerMode.RACCOON);
        }
    }

    private void handleDucking() {
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);

        if (isSmall()) {
            // Small Mario cannot duck (dasm prg008 PRG008_A70E: forcefully
            // disable ducking when small/holding/sliding).
            state.standUp();
        } else if (!state.isInAir()) {
            // Grounded: re-evaluate ducking from pad input each frame.
            // dasm prg008 PRG008_A72B: duck when ONLY down is held.
            if (inputDown) {
                state.duck();
            } else {
                state.standUp();
            }
        }
        // In air: ducking flag is frozen (dasm prg008 PRG008_A715: if already
        // ducking when airborne, keep it; if not, keep it unset). No action
        // needed — the flag persists from whatever it was before takeoff.
    }

    /**
     * Handles the raccoon tail attack (dasm prg008: Player_TailAttackAnim).
     *
     * <p>Triggered by a new B press while grounded, not ducking, and not
     * already attacking. Sets {@code playerTailAttack} to 18 ($12) which
     * auto-decrements each frame. The animation plays over 18 frames with
     * the player sprite flipping at frames 11 and 3.
     */
    private void handleTailAttack() {
        if (!isLarge()) {
            return;
        }

        // Decrement counter each frame (at start, so the trigger frame
        // displays at the full $12 value matching the dasm)
        if (playerTailAttack > 0) {
            playerTailAttack--;
        }

        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);

        // Cannot initiate while ducking (dasm: if PAD_DOWN held, skip)
        if (!inputDown && playerTailAttack == 0) {
            if (inputHandler.consumePress(HANDLER_RUN)) {
                playerTailAttack = 0x12;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Syncs the jme3 node position from the internal sprite-pixel coordinates.
     * Converts sprite-pixels to game-units (tile-fractions): divide by 16.
     * Y-axis is flipped: jme3 Y increases upward, tile rows increase downward.
     */
    @Override
    public void updateVisualPosition() {
        if (getNode() == null || getLevelScene() == null) {
            return;
        }
        // Convert sprite-pixel position to tile-unit position
        final float gameX = (float) (position.getX() / 16.0);
        // Invert Y: row 0 is at the top of the scene in tile space, but at
        // (rows - 1) in jme3 world space (where Y=0 is the bottom).
        final float gameY = (float) (collisionGrid.gridRows() - (position.getY() / 16.0));

        // The player's feet are always 32 sprite-pixels (2.0 game-units) below
        // Player_Y regardless of size (collision probes use 0x20 for both).
        // The quad is drawn upward from its origin, so place the origin at foot
        // level and let the quad's height determine how far up it extends.
        final float feetOffsetFromY = 32.0f / 16.0f;
        final float playerZ = getVisibility().getPlayerZ();
        getNode().setLocalTranslation(gameX, gameY - feetOffsetFromY, playerZ);
    }

    /**
     * Refines the player state after collision resolution. Ground states are
     * determined by velocity; air states are set during jump initiation and
     * collision (landing / walking off ledge).
     *
     * <p>Ducking is now a separate flag on {@code ActivePlayerState} and does
     * not participate in the movement state machine. The animator is
     * responsible for rendering the duck frame when the flag is set.
     *
     * @param hitWall true if the player collided with a horizontal wall this
     *               frame (dasm prg008 PRG008_B4F3: INC Player_WalkAnimTicks
     *               on wall hit keeps the walk animation running)
     * @param lowClearance true if the player is in low-clearance slide mode
     */
    private void refinePlayerState(final boolean hitWall, final boolean lowClearance) {
        if (state.isInAir()) {
            if (playerFlyTime > 0) {
                // dasm prg008: Player_FlyTime > 0 means the player is in
                // powered flight mode (raccoon/tanooki). The animation system
                // (Player_AnimTailWag) selects flying frames whenever FlyTime
                // is nonzero, independent of WagCount. WagCount only controls
                // the velocity cap physics, not the logical flight state.
                state.setTo(FLYING);
            } else if (position.getDY() < 0) {
                state.setTo(JUMPING);
            } else {
                state.setTo(FALLING);
            }
        } else {
            final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
            final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
            final boolean inputLeft = rawLeft && !rawRight;
            final boolean inputRight = rawRight && !rawLeft;
            if (isCurrentlySkidding(inputLeft, inputRight)) {
                state.setTo(SKIDDING);
            } else if (lowClearance && (inputLeft || inputRight)) {
                // During low clearance slide, show walk animation only when
                // a direction is held (dasm: WalkAnimTicks advances via
                // Player_GroundHControl which reads Pad_Holding).
                state.setTo(WALKING);
            } else if (hitWall && (inputLeft || inputRight)) {
                // dasm prg008 PRG008_B4F3: when the player hits a wall while
                // pressing a direction, Player_WalkAnimTicks is incremented
                // which keeps the walk animation cycling. The player appears to
                // "walk in place" against the wall rather than going still.
                state.setTo(WALKING);
            } else if (abs(position.getDX()) < 0.01) {
                state.setTo(STILL);
            } else if (isRunning && abs(position.getDX()) >= PLAYER_SPREAD_EAGLE_THRESHOLD) {
                // Spread-eagle: abs(XVel) >= $37 in the original (prg008.asm
                // Player_SetSpecialFrames). Full P-meter speed reached.
                state.setTo(POWER_RUNNING);
            } else if (isRunning) {
                // B held, speed >= TOPRUNSPEED but below spread-eagle threshold.
                // Still uses walk animation frames (accelerating toward max).
                state.setTo(RUNNING);
            } else {
                state.setTo(WALKING);
            }
        }
    }

    /**
     * Determines whether the player is currently in a skid condition.
     *
     * <p>From prg008.asm: the skid triggers when the player is grounded, not in
     * water, has |XVel| ≥ $02 (raw fixed-point), and is pressing the direction
     * opposite to their current movement.
     *
     * @param inputLeft  true if the left input is active
     * @param inputRight true if the right input is active
     * @return true if the player meets all skid conditions
     */
    private boolean isCurrentlySkidding(final boolean inputLeft, final boolean inputRight) {
        if (state.isInAir()) {
            return false;
        }
        final double dx = position.getDX();
        if (abs(dx) < PLAYER_SKID_VEL_THRESHOLD) {
            return false;
        }
        // Pressing opposite direction from current movement
        return (dx > 0 && inputLeft) || (dx < 0 && inputRight);
    }

    @Override
    public void onLayerSwitch() {
        setVisibility(getVisibility().opposite());
        updateForegroundLayerBuckets();
    }

    /**
     * When in BACKGROUND mode, moves layers that should render in front of
     * the player (DECORATIONS_LAND and above) into the {@code Translucent}
     * bucket and re-attaches them after the player node so they draw on top.
     * When back in FOREGROUND mode, restores them to {@code Transparent}.
     *
     * <p>This solves the ordering problem: the player must be in
     * {@code Translucent} to avoid frustum-related visibility issues
     * (see commit "Camera bounds aligned"), but when behind scenery,
     * those layers need to render after the player.
     */
    private void updateForegroundLayerBuckets() {
        final Node rootNode = gameEngine.getRootNode();
        final boolean background = (getVisibility() == PlayerVisibility.BACKGROUND);

        // Layers that should appear IN FRONT of the player when in BACKGROUND
        final String[] foregroundLayers = {
            "Layer-DECORATIONS_LAND",
            "Layer-STATIC_ENVIRONMENT",
            "Layer-INTERACTIVE_OBJECTS",
            "Layer-NON_PLAYABLE_CHARACTERS",
        };

        for (final String layerName : foregroundLayers) {
            final Spatial layerSpatial = rootNode.getChild(layerName);
            if (layerSpatial instanceof Geometry layerGeometry) {
                if (background) {
                    // Move to Translucent and re-attach after player so it renders on top
                    layerGeometry.setQueueBucket(RenderQueue.Bucket.Translucent);
                    rootNode.detachChild(layerGeometry);
                    rootNode.attachChild(layerGeometry);
                } else {
                    // Restore to Transparent (renders before player's Translucent)
                    layerGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
                }
            }
        }
    }
}
