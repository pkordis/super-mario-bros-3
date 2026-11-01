package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaParaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.SlotPredecessor;
import house.x1337.app.smb3.game.object.level.enemy.family.KoopaTroopaBody;
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_PARATROOPA;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.model.game.Dimensions.doubleTileHeight;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Data
@Prototype
@RequiredArgsConstructor
public final class KoopaParaTroopa implements KoopaTroopaBody, SlotPredecessor {
    private static final int BODY_CANVAS_HEIGHT_PIXELS = 32;
    private static final int BYTE_MASK = 255;
    private static final int WING_HEIGHT_PIXELS = 16;
    public static final int BACK_COLUMN_OFFSET_PIXELS = 8;
    public static final int CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT = 1;
    public static final int HOP_Y_VELOCITY_FIXED_POINT = -48;
    public static final int WALK_FRAME_SHIFT = 2;
    public static final int WING_ABOVE_MIDDLE_ROW_PIXELS = 8;
    public static final int WING_LIFT_FIXED_POINT = 1;
    public static final int WING_PHASE_BIT = 4;
    public static final int WING_PHASE_OFFSET = 3;
    public static final int[] MIDDLE_ROW_CANVAS_TOP = {11, 9};
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(BOUND_BOX_WIDTH, BOUND_BOX_HEIGHT);
    private static final Dimensions SPRITE_DIMENSIONS = doubleTileHeight("KoopaParatroopa");

    @Accessors(fluent = true)
    private final boolean bouncesOffOtherObjects = true;
    private final KoopaParaTroopaAnimator animator = getBean(KoopaParaTroopaAnimator.class);
    private final LevelObjectType type = KOOPA_PARATROOPA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GameEngine gameEngine;
    private final Offset offset;

    private FixedPointVelocity velocity = STILL;
    private Geometry spriteGeometry;
    private Geometry wingGeometry;
    private KoopaTroopaMode mode = GREEN;
    private PixelPosition position;
    private Reward pendingScoreReward;
    private boolean animationHalfStep;
    private boolean ceilingContact;
    private boolean expired;
    private boolean facingRight;
    private boolean groundContact;
    private boolean grounded;
    private boolean handedOver;
    private boolean spawnedIntoScene;
    private boolean wingUp;
    private float baseDepth = Z_DEPTH_ENEMY;
    private int animationClock;
    private int walkFrameIndex;

    @PostConstruct
    void init() {
        position = PixelPosition.ofTile(offset);
        updatePose();
    }

    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(KoopaTroopaMode.class, MODE_ATTRIBUTE)
            .orElse(GREEN);
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
        wingGeometry = animator.buildWingSprite(this);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        gameEngine.getRootNode().attachChild(wingGeometry);

        erasePlacementFromBakedTexture();
        positionSprite();
    }

    @Override
    public void detach() {
        KoopaTroopaBody.super.detach();
        wingGeometry = detachPart(wingGeometry);
    }

    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (handedOver) {
            return;
        }
        handedOver = true;
        pendingScoreReward = levelScenePlayer.nextKillChainReward();
        levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
        handOverToKoopaTroopa(STOMP_HIT_COOLDOWN_TICKS);
        expired = true;
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (handedOver) {
            return;
        }
        handedOver = true;
        handOverToKoopaTroopa(NO_HIT_COOLDOWN_TICKS).onTailAttack(levelScenePlayer);
        expired = true;
    }

    @Override
    public void onStruckByKickedObject(final int knockXVelocityFixedPoint) {
        if (handedOver) {
            return;
        }
        handedOver = true;
        handOverToKoopaTroopa(NO_HIT_COOLDOWN_TICKS).onStruckByKickedObject(knockXVelocityFixedPoint);
        expired = true;
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
        return !handedOver;
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        if (!canHurtPlayer()) {
            return;
        }
        levelScenePlayer.getHurtBy(this);
    }

    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }
        updatePose();

        velocity = velocity.directedX(facingRight, WALK_SPEED_FIXED_POINT);
        stepWalkHorizontally();
        moveVertically();
        applyGravity();
        advanceAnimationClock();

        if (groundContact) {
            velocity = velocity.withY(HOP_Y_VELOCITY_FIXED_POINT);
        } else {
            if (ceilingContact) {
                velocity = velocity.withY(CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT);
            }
            velocity = velocity.plusY(-WING_LIFT_FIXED_POINT);
        }

        if (groundContact && isBlockBumpActiveUnderfoot()) {
            onBumpedFromBelow();
            return;
        }
        positionSprite();
    }

    public WorldOffset wingDrawOffset(final WorldOffset feetAnchor) {
        final float backColumn = facingRight ? 0f : BACK_COLUMN_OFFSET_PIXELS * PIXELS_TO_GAME_UNITS;
        final int wingTopCanvasRow = MIDDLE_ROW_CANVAS_TOP[walkFrameIndex] - WING_ABOVE_MIDDLE_ROW_PIXELS;
        final int wingBottomAboveBodyBottom = BODY_CANVAS_HEIGHT_PIXELS - wingTopCanvasRow - WING_HEIGHT_PIXELS;
        return WorldOffset.of(
            feetAnchor.x() + backColumn,
            spriteDrawOffset(feetAnchor).y() + wingBottomAboveBodyBottom * PIXELS_TO_GAME_UNITS,
            depthInFrontOfBody()
        );
    }

    private void onBumpedFromBelow() {
        handedOver = true;
        final KoopaTroopa troopa = handOverToKoopaTroopa(NO_HIT_COOLDOWN_TICKS);
        troopa.setPendingScoreReward(SCORE_100);
        troopa.onBumpedFromBelow();
        expired = true;
    }

    private boolean isBlockBumpActiveUnderfoot() {
        final int centerColumn = position.plusX(BODY_SIZE_PIXELS / 2.0).column();
        final int feetRow = position.plusY(BODY_SIZE_PIXELS).row();
        return gameEngine.getCollisionGrid().isBlockBumpActiveAt(Offset.of(centerColumn, feetRow));
    }

    private KoopaTroopa handOverToKoopaTroopa(final int hitCooldownTicks) {
        final KoopaTroopa troopa = getBean(
            KoopaTroopa.class,
            gameEngine,
            Offset.of(position.x() / TILE_SPRITE_SIZE, position.y() / TILE_SPRITE_SIZE)
        );
        troopa.configure(new LevelObjectData(
            Map.of(MODE_ATTRIBUTE, mode.name())
        ));
        troopa.replace(this, hitCooldownTicks);
        return troopa;
    }

    private void updatePose() {
        final int frame = (animationClock >> WALK_FRAME_SHIFT) & 1;
        if (frame != walkFrameIndex) {
            walkFrameIndex = frame;
            animator.applyCurrentFrame(this);
        }
        wingUp = ((animationClock + WING_PHASE_OFFSET) & WING_PHASE_BIT) == 0;
    }

    private void advanceAnimationClock() {
        animationHalfStep = !animationHalfStep;
        if (!animationHalfStep) {
            animationClock = (animationClock + 1) & BYTE_MASK;
        }
    }

    private void moveVertically() {
        groundContact = false;
        ceilingContact = false;
        final PixelPosition proposed = position.steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            restOn((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
            groundContact = true;
            return;
        }
        if (velocity.rising() && isCeilingAbove(proposed)) {
            position = position.withY((double) (proposed.row() + 1) * TILE_SPRITE_SIZE);
            velocity = velocity.withY(0);
            grounded = false;
            ceilingContact = true;
            return;
        }
        position = proposed;
        grounded = false;
    }

    private void positionSprite() {
        final WorldOffset feetAnchor = positionWalkingSprite();
        if (feetAnchor == null) {
            return;
        }
        animator.applyWingFrame(this);
        final WorldOffset wing = wingDrawOffset(feetAnchor);
        placePart(wingGeometry, wing.x(), wing.y(), wing.z());
    }
}
