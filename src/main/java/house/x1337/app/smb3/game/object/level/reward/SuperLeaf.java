package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SUPER_LEAF;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;

@Getter
@Prototype
@RequiredArgsConstructor
public final class SuperLeaf implements RewardLevelObject {
    private static final int INITIAL_Y_VELOCITY_FIXED_POINT = -32;
    private static final int SPAWN_Y_OFFSET_PIXELS = -14;
    private static final int COLLECT_PROTECTION_FRAMES = 16;
    // Collection hitbox, inset from the sprite per dasm Object_BoundBox entry 1 (OAT_BOUNDBOX01,
    // used by OBJ_POWERUP_SUPERLEAF): left +1, width 13 — ObjectObject_Intersect reads byte[1] as a
    // width added to the left edge, so the right edge sits 2px in from the 16px sprite.
    private static final int HITBOX_LEFT_INSET = 1;
    private static final int HITBOX_WIDTH = 13;
    private static final int X_VELOCITY_STEP_FIXED_POINT = 2;
    private static final int X_VELOCITY_LIMIT_FIXED_POINT = 32;
    private static final int[] FLUTTER_Y_VELOCITY_BASE_FIXED_POINT = {10, -10, 8};
    private static final int FLUTTER_Y_VELOCITY_BIAS_FIXED_POINT = 6;
    private static final double FIXED_POINT_VELOCITY_TO_PIXELS = 1.0 / 16.0;

    private final LevelObjectType type = SUPER_LEAF;

    /** Points awarded (and captioned) when the leaf is collected — 1000, as in the ROM. */
    private final Reward rewardType = SCORE_1000;

    @Value("classpath:/sprites/reward/leaf/leaf_normal.png")
    private ImageResource imageResource;

    private final GameEngine gameEngine;
    private final Offset offset;

    private Dimensions spriteDimensions;
    private Geometry spriteGeometry;

    private boolean expired;
    private boolean collected;
    private boolean facingRight;
    private boolean rising = true;
    private double pixelX;
    private double pixelY;
    private int oscillationDirectionCounter;
    private int xVelocityFixedPoint;
    private int yVelocityFixedPoint;
    private int collectProtectionTimer;

    @PostConstruct
    void init() {
        pixelX = (double) offset.x() * TILE_SPRITE_SIZE;
        pixelY = (double) offset.y() * TILE_SPRITE_SIZE + SPAWN_Y_OFFSET_PIXELS;
        yVelocityFixedPoint = INITIAL_Y_VELOCITY_FIXED_POINT;
        collectProtectionTimer = COLLECT_PROTECTION_FRAMES;

        spriteDimensions = new Dimensions(
            "SuperLeaf",
            imageResource.getDimensions().width() * PIXELS_TO_GAME_UNITS,
            imageResource.getDimensions().height() * PIXELS_TO_GAME_UNITS
        );
        spriteGeometry = fromTexture(gameEngine.getAssetManager(), imageResource.asTexture(), spriteDimensions);
        spriteGeometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        positionSprite();
    }

    public void motionUpdate() {
        if (expired) {
            return;
        }

        if (collectProtectionTimer > 0) {
            collectProtectionTimer--;
        }

        if (rising) {
            pixelY += yVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
            yVelocityFixedPoint++;
            if (yVelocityFixedPoint != 0) {
                updateFacingAndPosition();
                return;
            }
            // Rise complete: fall through into the flutter phase on this same frame
            // (dasm PRG001_ABE7 clears the timer and continues at PRG001_ABEC).
            rising = false;
        }

        final int direction = oscillationDirectionCounter & 1;
        xVelocityFixedPoint += (direction == 0) ? X_VELOCITY_STEP_FIXED_POINT : -X_VELOCITY_STEP_FIXED_POINT;
        if (xVelocityFixedPoint == ((direction == 0) ? X_VELOCITY_LIMIT_FIXED_POINT : -X_VELOCITY_LIMIT_FIXED_POINT)) {
            oscillationDirectionCounter++;
        }

        int bobIndex = direction;
        if (xVelocityFixedPoint < 0) {
            bobIndex++;
        }
        yVelocityFixedPoint = FLUTTER_Y_VELOCITY_BASE_FIXED_POINT[bobIndex] + FLUTTER_Y_VELOCITY_BIAS_FIXED_POINT;

        pixelX += xVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
        pixelY += yVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;

        updateFacingAndPosition();

        if (hasFallenOffLevel()) {
            expired = true;
        }
    }

    /**
     * Collects the leaf: awards {@link #rewardType} to the collecting player, marks the leaf
     * collected, and — for a small player — starts the small→Super grow transition via
     * {@link LevelScenePlayer#onRewardConsumption}. The leaf itself vanishes on contact
     * ({@link #detachesOnCollect()}), the same tick it is collected and before any grow freeze
     * begins, exactly like the mushroom. (The ROM's {@code ObjHit_SuperLeaf} also grants the
     * Raccoon suit; that is still deferred.)
     *
     * @param levelScenePlayer the player that collected the leaf
     */
    @Override
    public void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        if (collected) {
            // Already collected this tick (a second player) — award and caption exactly once.
            return;
        }
        levelScenePlayer
            .getPlayerData()
            .addPoints(rewardType.getData().getPoints());
        collected = true;
        levelScenePlayer.onRewardConsumption(this);
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    /**
     * @return {@code true} once the spawn collect-protection window has elapsed. While the leaf is
     *         still emerging it cannot be picked up (dasm {@code ObjInit_SuperLeaf} sets
     *         {@code Objects_Timer2 = $10}; {@code Player_HitEnemy} skips the hit response while it is
     *         nonzero), so its manager keeps it out of the collision broadphase until then. Without
     *         this, a leaf dispensed while the player is flush against the block is collected on its
     *         first frame — the original requires the player to move or jump to reach it.
     */
    @Override
    public boolean isCollectable() {
        return collectProtectionTimer == 0;
    }

    /**
     * The leaf's collection hitbox is inset from its sprite, per dasm {@code Object_BoundBox} entry 1
     * ({@code OAT_BOUNDBOX01}, used by {@code OBJ_POWERUP_SUPERLEAF}): left {@code +1}, width 13, so
     * the box spans {@code [pixelX+1, pixelX+14]} — the right edge sits 2px in from the 16px sprite.
     * The default full-sprite box let a leaf swinging back over its spawn column be grabbed by a
     * player standing flush against the dispensing block; this inset restores the clearance the
     * original relies on (the ROM's box is {@code Object_CalcBoundBox} / {@code ObjectObject_Intersect}).
     * The vertical extent is left at the sprite height — the reported issue and the ROM's inset are
     * horizontal, and the engine's leaf art (16×14) is already cropped vertically.
     */
    @Override
    public AxisAlignedBoundingBox getBounds() {
        final double left = pixelX + HITBOX_LEFT_INSET;
        return new AxisAlignedBoundingBox(
            left,
            pixelY,
            left + HITBOX_WIDTH,
            pixelY + imageResource.getDimensions().height()
        );
    }

    @Override
    public void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        // The leaf reward is collected by contact and does not respond to the tail attack.
    }

    /**
     * The leaf disappears on contact, the same tick it is collected — before the
     * grow freeze a small player triggers next tick — leaving only its rising
     * "1000" caption during the transition, mirroring the mushroom (dasm
     * {@code ObjHit_SuperLeaf}, as {@code ObjHit_PUpMush} does for the mushroom).
     */
    @Override
    public boolean detachesOnCollect() {
        return true;
    }

    private void updateFacingAndPosition() {
        if (xVelocityFixedPoint > 0) {
            facingRight = true;
        } else if (xVelocityFixedPoint < 0) {
            facingRight = false;
        }
        positionSprite();
    }

    private void positionSprite() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final float worldX = (float) (pixelX / TILE_SPRITE_SIZE);
        final float worldY = (rows - 1) - (float) (pixelY / TILE_SPRITE_SIZE);
        final float width = spriteDimensions.width();

        if (facingRight) {
            // Mirror the left-pointing art to point right — same negative-X-scale
            // flip (plus a width shift to keep it in place) used by player sprites.
            spriteGeometry.setLocalScale(-1f, 1f, 1f);
            spriteGeometry.setLocalTranslation(worldX + width, worldY, Z_DEPTH_ITEM_REWARD);
        } else {
            spriteGeometry.setLocalScale(1f, 1f, 1f);
            spriteGeometry.setLocalTranslation(worldX, worldY, Z_DEPTH_ITEM_REWARD);
        }
    }

    private boolean hasFallenOffLevel() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        return pixelY / TILE_SPRITE_SIZE > rows + 1;
    }
}
