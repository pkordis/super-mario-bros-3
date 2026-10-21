package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static com.jme3.scene.Spatial.CullHint.Always;
import static com.jme3.scene.Spatial.CullHint.Inherit;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY_FOOT;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_TROOPA;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.BUMP_LAUNCH_Y_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.KNOCK_X_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.LAUNCH_Y_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.model.game.Dimensions.doubleTileHeight;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Getter
@Prototype
@RequiredArgsConstructor
public final class KoopaTroopa implements KoopaTroopaFamilyEnemy {
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(BOUND_BOX_WIDTH, BOUND_BOX_HEIGHT);
    private static final Dimensions SPRITE_DIMENSIONS = doubleTileHeight("KoopaTroopa");
    private static final int[] FEET_FLICKER_MASKS = {2, 3, 4, 8, 16, 16};
    private static final int LEDGE_RECOVERY_STEPS = 2;
    private static final int SHAKE_PHASE_MASK = 7;
    private static final double WHAM_TAIL_OFFSET_PIXELS = 16;

    @Accessors(fluent = true)
    private final boolean bouncesOffOtherObjects = true;
    private final KoopaTroopaAnimator animator = getBean(KoopaTroopaAnimator.class);
    private final LevelObjectType type = KOOPA_TROOPA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GameEngine gameEngine;
    private final Offset offset;

    private Geometry spriteGeometry;
    private Geometry shellGeometry;
    private Geometry leftShellFootGeometry;
    private Geometry rightShellFootGeometry;
    private KoopaTroopaMode mode = RED;
    private KoopaTroopaState state = NORMAL;

    @Setter
    private boolean expired;
    @Setter
    private Reward pendingScoreReward;
    private boolean facingRight;
    private boolean grounded;
    private boolean ledgeTurned;
    private boolean spawnedIntoScene;
    private boolean upsideDown;
    private boolean wakeUpHalfTickPhase;
    private int hitCooldownTicks;
    private int wakeUpTicks;
    private int walkFrameIndex;
    private int walkFrameTicks;
    private PixelPosition position;
    private FixedPointVelocity velocity = STILL;

    /**
     * Only the position may be derived here. An enemy is constructed while {@code GameEngine.setupLevel}
     * is still building the collision grid, so there is no scene graph, no players and nothing to ask
     * about the world yet - all of which is why {@link #spawnIntoScene()} exists.
     */
    @PostConstruct
    void init() {
        position = PixelPosition.ofTile(offset);
    }

    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(KoopaTroopaMode.class, MODE_ATTRIBUTE)
            .orElse(RED);
    }

    @Override
    public AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(
            position.plus(BOUND_BOX_LEFT_OFFSET, BOUND_BOX_TOP_OFFSET),
            BOUNDS_PIXELS
        );
    }

    @Override
    public void spawnIntoScene() {
        if (spawnedIntoScene) {
            return;
        }
        spawnedIntoScene = true;
        faceClosestPlayer();

        spriteGeometry = animator.buildSprite(this);
        shellGeometry = animator.buildShellSprite(this);
        leftShellFootGeometry = animator.buildShellFootSprite(this, true);
        rightShellFootGeometry = animator.buildShellFootSprite(this, false);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        gameEngine.getRootNode().attachChild(shellGeometry);
        gameEngine.getRootNode().attachChild(leftShellFootGeometry);
        gameEngine.getRootNode().attachChild(rightShellFootGeometry);

        erasePlacementFromBakedTexture();
        showGeometryForState();
        positionSprite();
    }

    @Override
    public void detach() {
        KoopaTroopaFamilyEnemy.super.detach();
        shellGeometry = detached(shellGeometry);
        leftShellFootGeometry = detached(leftShellFootGeometry);
        rightShellFootGeometry = detached(rightShellFootGeometry);
    }

    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (doesNotRespondToPlayerContact()) {
            return;
        }
        hitCooldownTicks = STOMP_HIT_COOLDOWN_TICKS;
        pendingScoreReward = SCORE_100;
        levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
        enterShell();
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        final double playerX = levelScenePlayer.getPosition().getX();
        final boolean playerOnTheLeft = playerX + BODY_SIZE_PIXELS / 2.0 < position.x() + BODY_SIZE_PIXELS / 2.0;

        hitCooldownTicks = TAIL_STRIKE_HIT_COOLDOWN_TICKS;
        launchAwayFromPlayer(LAUNCH_Y_VELOCITY_FIXED_POINT, playerOnTheLeft);
        // dasm: Sound_QPlayer |= SND_PLAYERKICK - a tail wham is the kick sound, not the squish one.
        // Nothing to queue it on while the project has no sound engine.
        // FlipBits |= SPR_VFLIP (prg000.asm:6075) - an OR, so an already-inverted shell stays inverted.
        upsideDown = true;
        enterShell();
        spawnTailFlash(levelScenePlayer, playerOnTheLeft);
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        hurt(levelScenePlayer);
    }

    @Override
    public void onPlayerOverlap(final LevelScenePlayer levelScenePlayer) {
        hurt(levelScenePlayer);
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        if (doesNotRespondToPlayerContact()) {
            return;
        }
        levelScenePlayer.onHurt();
    }

    @Override
    public void onSideCollisionWith(final ActiveLevelObject other) {
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        final double otherCenterX = (otherBounds.left() + otherBounds.right()) / 2.0;
        facingRight = otherCenterX < position.x() + BODY_SIZE_PIXELS / 2.0;
    }

    void faceClosestPlayer() {
        final double center = position.x() + BODY_SIZE_PIXELS / 2.0;
        facingRight = gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + BODY_SIZE_PIXELS / 2.0 >= center)
            .orElse(false);
    }

    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }

        if (hitCooldownTicks > 0) {
            hitCooldownTicks--;
        }
        runWakeUpClock();

        if (state == SHELLED) {
            if (wakeUpTicks == 0) {
                // Object_ShellDoWakeUp pops its caller's return address when it wakes an object
                // (PRG000_D155), so the rest of the shelled routine is skipped on this one frame.
                wakeUp();
                return;
            }

            moveShellHorizontally();
            moveShellVertically();
        } else {
            velocity = velocity.directedX(facingRight, WALK_SPEED_FIXED_POINT);

            advanceWalkFrame();
            moveHorizontally();
            moveVertically();
            if (mode == RED) {
                guardAgainstLedge();
            }
        }
        applyGravity();
        reactToBlockBumpBelow();
        positionSprite();

        if (hasFallenOffLevel()) {
            setExpired(true);
        }
    }

    /**
     * How far left the shell quad is drawn this frame. {@code Object_ShakeAndCalcSprite} does not add an
     * offset: it leaves the carry clear on {@code Objects_X - Horz_Scroll} when the shake phase's low bit
     * is clear, so the sprite comes out a pixel short (dasm prg000.asm:4560-4572). Phase {@code 0} leaves
     * the carry set, hence the non-zero test.
     *
     * @return {@link KoopaTroopaFamilyEnemy#SHELL_JITTER_PIXELS} or {@code 0}
     */
    public int getShellJitterPixels() {
        if (!isShellShaking()) {
            return 0;
        }
        final int phase = wakeUpTicks & SHAKE_PHASE_MASK;
        return phase != 0 && phase % 2 == 0 ? SHELL_JITTER_PIXELS : 0;
    }

    /**
     * Whether the waking feet are showing this frame. The ROM simply withholds the two foot sprites on
     * the off ticks (dasm prg000.asm:2338-2344), leaving the shell drawn either way - which is why this
     * is a visibility question and not a choice of shell art.
     *
     * @return {@code true} on a tick the feet are drawn
     */
    public boolean isWakingFeetShowing() {
        if (state != SHELLED || wakeUpTicks >= SHELL_FEET_THRESHOLD_TICKS) {
            return false;
        }
        return (wakeUpTicks & FEET_FLICKER_MASKS[wakeUpTicks >> 4]) != 0;
    }

    public boolean isShellShaking() {
        return state == SHELLED && wakeUpTicks < SHELL_SHAKE_THRESHOLD_TICKS;
    }

    @Override
    public void positionSprite() {
        if (state != SHELLED) {
            KoopaTroopaFamilyEnemy.super.positionSprite();
            return;
        }
        if (shellGeometry == null) {
            return;
        }
        final WorldOffset feetAnchor = pixelToWorldOffset(position);
        if (feetAnchor == null) {
            return;
        }
        final WorldOffset shellOffset = shellDrawOffset(feetAnchor);
        shellGeometry.setLocalScale(1f, upsideDown ? -1f : 1f, 1f);
        shellOffset.applyTo(shellGeometry);

        positionWakingFoot(leftShellFootGeometry, shellOffset, true);
        positionWakingFoot(rightShellFootGeometry, shellOffset, false);
    }

    /**
     * Hangs one foot three pixels past its side of the shell, inverted with it, and shows it only on the
     * ticks the flicker says.
     *
     * <p>No vertical offset of its own, and that is where the ROM's {@code ObjWakeUp_FeetYOff} went: the
     * foot art is authored against the bottom of its own 8x16 canvas, so sharing the shell's anchor puts
     * it exactly where {@code +10} puts the ROM's pattern. The pay-off is that the inverted case needs
     * nothing extra either - the same negative Y scale and one-quad-height lift that rights the shell
     * carries the feet to the other side of it, which is all {@code ObjWakeUp_FeetYOff[1] = -10} is
     * saying (dasm prg000.asm:2148).
     */
    private void positionWakingFoot(
        final Geometry footGeometry,
        final WorldOffset shellOffset,
        final boolean leftFoot
    ) {
        if (footGeometry == null) {
            return;
        }
        footGeometry.setCullHint(isWakingFeetShowing() ? Inherit : Always);
        final int outwardPixels = leftFoot
            ? -SHELL_FOOT_OUTWARD_OFFSET_PIXELS
            : SHELL_RIGHT_SPRITE_OFFSET_PIXELS + SHELL_FOOT_OUTWARD_OFFSET_PIXELS;
        footGeometry.setLocalScale(1f, upsideDown ? -1f : 1f, 1f);
        WorldOffset
            .of(
                shellOffset.x() + outwardPixels * PIXELS_TO_GAME_UNITS,
                shellOffset.y(),
                Z_DEPTH_ENEMY_FOOT
            )
            .applyTo(footGeometry);
    }

    /**
     * Where the shell quad is translated to. The anchor is the same feet line the walking quad uses -
     * the shell's top sits on the tracked Y and it is one tile tall, so its bottom edge lands there too.
     *
     * <p>Two corrections ride on top. A negative Y scale mirrors the quad about its own bottom edge and
     * hangs it <em>below</em> the anchor, so an inverted shell is lifted one quad height to put it back
     * over the same ground - the vertical twin of the walking sprite's mirror compensation, and the
     * reason the waking feet end up 10 px <em>above</em> the shell without any separate handling
     * ({@code ObjWakeUp_FeetYOff = {10, -10}}, dasm prg000.asm:2148). And the shake subtracts its pixel.
     *
     * @param feetAnchor the quad's unflipped bottom-left corner, from {@code pixelToWorldOffset}
     * @return the translation to apply
     */
    public WorldOffset shellDrawOffset(final WorldOffset feetAnchor) {
        final float invertedLift = upsideDown ? TILE_SIZE_GAME_UNITS : 0f;
        return feetAnchor.plus(-getShellJitterPixels() * PIXELS_TO_GAME_UNITS, invertedLift, 0f);
    }

    private void runWakeUpClock() {
        if (state != SHELLED || wakeUpTicks == 0) {
            return;
        }
        if (wakeUpTicks >= SHELL_SHAKE_THRESHOLD_TICKS) {
            wakeUpHalfTickPhase = !wakeUpHalfTickPhase;
            if (wakeUpHalfTickPhase) {
                return;
            }
        }
        wakeUpTicks--;
    }

    private void wakeUp() {
        state = NORMAL;
        upsideDown = false;
        faceClosestPlayer();
        showGeometryForState();
        positionSprite();
    }

    private void enterShell() {
        state = SHELLED;
        wakeUpTicks = WAKE_UP_TICKS;
        wakeUpHalfTickPhase = false;
        ledgeTurned = false;
        showGeometryForState();
        positionSprite();
    }

    private void launchAwayFromPlayer(
        final int launchYVelocityFixedPoint,
        final boolean playerOnTheLeft
    ) {
        final EnemyKnockMotion.Direction knockDirection = EnemyKnockMotion.Direction.struckFrom(playerOnTheLeft);
        velocity = new FixedPointVelocity(
            knockDirection.getValue() * KNOCK_X_VELOCITY_FIXED_POINT,
            launchYVelocityFixedPoint
        );
        grounded = false;
    }

    private void reactToBlockBumpBelow() {
        if (!grounded) {
            return;
        }
        final int centerColumn = position.plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int feetRow = position.plusY(BODY_SIZE_PIXELS).row();
        if (!gameEngine.getCollisionGrid().isBlockBumpActiveAt(Offset.of(centerColumn, feetRow))) {
            return;
        }
        launchAwayFromPlayer(BUMP_LAUNCH_Y_VELOCITY_FIXED_POINT, isClosestPlayerOnTheLeft());
        upsideDown = true;
        facingRight = false;
        enterShell();
    }

    private void advanceWalkFrame() {
        walkFrameTicks++;
        if (walkFrameTicks < TICKS_PER_WALK_FRAME) {
            return;
        }
        walkFrameTicks = 0;
        walkFrameIndex = (walkFrameIndex + 1) % animator.walkFrameCount(this);
        animator.applyCurrentFrame(this);
    }

    private void moveHorizontally() {
        final PixelPosition proposed = position.steppedX(velocity);
        if (isWallAhead(proposed)) {
            // Object_FlipFace: the facing bit alone, and the step is abandoned rather than taken into
            // the wall (dasm prg004.asm:3827). Enough on its own, because facing is direction here.
            facingRight = !facingRight;
            return;
        }
        position = proposed;
    }

    private void moveShellHorizontally() {
        if (velocity.stationaryX()) {
            return;
        }
        final PixelPosition proposed = position.steppedX(velocity);
        if (isWallAhead(proposed)) {
            facingRight = !facingRight;
            velocity = velocity.negatedX();
            return;
        }
        position = proposed;
    }

    private void moveVertically() {
        final PixelPosition proposed = position.steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            // Object_HitGround on the grounded path (dasm prg004.asm:3850-3857, PRG004_B29B → B29F).
            // Resolved from the proposed feet row rather than by flooring the current Y, so a troopa
            // falling fast enough to overshoot into the tile still lands on the surface and not a tile
            // above it.
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            restOn((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
            return;
        }
        position = proposed;
        grounded = false;
    }

    private void moveShellVertically() {
        final PixelPosition proposed = position.steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            final int impactYVelocityFixedPoint = velocity.y();
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            restOn((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
            dampenLanding(impactYVelocityFixedPoint);
            return;
        }
        if (velocity.rising() && isCeilingAbove(proposed)) {
            final int headRow = proposed.row();
            position = position.withY((double) (headRow + 1) * TILE_SPRITE_SIZE);
            velocity = velocity.withY(CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT);
            grounded = false;
            return;
        }
        position = proposed;
        grounded = false;
    }

    private void dampenLanding(final int impactYVelocityFixedPoint) {
        velocity = velocity.withX(velocity.x() / SHELL_LANDING_X_DAMPING);
        final int reboundYVelocity = -(impactYVelocityFixedPoint / SHELL_LANDING_Y_DAMPING);
        if (reboundYVelocity < SHELL_MIN_BOUNCE_Y_VELOCITY_FIXED_POINT) {
            velocity = velocity.withY(reboundYVelocity);
            grounded = false;
        }
    }

    private void guardAgainstLedge() {
        if (grounded) {
            ledgeTurned = false;
            return;
        }
        if (ledgeTurned) {
            return;
        }
        ledgeTurned = true;

        facingRight = !facingRight;
        velocity = velocity.negatedX();
        for (int step = 0; step < LEDGE_RECOVERY_STEPS; step++) {
            position = position.steppedX(velocity);
        }
        restOn((double) position.row() * TILE_SPRITE_SIZE);
    }

    private void restOn(final double groundAlignedY) {
        position = position.withY(groundAlignedY);
        velocity = velocity.withY(0);
        grounded = true;
    }

    private void applyGravity() {
        velocity = velocity.falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);
    }

    private boolean doesNotRespondToPlayerContact() {
        return state != NORMAL || hitCooldownTicks != 0;
    }

    private boolean isClosestPlayerOnTheLeft() {
        final double center = position.x() + BODY_SIZE_PIXELS / 2.0;
        return gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + BODY_SIZE_PIXELS / 2.0 < center)
            .orElse(false);
    }

    private void spawnTailFlash(
        final LevelScenePlayer levelScenePlayer,
        final boolean playerOnTheLeft
    ) {
        final double tailSideOffset = playerOnTheLeft ? WHAM_TAIL_OFFSET_PIXELS : -WHAM_TAIL_OFFSET_PIXELS;
        getBean(TailAttackFlashMotionManager.class).spawn(
            gameEngine,
            Offset.of(
                levelScenePlayer.getPosition().getX() + tailSideOffset,
                levelScenePlayer.getPosition().getY() + WHAM_TAIL_OFFSET_PIXELS
            )
        );
    }

    private void showGeometryForState() {
        final boolean shelled = state == SHELLED;
        setVisible(spriteGeometry, !shelled);
        setVisible(shellGeometry, shelled);
        // The feet are driven per frame by positionSprite; hidden outright whenever the shell is not up.
        setVisible(leftShellFootGeometry, false);
        setVisible(rightShellFootGeometry, false);
    }

    private void setVisible(final Geometry geometry, final boolean visible) {
        if (geometry != null) {
            geometry.setCullHint(visible ? Inherit : Always);
        }
    }

    private Geometry detached(final Geometry geometry) {
        if (geometry != null) {
            gameEngine.getRootNode().detachChild(geometry);
        }
        return null;
    }
}
