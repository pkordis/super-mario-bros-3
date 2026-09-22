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
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.LEFT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.RIGHT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.strikeFrom;
import static java.lang.Math.floor;
import static java.lang.Math.min;

@Getter
@Prototype
@RequiredArgsConstructor
public final class Goomba implements EnemyLevelObject {
    private static final int WALK_SPEED_FIXED_POINT = 8;
    private static final int GRAVITY_FIXED_POINT = 3;
    private static final int MAX_FALL_FIXED_POINT = 64;
    private static final int TICKS_PER_WALK_FRAME = 8;
    private static final int SQUISH_TICKS = 16;
    private static final double WHAM_TAIL_OFFSET_PIXELS = 16;
    private static final double FIXED_POINT_VELOCITY_TO_PIXELS = 1.0 / TILE_SPRITE_SIZE;
    private static final int SPRITE_SIZE_PIXELS = TILE_SPRITE_SIZE;
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(SPRITE_SIZE_PIXELS, SPRITE_SIZE_PIXELS);
    private static final Dimensions SPRITE_DIMENSIONS = Dimensions.fullTile("Goomba");

    @Accessors(fluent = true)
    private final boolean bouncesOffOtherObjects = true;
    private final GoombaAnimator animator = getBean(GoombaAnimator.class);
    private final LevelObjectType type = GOOMBA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GoombaMode mode = NORMAL;

    private final GameEngine gameEngine;
    private final Offset offset;
    private Geometry spriteGeometry;

    @Setter
    private boolean expired;
    @Setter
    private Reward pendingScoreReward;
    private boolean spawnedIntoScene;
    private boolean facingRight;
    private boolean grounded;
    private boolean squished;
    private int squishTicksRemaining;
    private EnemyKnockMotion whamMotion; // Non-null once tail-struck: the death throw that carries it off the level
    private double pixelX;
    private double pixelY;
    private int xVelocityFixedPoint;
    private int yVelocityFixedPoint;
    private int walkFrameTicks;
    private int walkFrameIndex;

    @PostConstruct
    void init() {
        pixelX = (double) offset.x() * TILE_SPRITE_SIZE;
        pixelY = (double) offset.y() * TILE_SPRITE_SIZE;
    }

    @Override
    public void spawnIntoScene() {
        if (spawnedIntoScene) {
            return;
        }
        spawnedIntoScene = true;
        faceClosestPlayer();

        spriteGeometry = animator.buildSprite(this);
        gameEngine.getRootNode().attachChild(spriteGeometry);

        eraseFromBakedTexture(
            gameEngine.getLayerGeometry(NON_PLAYABLE_CHARACTERS),
            gameEngine.getLevelScene().getDimensions()
        );
        positionSprite();
    }

    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (squished) {
            return;
        }
        squished = true;
        squishTicksRemaining = SQUISH_TICKS;
        pendingScoreReward = SCORE_100;
        // Halt horizontal movement, as ObjState_Squashed does on touching the ground.
        xVelocityFixedPoint = 0;
        animator.applyCurrentFrame(this);
        levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (squished || whamMotion != null) {
            return;
        }
        final double playerX = levelScenePlayer.getPosition().getX();
        final boolean playerOnTheLeft = playerX + SPRITE_SIZE_PIXELS / 2.0 < pixelX + SPRITE_SIZE_PIXELS / 2.0;
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
        return !squished && whamMotion == null;
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        if (squished || whamMotion != null) {
            return;
        }
        levelScenePlayer.onHurt();
    }

    @Override
    public void onSideCollisionWith(final ActiveLevelObject other) {
        final AxisAlignedBoundingBox otherBounds = other.getBounds();
        final double otherCenterX = (otherBounds.left() + otherBounds.right()) / 2.0;
        facingRight = otherCenterX < pixelX + SPRITE_SIZE_PIXELS / 2.0;
    }

    void faceClosestPlayer() {
        final double center = pixelX + SPRITE_SIZE_PIXELS / 2.0;
        facingRight = gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(pixelX, pixelY)
            .map(closestPlayer -> closestPlayer.getPosition().getX() + SPRITE_SIZE_PIXELS / 2.0 >= center)
            .orElse(false);
    }

    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }

        if (whamMotion != null) {
            tickDeathByTailAttack();
            return;
        }

        if (squished) {
            tickSquish();
            return;
        }

        // Facing IS direction: the handler reloads GroundTroop_XVel from the flip bit every frame.
        xVelocityFixedPoint = facingRight ? WALK_SPEED_FIXED_POINT : -WALK_SPEED_FIXED_POINT;

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
        pixelX += whamMotion.getStepX();
        pixelY += whamMotion.getStepY();
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
        final double proposedX = pixelX + xVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
        if (isWallAhead(proposedX)) {
            facingRight = !facingRight;
            return;
        }
        pixelX = proposedX;
    }

    private void moveVertically() {
        final double proposedY = pixelY + yVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
        if (yVelocityFixedPoint >= 0 && isGroundBelow(proposedY)) {
            final int feetRow = (int) floor((proposedY + SPRITE_SIZE_PIXELS) / TILE_SPRITE_SIZE);
            pixelY = (double) feetRow * TILE_SPRITE_SIZE - SPRITE_SIZE_PIXELS;
            yVelocityFixedPoint = 0;
            grounded = true;
            return;
        }
        pixelY = proposedY;
        grounded = false;
    }

    private void applyGravity() {
        yVelocityFixedPoint = min(yVelocityFixedPoint + GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);
    }

    private void positionSprite() {
        if (spriteGeometry == null) {
            return;
        }
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final float worldX = (float) (pixelX / TILE_SPRITE_SIZE);
        // Drop the sprite by one logical pixel, for the same reason SuperMushroom does: resting feet
        // are mathematically flush with the tile top but rasterise a pixel high, leaving a seam above
        // the surface. Purely visual - the collision maths above is untouched.
        final float worldY = (rows - 1) - (float) (pixelY / TILE_SPRITE_SIZE) - 1f / TILE_SPRITE_SIZE;

        // A tail-struck Goomba is drawn inverted (Objects_FlipBits |= SPR_VFLIP). The quad's origin is
        // its bottom-left corner, so a negative Y scale mirrors it about that edge and hangs it below the
        // anchor; lifting the translation by one quad height puts it back over the same ground. Enemy
        // sprites already render with face culling off, which is what a negative scale needs.
        final boolean upsideDown = whamMotion != null && whamMotion.isUpsideDown();
        final float quadHeight = getSpriteDimensions().height();
        spriteGeometry.setLocalScale(1f, upsideDown ? -1f : 1f, 1f);
        spriteGeometry.setLocalTranslation(worldX, upsideDown ? worldY + quadHeight : worldY, Z_DEPTH_ENEMY);
    }

    private boolean isWallAhead(final double proposedX) {
        final int midRow = (int) floor((pixelY + SPRITE_SIZE_PIXELS / 2.0) / TILE_SPRITE_SIZE);
        final int leadingColumn = xVelocityFixedPoint > 0
            ? (int) floor((proposedX + SPRITE_SIZE_PIXELS - 1) / TILE_SPRITE_SIZE)
            : (int) floor(proposedX / TILE_SPRITE_SIZE);
        return gameEngine.getCollisionGrid().isSolidTile(leadingColumn, midRow);
    }

    private boolean isGroundBelow(final double proposedY) {
        final int centerColumn = (int) floor((pixelX + SPRITE_SIZE_PIXELS / 2.0) / TILE_SPRITE_SIZE);
        final int feetRow = (int) floor((proposedY + SPRITE_SIZE_PIXELS) / TILE_SPRITE_SIZE);
        return gameEngine.getCollisionGrid().isSolidTile(centerColumn, feetRow);
    }

    private boolean hasFallenOffLevel() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        return pixelY / TILE_SPRITE_SIZE > rows + 1;
    }
}
