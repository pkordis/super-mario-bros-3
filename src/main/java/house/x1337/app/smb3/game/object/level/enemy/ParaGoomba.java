package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.GoombaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY_WING;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PARA_GOOMBA;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.model.game.Dimensions.fullTile;
import static java.lang.Math.floor;
import static java.lang.Math.min;

@Getter
@Prototype
@RequiredArgsConstructor
public final class ParaGoomba implements GoombaFamilyEnemy {
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(BOUND_BOX_WIDTH, BOUND_BOX_HEIGHT);
    private static final Dimensions SPRITE_DIMENSIONS = fullTile("ParaGoomba");

    private final ParaGoombaAnimator animator = getBean(ParaGoombaAnimator.class);
    private final LevelObjectType type = PARA_GOOMBA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GameEngine gameEngine;
    private final Offset offset;

    private Geometry spriteGeometry;
    private Geometry leftWingGeometry;
    private Geometry rightWingGeometry;
    private GoombaMode mode = NORMAL;

    @Setter
    private boolean expired;
    @Setter
    private Reward pendingScoreReward;
    private boolean ceilingContact;
    private boolean deWinged;
    private boolean facingRight;
    private boolean groundContact;
    private boolean grounded;
    private boolean spawnedIntoScene;
    private boolean tailStruck;
    private boolean wingUp;
    private double pixelX;
    private double pixelY;
    private int hopCooldownTicks;
    private int hopCounter;
    private int refaceClock;
    private int walkFrameIndex;
    private int walkFrameTicks;
    private int wingClock;
    private int xVelocityFixedPoint;
    private int yVelocityFixedPoint;

    @PostConstruct
    void init() {
        pixelX = (double) offset.x() * TILE_SPRITE_SIZE;
        pixelY = (double) offset.y() * TILE_SPRITE_SIZE;
    }

    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(GoombaMode.class, MODE_ATTRIBUTE)
            .orElse(NORMAL);
    }

    @Override
    public AxisAlignedBoundingBox getBounds() {
        final double left = pixelX + BOUND_BOX_LEFT_OFFSET;
        final double top = pixelY + BOUND_BOX_TOP_OFFSET;
        return new AxisAlignedBoundingBox(left, top, left + BOUND_BOX_WIDTH, top + BOUND_BOX_HEIGHT);
    }

    @Override
    public void spawnIntoScene() {
        if (spawnedIntoScene) {
            return;
        }
        spawnedIntoScene = true;
        faceClosestPlayer();

        spriteGeometry = animator.buildSprite(this);
        leftWingGeometry = animator.buildWingSprite(this, true);
        rightWingGeometry = animator.buildWingSprite(this, false);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        gameEngine.getRootNode().attachChild(leftWingGeometry);
        gameEngine.getRootNode().attachChild(rightWingGeometry);

        // The whole stamp an author painted, not just the anchor cell: the wing cells sit a row above
        // the body and a column either side of it, and would otherwise stay baked where it was placed.
        erasePlacementFromBakedTexture();
        positionSprite();
    }

    @Override
    public void detach() {
        GoombaFamilyEnemy.super.detach();
        detachWings();
    }

    @Override
    public void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        if (deWinged || tailStruck) {
            return;
        }
        deWinged = true;
        pendingScoreReward = SCORE_100;
        levelScenePlayer.getPosition().setDY(PLAYER_STOMP_BOUNCE_YVEL);
        replaceWithGroundGoomba(STOMP_HIT_COOLDOWN_TICKS);
        setExpired(true);
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (deWinged || tailStruck) {
            return;
        }
        tailStruck = true;
        replaceWithGroundGoomba(NO_HIT_COOLDOWN_TICKS).onTailAttack(levelScenePlayer);
        setExpired(true);
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
        return !deWinged && !tailStruck;
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        if (deWinged || tailStruck) {
            return;
        }
        levelScenePlayer.onHurt();
    }

    /**
     * One frame of {@code ObjNorm_ParaGoomba}, in the handler's own order: move, net-float the Y
     * velocity, advance the two clocks, then react to whatever the move detected.
     */
    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }

        // Objects_Timer is decremented by the central object loop before the handler runs
        // (dasm prg000.asm:1855), so the hop test below sees this frame's decremented value.
        if (hopCooldownTicks > 0) {
            hopCooldownTicks--;
        }

        // Facing IS direction, as for the ground troop: XVel is reloaded from the flip bit every frame.
        xVelocityFixedPoint = facingRight ? WALK_SPEED_FIXED_POINT : -WALK_SPEED_FIXED_POINT;

        advanceWalkFrame();
        moveHorizontally();
        moveVertically();
        applyFloatingGravity();
        advanceRefaceClock();
        advanceWingClock();

        if (ceilingContact) {
            yVelocityFixedPoint = CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT;
        }
        if (groundContact) {
            onGroundContact();
        }
        positionSprite();

        if (hasFallenOffLevel()) {
            setExpired(true);
        }
    }

    void faceClosestPlayer() {
        final double center = pixelX + SPRITE_SIZE_PIXELS / 2.0;
        facingRight = gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(pixelX, pixelY)
            .map(closestPlayer -> closestPlayer.getPosition().getX() + SPRITE_SIZE_PIXELS / 2.0 >= center)
            .orElse(false);
    }

    private Goomba replaceWithGroundGoomba(final int hitCooldownTicks) {
        final Goomba goomba = getBean(
            Goomba.class,
            gameEngine,
            Offset.of(pixelX / TILE_SPRITE_SIZE, pixelY / TILE_SPRITE_SIZE)
        );
        goomba.configure(new LevelObjectData(
            Map.of(MODE_ATTRIBUTE, mode.name())
        ));
        goomba.replace(this, hitCooldownTicks);
        return goomba;
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

    /**
     * {@code Objects_Var5++} every frame. It only ever has an effect on a frame that also made ground
     * contact, which is what makes the re-facing "every 64 ticks, but only while grounded".
     */
    private void advanceRefaceClock() {
        refaceClock = (refaceClock + 1) & OBJECT_CLOCK_WRAP;
    }

    /**
     * {@code Objects_Var3}: {@code +1} a frame, plus {@code +2} more while rising with
     * {@code Var4 == 0} - i.e. on the way up out of the big hop, where the wings beat faster. Read after
     * the float decrement, as the handler does.
     */
    private void advanceWingClock() {
        final int risingBonus = hopCounter == 0 && yVelocityFixedPoint < 0 ? RISING_WING_FLAP_BONUS : 0;
        wingClock = (wingClock + risingBonus + 1) & OBJECT_CLOCK_WRAP;
        applyWingPhase();
    }

    private void applyWingPhase() {
        final boolean raised = (wingClock & WING_FLAP_PHASE_BIT) != 0;
        if (raised == wingUp) {
            return;
        }
        wingUp = raised;
        animator.applyCurrentWingFrames(this);
    }

    /**
     * {@code DetStat & $04} - the landing branch: re-face the player on the 64-tick beat, fold the wings
     * while between hop cycles, and launch the next hop once the post-big-hop cooldown has run out.
     */
    private void onGroundContact() {
        if ((refaceClock & REFACE_PERIOD_MASK) == 0) {
            faceClosestPlayer();
        }
        if (hopCounter == 0) {
            // Var3 = 0 on every grounded frame between cycles, so the wings sit still through the cooldown.
            wingClock = 0;
            applyWingPhase();
        }
        if (hopCooldownTicks > 0) {
            return;
        }

        hopCounter++;
        if (hopCounter == HOPS_PER_CYCLE) {
            hopCounter = 0;
            hopCooldownTicks = BIG_HOP_COOLDOWN_TICKS;
            yVelocityFixedPoint = BIG_HOP_Y_VELOCITY_FIXED_POINT;
            return;
        }
        yVelocityFixedPoint = LITTLE_HOP_Y_VELOCITY_FIXED_POINT;
    }

    private void moveHorizontally() {
        final double proposedX = pixelX + xVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
        if (isWallAhead(proposedX)) {
            // Object_FlipFace: flip only, no velocity negate - the next frame rederives XVel from facing.
            // The handler defers this to its last instruction; doing it here, where the move is blocked,
            // is the Goomba's arrangement and differs in nothing observable.
            facingRight = !facingRight;
            return;
        }
        pixelX = proposedX;
    }

    private void moveVertically() {
        groundContact = false;
        ceilingContact = false;
        final double proposedY = pixelY + yVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;

        if (yVelocityFixedPoint >= 0 && isGroundBelow(proposedY)) {
            // Object_HitGround: align the feet to the tile top it landed on.
            final int feetRow = (int) floor((proposedY + SPRITE_SIZE_PIXELS) / TILE_SPRITE_SIZE);
            pixelY = (double) feetRow * TILE_SPRITE_SIZE - SPRITE_SIZE_PIXELS;
            yVelocityFixedPoint = 0;
            grounded = true;
            groundContact = true;
            return;
        }
        if (yVelocityFixedPoint < 0 && isCeilingAbove(proposedY)) {
            final int headRow = (int) floor(proposedY / TILE_SPRITE_SIZE);
            pixelY = (double) (headRow + 1) * TILE_SPRITE_SIZE;
            yVelocityFixedPoint = 0;
            grounded = false;
            ceilingContact = true;
            return;
        }
        pixelY = proposedY;
        grounded = false;
    }

    /**
     * {@code Object_Move}'s {@code OBJECT_FALLRATE} capped at {@code OBJECT_MAXFALL}, then the handler's
     * {@code DEC Objects_YVel}. Net {@code +$02} a frame, and a terminal fall of {@code $3F} rather than
     * the Goomba's {@code $40}, because the decrement lands after the cap.
     */
    private void applyFloatingGravity() {
        yVelocityFixedPoint = min(yVelocityFixedPoint + GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT)
            - WING_LIFT_FIXED_POINT;
    }

    private void positionSprite() {
        final WorldOffset worldOffset = pixelToWorldOffset(pixelX, pixelY);
        if (worldOffset == null) {
            return;
        }

        // Always upright and always winged: this class only ever draws the live, flying object. The
        // inverted SPR_VFLIP corpse belongs to the replacement Goomba, which both defeat responses hand
        // the slot to before this Para-Goomba stops being drawn at all.
        worldOffset.applyTo(spriteGeometry);
        positionWings(worldOffset);
    }

    /**
     * Places the two wing quads relative to the body: 10 px up, and 2 px out past each of the body's two
     * 8x16 halves. World Y grows upward where sprite pixels grow downward, hence the addition.
     */
    private void positionWings(final WorldOffset bodyWorldOffset) {
        if (leftWingGeometry == null || rightWingGeometry == null) {
            return;
        }
        final float wingWorldY = bodyWorldOffset.y() + (float) WING_LIFT_PIXELS / TILE_SPRITE_SIZE;
        final float leftWingWorldX = bodyWorldOffset.x() - (float) WING_OUTWARD_OFFSET_PIXELS / TILE_SPRITE_SIZE;
        final float rightWingWorldX = bodyWorldOffset.x()
            + (float) (BODY_RIGHT_SPRITE_OFFSET_PIXELS + WING_OUTWARD_OFFSET_PIXELS) / TILE_SPRITE_SIZE;
        leftWingGeometry.setLocalTranslation(leftWingWorldX, wingWorldY, Z_DEPTH_ENEMY_WING);
        rightWingGeometry.setLocalTranslation(rightWingWorldX, wingWorldY, Z_DEPTH_ENEMY_WING);
    }

    private void detachWings() {
        if (leftWingGeometry != null) {
            gameEngine.getRootNode().detachChild(leftWingGeometry);
            leftWingGeometry = null;
        }
        if (rightWingGeometry != null) {
            gameEngine.getRootNode().detachChild(rightWingGeometry);
            rightWingGeometry = null;
        }
    }

    private boolean isCeilingAbove(final double proposedY) {
        final int centerColumn = (int) floor((pixelX + SPRITE_SIZE_PIXELS / 2.0) / TILE_SPRITE_SIZE);
        final int headRow = (int) floor(proposedY / TILE_SPRITE_SIZE);
        return gameEngine.getCollisionGrid().isWallOrCeilingSolidTile(centerColumn, headRow);
    }
}
