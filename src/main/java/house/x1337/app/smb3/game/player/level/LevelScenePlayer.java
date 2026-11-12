package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerDeathKind;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.object.level.reward.SuperLeaf;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.PlayerDeathFallEnded;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
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
import static house.x1337.app.smb3.enumeration.SoundEffect.BLOCK;
import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_UP;
import static house.x1337.app.smb3.enumeration.SoundEffect.TANOOKI;
import static house.x1337.app.smb3.game.player.factory.PlayerAnimatorFactory.contextForLevel;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_SIZE_TOGGLE;
import static java.lang.Math.clamp;
import static java.lang.Math.min;

/**
 * Player physics and rendering controller implementing SMB3 movement mechanics.
 *
 * <p>It runs the SMB3 player physics in a jMonkeyEngine + dyn4j environment, following the injection
 * pattern from PixelLinePlatformer's Player class.
 *
 * <p>The player's appearance is initially a colored box (cyan). Collision is resolved against tiles
 * whose {@link TileType} category is {@link TileType.Category#COLLIDING}.
 */
@Data
@Slf4j
@Prototype
public final class LevelScenePlayer implements LevelScenePlayerCapabilities {
    // Raccoon tail-attack hitbox for dynamic objects, in sprite-pixel space: 10 wide, 15 tall, covering
    // the lower body, and placed on the side OPPOSITE the facing the sprite is drawn with -- which is the
    // side the tail is drawn on. The offset follows the drawn facing, so the left-facing art takes +17 and
    // the right-facing art takes -10; see getTailAttackSwingFacing for why that puts the box behind the
    // player on the first strike frame and in front on the second.
    private static final double TAIL_ATTACK_X_OFFSET_FACING_LEFT = 17;
    private static final double TAIL_ATTACK_X_OFFSET_FACING_RIGHT = -10;
    private static final double TAIL_ATTACK_WIDTH = 10;
    private static final double TAIL_ATTACK_Y_OFFSET = 16;
    private static final double TAIL_ATTACK_HEIGHT = 15;
    private static final int TAIL_ATTACK_STRIKE_FRAME_EARLY = 12;
    private static final int TAIL_ATTACK_STRIKE_FRAME_LATE = 9;

    // The swing mirrors the sprite partway through: the tail animation flips it horizontally on the frame
    // the counter reads 11 and flips it back on 3, so the counter range (11, 3] is drawn -- and struck --
    // facing the other way.
    private static final int TAIL_ATTACK_FLIP_FROM_FRAME = 11;
    private static final int TAIL_ATTACK_FLIP_UNTIL_FRAME = 3;

    // Tail attack against static blocks is NOT the box above: it probes a single point and looks up the
    // one tile containing it (Y = 28, X = -6 or +21). Keyed off the drawn facing like the object box, so
    // it too sits behind the drawn facing. It also fires on one frame only, 9 -- not on 12 like the
    // object test.
    private static final double TAIL_ATTACK_BLOCK_PROBE_X_FACING_LEFT = 21;
    private static final double TAIL_ATTACK_BLOCK_PROBE_X_FACING_RIGHT = -6;
    private static final double TAIL_ATTACK_BLOCK_PROBE_Y = 28;

    private final PlayerRuntimeState runtimeState = getBean(PlayerRuntimeState.class);
    private final LevelScenePlayerAnimationContext animationContext;
    private final PlayerInputHandler inputHandler;
    private final PlayerPosition position;
    private final PlayerOrientation orientation;
    private final PlayerData playerData;
    private final GameEngine gameEngine;

    private PlayerVisibility visibility = FOREGROUND;
    private LevelSceneVerticalScroll verticalScroll;
    private PlayerMode mode;
    private Node node;

    /**
     * Guards the one-shot {@link PlayerDeathFallEnded} publish at the end of the death throe, so the body's fall is
     * reported exactly once even though {@link #tickDeath()} keeps running until the engine swaps the scene out.
     */
    private boolean deathEventPublished;

    /**
     * The state behind {@link LevelScenePlayerPipeUsingCapabilities#isPipeTransitPublished()}: guards the one-shot
     * transit request when the pipe countdown runs out, as {@link #deathEventPublished} guards the death's.
     */
    private boolean pipeTransitPublished;

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
        this.verticalScroll = getBean(LevelSceneVerticalScroll.class, getLevelScene());
        this.animationContext = contextForLevel(this);
        this.orientation = new PlayerOrientation(RIGHT, SUSTAINED);
    }

    /**
     * The player's hitbox for collision against dynamic {@code ActiveLevelObject}s, in sprite-pixel
     * space, selected by size: the small box for small or ducking, the large box otherwise. Each box is
     * {@code (left, width, top, height)} applied to the sprite's top-left, so:
     *
     * <ul>
     *   <li><b>Large standing</b> {@code (3, 10, 5, 25)} → X {@code [+3, +13]}, Y {@code [+5, +30]}.</li>
     *   <li><b>Small / ducking</b> {@code (4, 8, 17, 13)} → X {@code [+4, +12]}, Y {@code [+17, +30]}.</li>
     * </ul>
     *
     * <p>Note the bottom is {@code +30} for both, not the sprite-flush {@code +32}: the box stops two
     * pixels above the feet. The size switch keys off {@link #isLarge()} AND {@code !isDucking()}
     * together — a ducking large player uses the small box.
     *
     * <p>This is the "hoist" point for object collision — compute it <b>once per tick</b> and reuse
     * it across every object test, rather than recomputing the player's state inside each object's
     * overlap check.
     *
     * @return the player's object-collision box for the current tick
     */
    public AxisAlignedBoundingBox getObjectCollisionBounds() {
        final boolean largeStanding = isLarge() && !runtimeState.isDucking();
        // Edges from the (left, width, top, height) offsets: large standing spans X [+3, +13] / Y [+5, +30],
        // small or ducking spans X [+4, +12] / Y [+17, +30].
        final double left = position.getX() + (largeStanding ? 3 : 4);
        final double right = position.getX() + (largeStanding ? 13 : 12);
        final double top = position.getY() + (largeStanding ? 5 : 17);
        final double bottom = position.getY() + 30;
        return new AxisAlignedBoundingBox(left, top, right, bottom);
    }

    /**
     * Whether the carry button (B) is held down this tick, which both the pickup and the release read.
     *
     * <p>Deliberately the <em>held</em> state and not {@code consumePress}: the press edge belongs to
     * the tail swing, and a carry that consumed it would silently cancel the swing. It is also what
     * makes "B is no longer held" a usable release test, since the input handler exposes no release
     * edge of its own.
     *
     * @return {@code true} while B is down
     */
    public boolean isCarryButtonHeld() {
        return inputHandler.isActive(HANDLER_RUN);
    }

    @Override
    public LevelScenePlayer asLevelScenePlayer() {
        return this;
    }

    /**
     * Whether the tail swing is on one of its two "kick" frames this tick — the only frames on which
     * it registers a hit (countdown {@code == 12} or {@code == 9}). Guarded by {@link #hasTail()} so a
     * countdown left over after losing the suit cannot strike.
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
     * The facing the tail swing is drawn <em>and struck</em> with this tick — during a wag this is not the
     * facing the player set out with.
     *
     * <p>The tail animation mirrors the sprite mid-swing: it flips it horizontally on the frame the
     * countdown reads {@code 11} and flips it back on {@code 3}, so Mario spins through the wag. The facing
     * cannot be steered while it runs, either — the pad's facing update is skipped outright for the whole
     * countdown, which is what lets the flip survive a held direction.
     *
     * <p>It matters because every tail geometry is keyed off the drawn facing rather than off the
     * player's intent, and the two strike frames straddle the flip: {@code 12} lands before it and
     * {@code 9} after. With the hitbox always sitting <b>behind</b> the drawn facing (see
     * {@link #getTailAttackBounds()}), the swing therefore covers <b>both sides</b> of the player — the
     * early frame strikes behind them, the late frame in front — which is the tail arcing around.
     *
     * @return the drawn facing: the player's own outside the flip window, mirrored inside it
     */
    public PlayerOrientationHorizontal getTailAttackSwingFacing() {
        final int countdown = runtimeState.getPlayerTailAttackCountdown();
        final boolean flipped = countdown <= TAIL_ATTACK_FLIP_FROM_FRAME
            && countdown > TAIL_ATTACK_FLIP_UNTIL_FRAME;
        return orientation.getHorizontal().oppositeIf(flipped);
    }

    /**
     * The tail attack's hitbox for this tick, in sprite-pixel space. It is 10 wide and 15 tall, covers
     * the lower body ({@code +16} below the sprite top), and sits on the side
     * <b>opposite</b> {@link #getTailAttackSwingFacing()} — just past the right edge ({@code +17}) while
     * the sprite is drawn facing left, or just left of it ({@code -10}) while drawn facing right.
     *
     * <p>Behind the drawn facing is where the tail <em>is</em>: the offset follows the drawn facing, and
     * for left-facing art it is {@code +17}. Taking the player's facing instead would put the box where the
     * tail is not drawn, and would miss the swing's back half entirely — an enemy walking into the player
     * from behind is legitimately tail-struck.
     *
     * <p>Only meaningful while {@link #isTailAttackStriking()}.
     *
     * @return the tail hitbox for the current tick
     */
    public AxisAlignedBoundingBox getTailAttackBounds() {
        final double left = position.getX() + (getTailAttackSwingFacing() == RIGHT
            ? TAIL_ATTACK_X_OFFSET_FACING_RIGHT
            : TAIL_ATTACK_X_OFFSET_FACING_LEFT);
        final double top = position.getY() + TAIL_ATTACK_Y_OFFSET;
        return new AxisAlignedBoundingBox(left, top, left + TAIL_ATTACK_WIDTH, top + TAIL_ATTACK_HEIGHT);
    }

    /**
     * Whether the tail swing is on the single frame that strikes <em>blocks</em> (countdown {@code == 9}).
     * Deliberately narrower than {@link #isTailAttackStriking()}, which also fires on {@code 12} for
     * objects — blocks are never struck on that frame.
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
     * <p>Keyed off {@link #getTailAttackSwingFacing()} exactly as the object box is, and for the same
     * reason — the probe offsets follow the drawn facing. The probe fires on {@code 9} alone, which falls
     * inside the flip window, so for a player who swung facing right it resolves to {@code +21}: the tile
     * in front of them.
     *
     * @return the probe X, {@code +21} while drawn facing left, {@code -6} while drawn facing right
     */
    public double getTailAttackBlockProbeX() {
        return position.getX() + (getTailAttackSwingFacing() == RIGHT
            ? TAIL_ATTACK_BLOCK_PROBE_X_FACING_RIGHT
            : TAIL_ATTACK_BLOCK_PROBE_X_FACING_LEFT);
    }

    /**
     * Y of the tail's block-probe point, in sprite-pixel space: {@code +28} below the sprite top, i.e.
     * down at the feet.
     *
     * <p>Blocks are tested with a <b>single point</b>, not with {@link #getTailAttackBounds()}: the probe
     * offsets go into the tile probe, which resolves exactly one tile. Reusing the 15-tall object box here
     * would span two tile rows whenever the player is not tile-aligned — notably while their head is
     * pressed against a ceiling, where the 6px head padding shifts the sprite top up and drags the box's
     * top edge into the row above — and would break bricks level with the player's chest, which a tail
     * swing must never do.
     *
     * @return the probe Y
     */
    public double getTailAttackBlockProbeY() {
        return position.getY() + TAIL_ATTACK_BLOCK_PROBE_Y;
    }

    /**
     * Resolves this tick's tail strike against <b>static blocks</b>: a single probe point resolving to one
     * tile, tested on countdown frame {@code 9} only.
     *
     * <p>Called from {@link #updateFrame()} once the swing counter has advanced and collision has
     * settled this tick's position, which the probe is derived from — blocks are resolved inside the
     * player update, and the terrain grid is tile-aligned and rebuilt only when the level mutates, so it
     * is valid at any point in the tick.
     *
     * <p>The object half is deliberately not resolved here: it belongs to the object pass, so it
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
        // X follows the player node. Y is driven by the vertical-scroll
        // model instead of the node so the camera stays locked at the bottom of
        // a horizontal level (World 1-1) rather
        // than scrolling on every jump. Called once, at spawn.
        cameraState.setTarget(node);
        cameraState.setVerticalScrollProvider(verticalScroll::getInterpolatedCameraY);
    }

    @Override
    public void advanceAnimation() {
        animationContext.update(this);
        // Paired with the frame the animator just chose, because the hurt-flash blanking is part of drawing
        // that frame rather than a separate effect: it is decided before any sprite is drawn.
        applyHurtFlashVisibility();
    }

    @Override
    public void updateFrame() {
        // The swing counter first, and outside every early return below: it is a shared counter that
        // advances on the unconditional path after the player update, so it ticks through a halt, through
        // a suit loss and through the death throe alike. See tickTailAttackCountdown for what gating it
        // strands.
        tickTailAttackCountdown();

        // The carry lag flag, for the same reason and in the same place: a lag flag that stops being
        // advanced is a lag flag that lies, and this one gates every pickup.
        advanceCarryLag();

        // The vertical scroll, for the same reason: every tick starts from a camera at rest, and only a tick that
        // reaches updateVerticalScroll below moves it. A tick that returns early would otherwise leave the previous
        // tick's camera move in place, and the render frames between ticks would keep easing across it, so the view
        // would flicker for the whole pause wherever the screen refreshes faster than the simulation.
        verticalScroll.hold();

        // The death throe owns the tick: it freezes gameplay and plays out its own rise-and-fall off
        // the bottom of the screen (the death routine owns the Y motion while the rest of the game is
        // halted). Checked first: a dying player runs no physics, input or transition.
        if (runtimeState.isDying()) {
            tickDeath();
            return;
        }

        // The small→Super grow transition owns the tick: advance only the grow
        // flicker and skip all physics/input.
        if (runtimeState.isTransitioning()) {
            tickModeTransition();
            return;
        }

        // Going into a pipe owns the tick and drops the rest of the player update: no input, physics,
        // collision, scrolling or pit check, so a pipe on the bottom row never counts as falling off the level.
        if (runtimeState.isEnteringPipe()) {
            tickPipeEntering();
            return;
        }

        // Coming out of a pipe owns the tick in the same way
        if (runtimeState.isExitingPipe()) {
            tickPipeExiting();
            return;
        }

        // Another player is mid-transition and has halted gameplay: freeze this player in place. Snapshot
        // so render interpolation holds steady rather than easing toward a stale target.
        if (gameEngine.isGameplayHalted()) {
            position.snapshotPrevious();
            return;
        }

        // The run-off has ended where the player stands
        if (isRunOffHalted()) {
            position.snapshotPrevious();
            return;
        }

        // Past the level's right edge in the end-level run-off there is nothing to stand on, so the player walks on at
        // the height it left at, with no gravity, collision or pit check.
        if (isRunningOff() && isPastTheLevelsRightEdge()) {
            tickBeyondTheLevel();
            return;
        }

        final StaticEnvironmentCollisionGrid collisionGrid = gameEngine.getCollisionGrid();

        // Capture the pre-tick position for render interpolation. Done here
        // (rather than in the engine loop) so the engine never dereferences a
        // player's position — MapPlayer, which has none, simply no-ops.
        position.snapshotPrevious();

        handleSizeToggle();

        // Ducking is handled early so the flag is current for the height offset and collision probes.
        handleDucking(inputHandler);

        // Determine collision height offset from the ducking flag (Y=20 when small or ducking, Y=10
        // otherwise).
        final int heightOffset = determineHeightOffset();

        // Emergency exit (emexit) detection — checks for a solid non-one-way tile
        // above the player's head at horizontal center (X+8).
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

        // In low clearance, new button presses (jump) are discarded but NOT held-direction
        // input. Horizontal movement (acceleration, facing)
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

        // The run-off owns the facing and the walk, whatever the friction above made of DX
        if (isRunningOff()) {
            applyRunOffWalk();
        }

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
        // The collision pass flags the tick the head strikes something solid, whatever it is
        if (runtimeState.isHitCeiling()) {
            getSoundPlayer().play(BLOCK);
        }

        // Refine the logical state after physics + collision
        refinePlayerState(inputHandler, hitSomething, lowClearance);

        if (hasTail()) {
            // Tail attack (raccoon/tanooki B press on ground). Big and small
            // Mario are tailless, so they never trigger it.
            handleTailAttack(inputHandler);

            // Resolve the swing against terrain in the same breath as advancing it, now that
            // collision has settled this tick's position. Objects are struck later, from the
            // broadphase pass, keeping the player and object passes separate.
            resolveTailAttackAgainstBlocks();
        }

        // Special tiles such as pipes are handled after solid-tile and tail-block collision.
        // Going into a pipe ends the tick there: the player is drawn and the rest of the update is skipped.
        if (enterPipeIfPossible()) {
            return;
        }

        // Advance raccoon sprite animation (walk cycle, still/moving transitions)
        advanceAnimation();

        // Only now, on a frame the player was actually drawn as itself, does the hurt flash advance.
        // Both early returns above are frames that never reach it — the poof owns the drawing for its
        // whole duration — which is precisely what holds the invincibility at full length until gameplay
        // resumes. Decremented after advanceAnimation so this frame's visibility came from the
        // pre-decrement value.
        runtimeState.decrementHurtInvincibility();

        // The turn-around face timer, decremented right after the test that chose the face-forward frame
        // so, like the flash above, after advanceAnimation, because this tick's pose belonged to the
        // pre-decrement value.
        runtimeState.decrementTurnAroundFace();

        // The kick pose timer, decremented in that same block right after the frame was chosen and so for
        // the same reason.
        tickKickPose();

        // Advance the vertical camera scroll (locked at the level bottom unless
        // flying/climbing).
        updateVerticalScroll();

        // Sync visual
        updateVisualPosition();

        // Fall in a pit and die
        if (gameEngine.getCameraState().isBelowVisibleBottom(position)) {
            dieByFallingOff();
            applyHurtFlashVisibility();
        }
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

    private void tickDeath() {
        position.snapshotPrevious();

        // A pit death has no throe: nothing moves and nothing is drawn while the death music plays. The
        // level's end comes from PlayerDeathSequence when the music finishes, not from here.
        if (runtimeState.isDyingByFallingOff()) {
            applyHurtFlashVisibility();
            return;
        }

        // Pre-launch pause (a 48-tick countdown): the dead sprite is drawn in place with
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
            gameEngine.publish(new PlayerDeathFallEnded());
        }
    }

    void applyDeathGravityStep(final PlayerPosition position) {
        position.addToDY(PLAYER_DEATH_GRAVITY);
        if (position.getDY() > PLAYER_DEATH_FALL_MAX) {
            position.setDY(PLAYER_DEATH_FALL_MAX);
        }
        position.addToY(position.getDY());
    }

    boolean hasFallenOffLevel(final PlayerPosition position, final int rows) {
        return position.getY() > (double) (rows + 1) * TILE_SPRITE_SIZE;
    }

    @Override
    public boolean isHaltingGameplay() {
        return runtimeState.isTransitioning() || runtimeState.isDying() || runtimeState.isInPipe();
    }

    @Override
    public void announceDeath(final PlayerDeathKind kind) {
        // A death closes the P-Switch window at once, quietly: the death music is about to silence the level anyway
        gameEngine.getCollisionGrid().getPowerSwitchTimeWindow().reset();
        gameEngine.publish(new PlayerDeathStarted(kind));
    }

    @Override
    public void onRewardConsumption(final RewardLevelObject reward) {
        final PlayerMode mode = getMode();
        final boolean extraLife = reward.getRewardType() == ONE_UP;
        if (runtimeState.isTransitioning() || runtimeState.isInPipe()) {
            getSoundPlayer().play(extraLife ? SoundEffect.ONE_UP : POWER_UP);
            return;
        }
        if (extraLife) {
            getSoundPlayer().play(SoundEffect.ONE_UP);
        } else if (mode == SHRUNK) {
            turnToNormal();
            getSoundPlayer().play(POWER_UP);
        } else if (reward instanceof SuperLeaf && mode != RACCOON) {
            turnToRaccoon();
            getSoundPlayer().play(TANOOKI);
        } else {
            getSoundPlayer().play(POWER_UP);
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
        final PlayerPosition interpolated = position.interpolateBetweenPreviousAndCurrent(alpha);
        final PlayerPosition interpolatedPosition = interpolated
            .toTileUnitBased(getLevelScene().getDimensions());

        getNode()
            .setLocalTranslation(
                (float) interpolatedPosition.getX(),
                (float) interpolatedPosition.getY() - 2 + pipeStepUp(),
                getVisibility().getPlayerZ()
            );

        // Anything in the player's hands moves in the same breath, off the same interpolated position.
        // This is the only place a carried object's sprite is placed, and it has to be here: the node
        // above is re-placed every rendered frame while the simulation advances at a fixed 60 Hz, so a
        // carried object positioned from the simulation position would lag its holder by up to a tick
        // and visibly shake. Collision still reads the object's own logical position, which its state
        // routine assigns once per tick.
        placeCarriedObject(interpolated);

        // Advance the camera's vertical scroll with the same alpha so the Y axis
        // interpolates in lockstep with the node (X follows the node directly).
        verticalScroll.interpolate(alpha);
    }

    private void placeCarriedObject(final PlayerPosition at) {
        final CarriableLevelObject carried = runtimeState.getCarriedObject();
        if (carried == null) {
            return;
        }
        carried.drawCarriedAt(carried.carryAnchorFrom(this, at.getX(), at.getY()));
    }

    @Override
    public void onLayerSwitch() {
        setVisibility(getVisibility().opposite());
        updateForegroundLayerBuckets();
    }
}
