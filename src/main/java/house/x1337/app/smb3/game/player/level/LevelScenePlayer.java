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
import house.x1337.app.smb3.model.event.PlayerDied;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_FALL_MAX;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_GRAVITY;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.Reward.ONE_UP;
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
    // Raccoon tail-attack hitbox for dynamic objects, in sprite-pixel space (dasm prg000
    // Object_RespondToTailAttack / Player_TailAttackXOff): offset to the side the player faces, 10 wide,
    // 15 tall, covering the lower body. The swing "kicks" (registers a hit) only on the two countdown
    // frames $0C and $09.
    private static final double TAIL_ATTACK_X_OFFSET_RIGHT = 17;
    private static final double TAIL_ATTACK_X_OFFSET_LEFT = -10;
    private static final double TAIL_ATTACK_WIDTH = 10;
    private static final double TAIL_ATTACK_Y_OFFSET = 16;
    private static final double TAIL_ATTACK_HEIGHT = 15;
    private static final int TAIL_ATTACK_STRIKE_FRAME_EARLY = 12;
    private static final int TAIL_ATTACK_STRIKE_FRAME_LATE = 9;

    // Tail attack against static blocks is NOT the box above: the ROM probes a single point and looks up
    // the one tile containing it (dasm prg008 Player_TailAttack_HitBlocks -> Player_GetTileAndSlope, with
    // the offsets from Player_TailAttack_Offsets: Y = 28, X = -6 facing left / +21 facing right). It also
    // fires on one frame only, $09 -- not on $0C like the object test.
    private static final double TAIL_ATTACK_BLOCK_PROBE_X_RIGHT = 21;
    private static final double TAIL_ATTACK_BLOCK_PROBE_X_LEFT = -6;
    private static final double TAIL_ATTACK_BLOCK_PROBE_Y = 28;

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

    /**
     * Guards the one-shot {@link PlayerDied} publish at the end of the death throe, so the engine is
     * asked to restart the level exactly once even though {@link #tickDeath()} keeps running until the
     * engine swaps the scene out.
     */
    private boolean deathEventPublished;

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
     * space — the ROM's {@code Player_BoundBox} (dasm prg000 @ line 5190), selected by size exactly as
     * {@code Object_HitTest} does: entry 0 for small or ducking, entry 1 otherwise. Each ROM entry is
     * {@code (left, width, top, height)} applied to the sprite's top-left, so:
     *
     * <ul>
     *   <li><b>Large standing</b> {@code (3, 10, 5, 25)} → X {@code [+3, +13]}, Y {@code [+5, +30]}.</li>
     *   <li><b>Small / ducking</b> {@code (4, 8, 17, 13)} → X {@code [+4, +12]}, Y {@code [+17, +30]}.</li>
     * </ul>
     *
     * <p>Note the bottom is {@code +30} for both, not the sprite-flush {@code +32}: the ROM's box stops
     * two pixels above the feet. The size switch keys off {@link #isLarge()} AND
     * {@code !isDucking()} together — a ducking large player uses the small box, which is what the ROM's
     * {@code Player_IsDucking} test does.
     *
     * <p>This is the "hoist" point for object collision — compute it <b>once per tick</b> and reuse
     * it across every object test, rather than recomputing the player's state inside each object's
     * overlap check.
     *
     * @return the player's object-collision box for the current tick
     */
    public AxisAlignedBoundingBox getObjectCollisionBounds() {
        final boolean largeStanding = isLarge() && !runtimeState.isDucking();
        // Edges straight from the ROM's Player_BoundBox table (left/width/top/height offsets): large
        // standing spans X [+3, +13] / Y [+5, +30], small or ducking spans X [+4, +12] / Y [+17, +30].
        final double left = position.getX() + (largeStanding ? 3 : 4);
        final double right = position.getX() + (largeStanding ? 13 : 12);
        final double top = position.getY() + (largeStanding ? 5 : 17);
        final double bottom = position.getY() + 30;
        return new AxisAlignedBoundingBox(left, top, right, bottom);
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
        final double left = position.getX() + (orientation.getHorizontal() == RIGHT
            ? TAIL_ATTACK_X_OFFSET_RIGHT
            : TAIL_ATTACK_X_OFFSET_LEFT);
        final double top = position.getY() + TAIL_ATTACK_Y_OFFSET;
        return new AxisAlignedBoundingBox(left, top, left + TAIL_ATTACK_WIDTH, top + TAIL_ATTACK_HEIGHT);
    }

    /**
     * Whether the tail swing is on the single frame that strikes <em>blocks</em> (dasm prg008
     * {@code Player_TailAttack_HitBlocks}: {@code CMP #$09 / BNE}). Deliberately narrower than
     * {@link #isTailAttackStriking()}, which also fires on {@code $0C} for objects — the ROM's block
     * routine never runs on that frame.
     *
     * @return {@code true} if the tail should be tested against static blocks this tick
     */
    public boolean isTailAttackStrikingBlocks() {
        return hasTail() && runtimeState.getPlayerTailAttackCountdown() == TAIL_ATTACK_STRIKE_FRAME_LATE;
    }

    /**
     * X of the tail's block-probe point, in sprite-pixel space. See
     * {@link #getTailAttackBlockProbeY()} for why this is a point rather than a box.
     *
     * @return the probe X, {@code +21} of the sprite left edge facing right, {@code -6} facing left
     */
    public double getTailAttackBlockProbeX() {
        return position.getX() + (orientation.getHorizontal() == RIGHT
            ? TAIL_ATTACK_BLOCK_PROBE_X_RIGHT
            : TAIL_ATTACK_BLOCK_PROBE_X_LEFT);
    }

    /**
     * Y of the tail's block-probe point, in sprite-pixel space: {@code +28} below the sprite top, i.e.
     * down at the feet.
     *
     * <p>The ROM tests blocks with a <b>single point</b>, not with {@link #getTailAttackBounds()}: it
     * loads the {@code Player_TailAttack_Offsets} pair into the tile-probe inputs and calls
     * {@code Player_GetTileAndSlope}, which resolves exactly one tile (dasm prg008
     * {@code Player_TailAttack_HitBlocks}). Reusing the 15-tall object box here would span two tile rows
     * whenever the player is not tile-aligned — notably while their head is pressed against a ceiling,
     * where the 6px head padding shifts the sprite top up and drags the box's top edge into the row
     * above — and would break bricks level with the player's chest, which the original never does.
     *
     * @return the probe Y
     */
    public double getTailAttackBlockProbeY() {
        return position.getY() + TAIL_ATTACK_BLOCK_PROBE_Y;
    }

    /**
     * Resolves this tick's tail strike against <b>static blocks</b> (dasm prg008
     * {@code Player_TailAttack_HitBlocks}): a single probe point resolving to one tile, tested on
     * countdown frame {@code $09} only.
     *
     * <p>Called from {@link #updateFrame()} once the swing counter has advanced and collision has
     * settled this tick's position, which the probe is derived from — the ROM likewise resolves blocks
     * inside the player routine, and the terrain grid is tile-aligned and rebuilt only when the level
     * mutates, so it is valid at any point in the tick.
     *
     * <p>The object half is deliberately not resolved here: the ROM runs it in the object loop, so it
     * is driven from {@code ActiveObjectGrid.resolveActiveObjectCollisions} once every object has moved
     * this tick, consulting {@link #isTailAttackStriking()} and {@link #getTailAttackBounds()}.
     */
    private void resolveTailAttackAgainstBlocks() {
        if (isTailAttackStrikingBlocks()) {
            getCollisionGrid().resolveTailAttack(this, getTailAttackBlockProbeX(), getTailAttackBlockProbeY());
        }
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
        // Paired with the frame the animator just chose, because the ROM's blanking is part of drawing
        // that frame rather than a separate effect: Player_Draw decides it before writing any sprite.
        applyHurtFlashVisibility();
    }

    @Override
    public void updateFrame() {
        // The death throe owns the tick: it freezes gameplay and plays out its own rise-and-fall off
        // the bottom of the screen (dasm Player_IsDying — the dying routine in prg029 PRG029_D768 owns
        // the Y motion while the rest of the game is halted). Checked first: a dying player runs no
        // physics, input or transition.
        if (runtimeState.isDying()) {
            tickDeath();
            return;
        }

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

            // Resolve the swing against terrain in the same breath as advancing it, now that
            // collision has settled this tick's position. Objects are struck later, from the
            // broadphase pass, mirroring the ROM's split between the player and object routines.
            resolveTailAttackAgainstBlocks();
        }

        // Advance raccoon sprite animation (walk cycle, still/moving transitions)
        advanceAnimation();

        // Only now, on a frame the player was actually drawn as itself, does the hurt flash advance
        // (dasm prg029 Player_Draw: DEC Player_FlashInv). Both early returns above are frames the ROM
        // never reaches that code on — the poof owns the draw routine for its whole duration
        // (PRG029_D205 returns straight after it) — which is precisely what holds the invincibility at
        // full length until gameplay resumes. Decremented after advanceAnimation so this frame's
        // visibility came from the pre-decrement value, as the ROM's LDA/DEC order gives.
        runtimeState.decrementHurtInvincibility();

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

    /**
     * Advances the death throe by one tick (dasm prg029 PRG029_D768): apply gravity to the launch
     * velocity, move on the Y axis only, and draw the dead frame. The body pops up
     * ({@link GameConstants#PLAYER_DEATH_RISE_YVEL}), decelerates at {@link GameConstants#PLAYER_DEATH_GRAVITY}
     * to an apex, then falls — capped at {@link GameConstants#PLAYER_DEATH_FALL_MAX} — off the bottom of
     * the level. There is no horizontal motion and no input; the throe owns the player entirely while
     * {@link #isHaltingGameplay()} freezes everything else.
     *
     * <p>Once the body has dropped clear of the bottom edge, a single {@link PlayerDied} is published
     * (guarded by {@link #deathEventPublished}) and the engine restarts the level from scratch. The
     * throe keeps ticking harmlessly until that swap happens, so the one-shot guard matters.
     */
    private void tickDeath() {
        position.snapshotPrevious();

        // Pre-launch pause (dasm Event_Countdown seeded to $30): the dead sprite is drawn in place with
        // no motion while the countdown runs; the rise-and-fall begins only once it reaches zero. This
        // is the beat where the body hangs in contact with the enemy that killed it before popping up.
        if (runtimeState.isDeathPausing()) {
            runtimeState.decrementDeathPause();
            advanceAnimation();
            updateVisualPosition();
            return;
        }

        applyDeathGravityStep(position);
        advanceAnimation();
        updateVisualPosition();

        if (!deathEventPublished && hasFallenOffLevel(position, getLevelScene().getDimensions().rows())) {
            deathEventPublished = true;
            gameEngine.publish(new PlayerDied());
        }
    }

    /**
     * One tick of death-throe Y motion (dasm prg029 PRG029_D768): add gravity to the velocity, cap the
     * fall at the terminal rate, and move on Y only. Extracted as a pure step over the position so the
     * physics is unit-testable without a fully wired player.
     *
     * @param position the dying player's position, advanced in place
     */
    static void applyDeathGravityStep(final PlayerPosition position) {
        position.addToDY(PLAYER_DEATH_GRAVITY);
        if (position.getDY() > PLAYER_DEATH_FALL_MAX) {
            position.setDY(PLAYER_DEATH_FALL_MAX);
        }
        position.addToY(position.getDY());
    }

    /**
     * Whether the dying body has dropped a full tile below the level's bottom edge, i.e. fully
     * off-screen. Y is sprite-pixel space growing down, so the bottom edge sits at
     * {@code rows × TILE_SPRITE_SIZE}; one extra tile of margin ensures the sprite has entirely cleared
     * the view before the restart fires.
     *
     * @param position the dying player's position
     * @param rows     the level height in tiles
     * @return {@code true} once the body is clear of the bottom edge
     */
    static boolean hasFallenOffLevel(final PlayerPosition position, final int rows) {
        return position.getY() > (double) (rows + 1) * TILE_SPRITE_SIZE;
    }

    @Override
    public boolean isHaltingGameplay() {
        return runtimeState.isTransitioning() || runtimeState.isDying();
    }

    @Override
    public void onRewardConsumption(final RewardLevelObject reward) {
        final PlayerMode mode = getMode();
        if (runtimeState.isTransitioning()) {
            return;
        }
        if (reward.getRewardType() != ONE_UP) {
            if (mode == SHRUNK) {
                turnToNormal();
            } else if (reward instanceof SuperLeaf && mode != RACCOON) {
                turnToRaccoon();
            }
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
