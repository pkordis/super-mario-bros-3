package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaFamilyEnemy;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_TROOPA;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.HELD;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KICKED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.BUMP_LAUNCH_Y_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.LEFT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.RIGHT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.KNOCK_X_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.LAUNCH_Y_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.model.game.Dimensions.doubleTileHeight;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

/**
 * The Koopa Troopa: a ground troop that walks (the red one turns back at ledges) and goes into its shell
 * when defeated.
 *
 * <p>This class keeps what is the troopa's own - its lifecycle, walking, and how it answers each kind of
 * contact - and dispatches the tick by state. The shell's behavior lives on
 * {@code KoopaTroopaShellCapabilities}, and the drawing on {@link KoopaTroopaFamilyEnemy}, which is built
 * on it.
 */
@Data
@Prototype
@RequiredArgsConstructor
public final class KoopaTroopa implements KoopaTroopaFamilyEnemy {
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(BOUND_BOX_WIDTH, BOUND_BOX_HEIGHT);
    private static final Dimensions SPRITE_DIMENSIONS = doubleTileHeight("KoopaTroopa");
    private static final int LEDGE_RECOVERY_STEPS = 2;
    private static final double WHAM_TAIL_OFFSET_PIXELS = 16;

    @Accessors(fluent = true)
    private final boolean bouncesOffOtherObjects = true;
    private final KoopaTroopaAnimator animator = getBean(KoopaTroopaAnimator.class);
    private final LevelObjectType type = KOOPA_TROOPA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GameEngine gameEngine;
    private final Offset offset;

    private FixedPointVelocity velocity = STILL;
    private Geometry leftShellFootGeometry;
    private Geometry rightShellFootGeometry;
    private Geometry shellGeometry;
    private Geometry spriteGeometry;
    private KoopaTroopaMode mode = RED;
    private KoopaTroopaState state = NORMAL;
    private LevelScenePlayer carrier;
    private PixelPosition position;
    private Reward pendingScoreReward;
    private boolean expired;
    private boolean facingRight;
    private boolean grounded;
    private boolean ledgeTurned;
    private boolean spawnedIntoScene;
    private boolean upsideDown;
    private float baseDepth = Z_DEPTH_ENEMY;
    private int hitCooldownTicks;
    private int killTally;
    private int spinTicks;
    private int wakeUpThrottlePhase;
    private int wakeUpTicks;
    private int walkFrameIndex;
    private int walkFrameTicks;

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
        final Node rootNode = gameEngine.getRootNode();
        spawnedIntoScene = true;
        faceClosestPlayer();

        spriteGeometry = animator.buildSprite(this);
        shellGeometry = animator.buildShellSprite(this);
        leftShellFootGeometry = animator.buildShellFootSprite(this, true);
        rightShellFootGeometry = animator.buildShellFootSprite(this, false);
        rootNode.attachChild(spriteGeometry);
        rootNode.attachChild(shellGeometry);
        rootNode.attachChild(leftShellFootGeometry);
        rootNode.attachChild(rightShellFootGeometry);

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
    public void refreshShellFrame() {
        animator.applyShellFrame(this);
    }

    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (hitCooldownTicks == 0 && state == SHELLED) {
            getShellStompedBy(levelScenePlayer);
            return;
        }
        if (hitCooldownTicks == 0 && state == KICKED) {
            hitCooldownTicks = STOMP_HIT_COOLDOWN_TICKS;
            levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
            velocity = STILL;
            enterShell();
            return;
        }
        if (!canHurtPlayer()) {
            return;
        }
        hitCooldownTicks = STOMP_HIT_COOLDOWN_TICKS;
        pendingScoreReward = levelScenePlayer.nextKillChainReward();
        levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
        enterShell();
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        final double playerX = levelScenePlayer.getPosition().getX();
        final boolean playerOnTheLeft = playerX + BODY_SIZE_PIXELS / 2.0 < position.x() + BODY_SIZE_PIXELS / 2.0;

        hitCooldownTicks = TAIL_STRIKE_HIT_COOLDOWN_TICKS;
        launchAwayFromPlayer(LAUNCH_Y_VELOCITY_FIXED_POINT, playerOnTheLeft);
        upsideDown = true;
        enterShell();
        spawnTailFlash(levelScenePlayer, playerOnTheLeft);
    }

    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        holdKickOrHurt(levelScenePlayer);
    }

    @Override
    public void onPlayerOverlap(final LevelScenePlayer levelScenePlayer) {
        holdKickOrHurt(levelScenePlayer);
    }

    @Override
    public boolean canHurtPlayer() {
        return (state == NORMAL || state == KICKED) && hitCooldownTicks == 0;
    }

    @Override
    public boolean isHittable() {
        return state != KILLED;
    }

    @Override
    public boolean isDefeated() {
        return state == KILLED;
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        if (!canHurtPlayer()) {
            return;
        }
        if (state == KICKED && isPlayerChasingThisShell(levelScenePlayer)) {
            return;
        }
        levelScenePlayer.getHurtBy(this);
    }

    @Override
    public void onSideCollisionWith(final ActiveLevelObject other) {
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        final double otherCenterX = (otherBounds.left() + otherBounds.right()) / 2.0;
        facingRight = otherCenterX < position.x() + BODY_SIZE_PIXELS / 2.0;
    }

    private void getShellStompedBy(final LevelScenePlayer levelScenePlayer) {
        pendingScoreReward = levelScenePlayer.nextKillChainReward();
        levelScenePlayer.kickWithoutPose(this);
    }

    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }
        if (hitCooldownTicks > 0) {
            hitCooldownTicks--;
        }
        if (state == HELD) {
            tickHeld();
            return;
        }
        if (state == KILLED) {
            tickKilled();
            return;
        }
        if (state == KICKED) {
            tickKicked();
            return;
        }

        runWakeUpClock();

        if (state == SHELLED) {
            if (wakeUpTicks == 0) {
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
            facingRight = !facingRight;
            return;
        }
        position = proposed;
    }

    private void moveVertically() {
        final PixelPosition proposed = position.steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            restOn((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
            return;
        }
        position = proposed;
        grounded = false;
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

    private void launchAwayFromPlayer(
        final int launchYVelocityFixedPoint,
        final boolean playerOnTheLeft
    ) {
        final EnemyKnockMotion.Direction knockDirection = playerOnTheLeft ? LEFT : RIGHT;
        velocity = new FixedPointVelocity(
            knockDirection.getValue() * KNOCK_X_VELOCITY_FIXED_POINT,
            launchYVelocityFixedPoint
        );
        grounded = false;
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

    private Geometry detached(final Geometry geometry) {
        if (geometry != null) {
            gameEngine.getRootNode().detachChild(geometry);
        }
        return null;
    }
}
