package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Score;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.util.GameRenderer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SUPER_LEAF;
import static house.x1337.app.smb3.model.game.WorldOffset.of;

@Getter
@Prototype
@RequiredArgsConstructor
public final class SuperLeaf implements ActiveLevelObject, GameRenderer {
    private static final int INITIAL_Y_VELOCITY_FIXED_POINT = -32;
    private static final int SPAWN_Y_OFFSET_PIXELS = -14;
    private static final int X_VELOCITY_STEP_FIXED_POINT = 2;
    private static final int X_VELOCITY_LIMIT_FIXED_POINT = 32;
    private static final int[] FLUTTER_Y_VELOCITY_BASE_FIXED_POINT = {10, -10, 8};
    private static final int FLUTTER_Y_VELOCITY_BIAS_FIXED_POINT = 6;
    private static final double FIXED_POINT_VELOCITY_TO_PIXELS = 1.0 / 16.0;
    private static final float Z_DEPTH = 0.06f;

    private final LevelObjectType type = SUPER_LEAF;

    /** Points awarded (and captioned) when the leaf is collected — 1000, as in the ROM. */
    private final Score rewardScore = Score.SCORE_1000;

    @Value("classpath:/sprites/reward/leaf/leaf_normal.png")
    private ImageResource leafImage;

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

    @PostConstruct
    void init() {
        pixelX = (double) offset.x() * TILE_SPRITE_SIZE;
        pixelY = (double) offset.y() * TILE_SPRITE_SIZE + SPAWN_Y_OFFSET_PIXELS;
        yVelocityFixedPoint = INITIAL_Y_VELOCITY_FIXED_POINT;

        spriteDimensions = new Dimensions(
            "SuperLeaf",
            leafImage.getDimensions().width() * PIXELS_TO_GAME_UNITS,
            leafImage.getDimensions().height() * PIXELS_TO_GAME_UNITS
        );
        spriteGeometry = fromTexture(gameEngine.getAssetManager(), leafImage.asTexture(), spriteDimensions);
        spriteGeometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        positionSprite();
    }

    public void tick() {
        if (expired) {
            return;
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
     * Collects the leaf: awards {@link #rewardScore} to the collecting player and marks the leaf
     * collected. The leaf is not removed immediately — its manager keeps it rendered for exactly
     * one more frame alongside the freshly spawned score caption, matching the ROM where the leaf
     * and the "1000" caption are both visible for a single frame before the leaf vanishes. (The
     * ROM's {@code ObjHit_SuperLeaf} also grants the Raccoon suit; that is still deferred.)
     *
     * @param levelScenePlayer the player that collected the leaf
     */
    public void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        if (collected) {
            // Already collected this tick (a second player) or lingering for its co-render frame —
            // award and caption exactly once.
            return;
        }
        levelScenePlayer
            .getPlayerData()
            .addToScore(rewardScore.getData().getValue());
        collected = true;
    }

    /**
     * @return the leaf's current world position using the same top-edge convention as
     *         {@code PopAnimation}, so the score caption pops from where the leaf was collected
     */
    public WorldOffset getCurrentWorldOffset() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final float worldX = (float) (pixelX / TILE_SPRITE_SIZE);
        final float topEdgeWorldY = (rows - 1) - (float) (pixelY / TILE_SPRITE_SIZE) + spriteDimensions.height();
        return of(worldX, topEdgeWorldY, Z_DEPTH);
    }

    /**
     * @return the leaf's collision box in sprite-pixel space (the raw sprite rectangle at its
     *         current fluttering position)
     */
    @Override
    public AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(pixelX, pixelY, leafImage.getDimensions());
    }

    @Override
    public boolean isCollidable() {
        return false;
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
            spriteGeometry.setLocalTranslation(worldX + width, worldY, Z_DEPTH);
        } else {
            spriteGeometry.setLocalScale(1f, 1f, 1f);
            spriteGeometry.setLocalTranslation(worldX, worldY, Z_DEPTH);
        }
    }

    private boolean hasFallenOffLevel() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        return pixelY / TILE_SPRITE_SIZE > rows + 1;
    }
}
