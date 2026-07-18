package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientation;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
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
import static house.x1337.app.smb3.enumeration.PlayerState.DUCKING;
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
@Prototype
public final class LevelScenePlayer extends LevelScenePlayerCapabilities {
    // P-meter / flight state
    @Getter
    private int playerPower;
    private int playerPMeterCnt;
    private boolean playerRunFlag;
    @Getter
    private int playerFlyTime;
    @Getter
    private int playerWagCount;
    private int flyTimeToggle;

    private PlayerOrientation orientation = RIGHT;

    public LevelScenePlayer(
        final GameEngine gameEngine,
        final PlayerIdentity identity
    ) {
        super(gameEngine, identity);
        setPlayerOrientation(orientation);
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

        // Determine collision height offset — use direct input check since
        // the ducking state is applied at end-of-frame.
        final boolean duckingThisFrame = !isSmall() && !state.isInAir()
            && inputHandler.isActive(HANDLER_DOWN);
        final int heightOffset = (isSmall() || duckingThisFrame) ? 20 : 10;
        final boolean tileAbove = collisionGrid.collidesAtOffset(8, heightOffset) &&
            !collisionGrid.isOneWayTileFromPlayer(8, heightOffset);
        final boolean lowClearance = tileAbove && !state.isInAir();
        if (lowClearance) {
            position.setDX(0);
            position.incrementX();
        }

        // Advance position
        position.addToX(clamp(position.getDX(), -4, 4));
        if (state.isInAir()) {
            position.addToY(min(4, position.getDY()));
        }

        handleHorizontalMovement();
        handlePMeterAndRunFlag();
        handleVerticalMovement();
        handleFlyTimeCountdown();

        // Collision detection
        collisionGrid.handleCollision(heightOffset, lowClearance);

        // Refine the logical state after physics + collision
        refinePlayerState();

        // Ducking must be applied after state refinement since collision
        // resets grounded state to STILL every frame.
        handleDucking();

        // Advance raccoon sprite animation (walk cycle, still/moving transitions)
        tickRacoonAnimation();

        // Sync visual
        updateVisualPosition();
    }

    // -------------------------------------------------------------------------
    // Horizontal movement — 3 speed tiers (ASM: prg008 Player_GroundHControl)
    // -------------------------------------------------------------------------

    private void handleHorizontalMovement() {
        final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
        final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);

        // Cancel simultaneous L+R — impossible on a real NES D-pad; on
        // keyboard/gamepad we treat it as no directional input (dasm prg008:
        // original hardware cannot physically produce this combination).
        boolean inputLeft = rawLeft && !rawRight;
        boolean inputRight = rawRight && !rawLeft;

        // Suppress directional acceleration while ducking — the player should
        // decelerate to a stop via friction only. In the original game this is
        // implicit because ducking requires releasing L/R; here we allow
        // ducking while L/R is held, so we suppress them explicitly.
        if (inputDown && !state.isInAir() && isLarge()) {
            inputLeft = false;
            inputRight = false;
        }

        // Update facing orientation from pad input — in the original game the
        // sprite always faces the pressed direction, including during a skid
        // (dasm prg008 PRG008_AE11–AE24: Player_FlipBits is set directly from
        // Pad_Holding regardless of velocity direction).
        if (inputLeft) {
            orientation = LEFT;
            setPlayerOrientation(LEFT);
        } else if (inputRight) {
            orientation = RIGHT;
            setPlayerOrientation(RIGHT);
        }

        // Determine top speed
        double topSpeed = PLAYER_TOPWALKSPEED;
        if (inputRun) {
            topSpeed = PLAYER_TOPRUNSPEED;
            if (playerPower >= PMETER_LEVELS) {
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

    private void handlePMeterAndRunFlag() {
        final boolean inputRun = inputHandler.isActive(HANDLER_RUN);

        // Player_RunFlag: set when on ground, holding B, speed >= TOPRUNSPEED
        playerRunFlag = inputRun && !state.isInAir()
                && abs(position.getDX()) >= PLAYER_TOPRUNSPEED - 0.001;

        // P-meter update
        if (playerFlyTime <= 0) {
            if (playerPMeterCnt > 0) {
                playerPMeterCnt--;
            } else {
                if (playerRunFlag) {
                    if (playerPower < PMETER_LEVELS) {
                        playerPower++;
                        playerPMeterCnt = PMETER_CHARGE_FRAMES;
                    } else {
                        playerPMeterCnt = PMETER_FULL_HOLD_FRAMES;
                    }
                } else {
                    if (playerPower > 0) {
                        playerPower--;
                        playerPMeterCnt = PMETER_DRAIN_FRAMES;
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
                if (isLarge() && playerPower >= PMETER_LEVELS && playerFlyTime <= 0) {
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
                    playerPower = 0;
                    playerPMeterCnt = 0;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Size toggle, layer toggle & ducking
    // -------------------------------------------------------------------------

    private void handleSizeToggle() {
        if (inputHandler.consumePress(HANDLER_SIZE_TOGGLE)) {
            setMode(this.getMode() == PlayerMode.RACOON ? PlayerMode.SHRUNK : PlayerMode.RACOON);
        }
    }

    private void handleDucking() {
        final boolean inputDown = inputHandler.isActive(HANDLER_DOWN);

        if (isSmall()) {
            if (state.isDucking()) {
                state.setTo(STILL);
            }
        } else if (!state.isInAir()) {
            // Duck whenever down is held, regardless of left/right input.
            if (inputDown && !state.isDucking()) {
                state.setTo(DUCKING);
            } else if (!inputDown && state.isDucking()) {
                state.setTo(STILL);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Raccoon animation
    // -------------------------------------------------------------------------

    /** Tracks whether the last rendered frame was a sprite (true) or cyan box (false). */
    private boolean lastFrameWasSprite = true;

    /**
     * Drives the raccoon sprite animator each frame when in raccoon mode.
     * For states the animator does not handle (jumping, falling, flying, etc.),
     * the player reverts to the cyan colored box fallback.
     */
    private void tickRacoonAnimation() {
        final RacoonPlayerAnimator animator = getRacoonAnimator();
        if (animator == null || node == null || getMode() != PlayerMode.RACOON) {
            return;
        }
        final boolean handled = animator.tick(
            node, state.getCurrent(), orientation, abs(position.getDX())
        );
        if (handled) {
            lastFrameWasSprite = true;
        } else if (lastFrameWasSprite) {
            // Transition from sprite to unhandled state — show cyan box
            buildCyanBox(node);
            lastFrameWasSprite = false;
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
        if (node == null || getLevelScene() == null) {
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
        node.setLocalTranslation(gameX, gameY - feetOffsetFromY, playerZ);
    }

    /**
     * Refines the player state after collision resolution. Ground states are
     * determined by velocity; air states are set during jump initiation and
     * collision (landing / walking off ledge).
     */
    private void refinePlayerState() {
        if (state.isInAir()) {
            if (playerFlyTime > 0 && playerWagCount > 0) {
                state.setTo(FLYING);
            } else if (position.getDY() < 0) {
                state.setTo(JUMPING);
            } else {
                state.setTo(FALLING);
            }
        } else if (!state.isDucking()) {
            final boolean rawLeft = inputHandler.isActive(HANDLER_LEFT);
            final boolean rawRight = inputHandler.isActive(HANDLER_RIGHT);
            final boolean inputLeft = rawLeft && !rawRight;
            final boolean inputRight = rawRight && !rawLeft;
            if (isCurrentlySkidding(inputLeft, inputRight)) {
                state.setTo(SKIDDING);
            } else if (abs(position.getDX()) < 0.01) {
                state.setTo(STILL);
            } else if (playerRunFlag && abs(position.getDX()) >= PLAYER_SPREAD_EAGLE_THRESHOLD) {
                // Spread-eagle: abs(XVel) >= $37 in the original (prg008.asm
                // Player_SetSpecialFrames). Full P-meter speed reached.
                state.setTo(POWER_RUNNING);
            } else if (playerRunFlag) {
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
    }
}
