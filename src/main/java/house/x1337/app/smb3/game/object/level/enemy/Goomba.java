package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.GoombaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
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

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.LEFT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.RIGHT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.strikeFrom;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Getter
@Prototype
@RequiredArgsConstructor
public final class Goomba implements GoombaFamilyEnemy {
    private static final double WHAM_TAIL_OFFSET_PIXELS = 16;
    private static final int BOUND_BOX_HEIGHT = 8;
    private static final int BOUND_BOX_LEFT_OFFSET = 1;
    private static final int BOUND_BOX_TOP_OFFSET = 2;
    private static final int BOUND_BOX_WIDTH = 13;
    private static final int MAX_FALL_FIXED_POINT = 64;
    private static final int SQUISH_TICKS = 16;
    private static final int TICKS_PER_WALK_FRAME = 8;
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(BOUND_BOX_WIDTH, BOUND_BOX_HEIGHT);
    private static final Dimensions SPRITE_DIMENSIONS = Dimensions.fullTile("Goomba");

    @Accessors(fluent = true)
    private final boolean bouncesOffOtherObjects = true;
    private final GoombaAnimator animator = getBean(GoombaAnimator.class);
    private final LevelObjectType type = GOOMBA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GameEngine gameEngine;
    private final Offset offset;

    private Geometry spriteGeometry;
    private GoombaMode mode = NORMAL;

    @Setter
    private boolean expired;
    @Setter
    private Reward pendingScoreReward;
    private EnemyKnockMotion whamMotion;
    private boolean adoptedSlot;
    private boolean facingRight;
    private boolean grounded;
    private boolean spawnedIntoScene;
    private boolean squished;
    private int hitCooldownTicks;
    private int squishTicksRemaining;
    private int walkFrameIndex;
    private int walkFrameTicks;
    private PixelPosition position;
    private FixedPointVelocity velocity = STILL;

    @PostConstruct
    void init() {
        position = PixelPosition.ofTile(offset);
    }

    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(GoombaMode.class, MODE_ATTRIBUTE)
            .orElse(NORMAL);
    }

    public void replace(
        final ParaGoomba paraGoomba,
        final int hitCooldownTicks
    ) {
        this.position = paraGoomba.getPosition();
        this.facingRight = paraGoomba.isFacingRight();
        this.hitCooldownTicks = hitCooldownTicks;
        // Deliberately not the Para-Goomba's velocity: the ROM zeroes YVel on the hand-over
        // (PRG000_D31E) and the walk reloads XVel from the facing on its first frame anyway.
        this.velocity = STILL.directedX(facingRight, WALK_SPEED_FIXED_POINT);
        this.adoptedSlot = true;
        gameEngine.getActiveObjectGrid().spawnEnemy(this);
    }

    /**
     * The Goomba's collision box, inset from its 16x16 sprite to the ROM's {@code Object_BoundBox} row
     * 01 (dasm prg000: left {@code +1}, width 13, top {@code +2}, height 8). Overridden rather than left
     * to the full-tile default so object overlap engages where the ROM's does - a touch narrower, and far
     * shorter, than the sprite.
     *
     * @return the box spanning X {@code [x+1, x+14]}, Y {@code [y+2, y+10]}
     */
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
        if (!adoptedSlot) {
            faceClosestPlayer();
        }

        spriteGeometry = animator.buildSprite(this);
        gameEngine.getRootNode().attachChild(spriteGeometry);

        if (!adoptedSlot) {
            erasePlacementFromBakedTexture();
        }
        positionSprite();
    }

    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (!isHittable()) {
            return;
        }
        squished = true;
        squishTicksRemaining = SQUISH_TICKS;
        pendingScoreReward = SCORE_100;
        // Halt horizontal movement, as ObjState_Squashed does on touching the ground.
        velocity = velocity.withX(0);
        animator.applyCurrentFrame(this);
        levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (!isHittable()) {
            return;
        }
        final double playerX = levelScenePlayer.getPosition().getX();
        final boolean playerOnTheLeft = playerX + SPRITE_SIZE_PIXELS / 2.0 < position.x() + SPRITE_SIZE_PIXELS / 2.0;
        whamMotion = strikeFrom(playerOnTheLeft ? LEFT : RIGHT);
        pendingScoreReward = SCORE_100;

        // Put the flash on the side the Goomba is actually on, so it always lands between the two rather
        // than off the player's far shoulder. Keying off which side was struck rather than off the
        // player's facing keeps it right even when the two disagree - the tail sweeps through a full arc,
        // so a Goomba can legitimately be clipped behind a player who is already turning away.
        final double tailSideOffset = playerOnTheLeft ? WHAM_TAIL_OFFSET_PIXELS : -WHAM_TAIL_OFFSET_PIXELS;
        getBean(TailAttackFlashMotionManager.class).spawn(
            gameEngine,
            Offset.of(
                playerX + tailSideOffset,
                levelScenePlayer.getPosition().getY() + WHAM_TAIL_OFFSET_PIXELS
            )
        );
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
    public boolean isHittable() {
        return !squished && whamMotion == null && hitCooldownTicks == 0;
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        if (!canHurtPlayer()) {
            return;
        }
        levelScenePlayer.onHurt();
    }

    @Override
    public void onSideCollisionWith(final ActiveLevelObject other) {
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        final double otherCenterX = (otherBounds.left() + otherBounds.right()) / 2.0;
        facingRight = otherCenterX < position.x() + SPRITE_SIZE_PIXELS / 2.0;
    }

    void faceClosestPlayer() {
        final double center = position.x() + SPRITE_SIZE_PIXELS / 2.0;
        facingRight = gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + SPRITE_SIZE_PIXELS / 2.0 >= center)
            .orElse(false);
    }

    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }

        // Objects_Timer2 is run down by the central object loop (dasm prg000.asm:1862), whatever state
        // the object is in, so it is decremented before any of the state branches below.
        if (hitCooldownTicks > 0) {
            hitCooldownTicks--;
        }

        if (whamMotion != null) {
            tickDeathByTailAttack();
            return;
        }

        if (squished) {
            tickSquish();
            return;
        }

        // Facing IS direction
        velocity = velocity.directedX(facingRight, WALK_SPEED_FIXED_POINT);

        advanceWalkFrame();
        moveHorizontally();
        moveVertically();
        applyGravity();
        positionSprite();

        if (hasFallenOffLevel()) {
            setExpired(true);
        }
    }

    private void tickSquish() {
        moveVertically();
        applyGravity();
        positionSprite();

        squishTicksRemaining--;
        if (squishTicksRemaining <= 0 || hasFallenOffLevel()) {
            setExpired(true);
        }
    }

    private void tickDeathByTailAttack() {
        whamMotion.advance();
        position = position.stepped(whamMotion.getStepVelocity());
        positionSprite();

        if (hasFallenOffLevel()) {
            setExpired(true);
        }
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
            final int feetRow = proposed.plusY(SPRITE_SIZE_PIXELS).row();
            position = position.withY((double) feetRow * TILE_SPRITE_SIZE - SPRITE_SIZE_PIXELS);
            velocity = velocity.withY(0);
            grounded = true;
            return;
        }
        position = proposed;
        grounded = false;
    }

    private void applyGravity() {
        velocity = velocity.falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);
    }

    private void positionSprite() {
        final WorldOffset worldOffset = pixelToWorldOffset(position);
        if (worldOffset == null) {
            return;
        }

        // A tail-struck Goomba is drawn inverted (Objects_FlipBits |= SPR_VFLIP). The quad's origin is
        // its bottom-left corner, so a negative Y scale mirrors it about that edge and hangs it below the
        // anchor; lifting the translation by one quad height puts it back over the same ground. Enemy
        // sprites already render with face culling off, which is what a negative scale needs.
        final boolean upsideDown = whamMotion != null && whamMotion.isUpsideDown();
        final float quadHeight = getSpriteDimensions().height();
        spriteGeometry.setLocalScale(1f, upsideDown ? -1f : 1f, 1f);
        spriteGeometry.setLocalTranslation(
            worldOffset.x(),
            upsideDown ? worldOffset.y() + quadHeight : worldOffset.y(),
            worldOffset.z()
        );
    }
}
