package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.util.GameRenderer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.SUPER_LEAF;

/**
 * A dispensed Super Leaf reward that pops out of a bounced block, rises, and then
 * flutters down while swaying left and right — matching the original game.
 *
 * <h2>Physics — ported from dasm {@code prg001.asm ObjInit_SuperLeaf / ObjNorm_SuperLeaf}</h2>
 *
 * <p>All velocities are the ROM's 4.4 fixed-point values (in 16ths of a sprite pixel per
 * frame). Two phases drive the motion:
 *
 * <ol>
 *   <li><b>Rise</b> — {@code YVel} starts at {@code -$20} (up) and is incremented by 1 each
 *       frame ({@code INC Objects_YVel}); when it reaches 0 the rise ends. This produces the
 *       ~33px pop above the block over 32 frames (dasm {@code PRG001_ABD1} branch).</li>
 *   <li><b>Flutter</b> — {@code XVel} accumulates {@code Leaf_XVelByOsc = ±$02} each frame and
 *       reverses when it hits {@code Leaf_XVelLimit = ±$20}, giving the side-to-side sway. The
 *       vertical bob comes from {@code PRG001_ABD1 = {$0A, -$0A, $08}} plus a {@code +$06} bias,
 *       indexed by the oscillation direction and the sign of {@code XVel}.</li>
 * </ol>
 *
 * <p>The sprite art ({@code leaf_normal.png}) points left; while moving right the ROM sets
 * {@code SPR_HFLIP} (dasm {@code PRG001_AC15}), which this class reproduces with the same
 * negative-X-scale flip the player sprites use.
 *
 * <p>Unlike the ROM's {@code ObjHit_SuperLeaf} (which grants the Raccoon suit), collecting the
 * leaf currently just makes it vanish — see {@link #onCollisionWith(LevelScenePlayer)}.
 */
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

    @Value("classpath:/sprites/reward/leaf/leaf_normal.png")
    private ImageResource leafImage;

    private final GameEngine gameEngine;
    private final Offset offset;

    private Dimensions spriteDimensions;
    private Geometry spriteGeometry;

    private boolean expired;
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
            leafImage.getWidth() * PIXELS_TO_GAME_UNITS,
            leafImage.getHeight() * PIXELS_TO_GAME_UNITS
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

    public void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        expired = true;
    }

    /**
     * @param player the player to test against
     * @return {@code true} if the given player's hitbox overlaps the leaf this frame
     */
    public boolean intersects(final LevelScenePlayer player) {
        final PlayerPosition position = player.getPosition();
        final double playerX = position.getX();
        final double playerY = position.getY();

        // Player hitbox in sprite-pixel space, derived from the collision probe
        // extents in CollisionOffsets (X: 1..14, Y bottom: 32; top: 6 when large
        // and standing, 16 when small or ducking).
        final boolean largeStanding = player.isLarge() && !player.getRuntimeState().isDucking();
        final double playerLeft = playerX + 1;
        final double playerRight = playerX + 15;
        final double playerTop = playerY + (largeStanding ? 6 : 16);
        final double playerBottom = playerY + 32;

        final double leafLeft = pixelX;
        final double leafRight = pixelX + leafImage.getWidth();
        final double leafTop = pixelY;
        final double leafBottom = pixelY + leafImage.getHeight();

        return leafRight > playerLeft
            && leafLeft < playerRight
            && leafBottom > playerTop
            && leafTop < playerBottom;
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
