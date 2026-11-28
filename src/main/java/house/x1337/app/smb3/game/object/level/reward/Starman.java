package house.x1337.app.smb3.game.object.level.reward;

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.SlopeFollowingCapable;
import house.x1337.app.smb3.game.object.level.reward.animation.StarmanAnimator;
import house.x1337.app.smb3.game.object.level.reward.core.PowerUpCollectProtection;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import java.util.OptionalDouble;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.STARMAN;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_1000;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static java.lang.Math.clamp;

/**
 * The star: it comes out of a block like a mushroom, hops out of it and then bounces around the static environment
 * until it is collected or leaves the level, flipping between its two sprites. Collecting it makes the player
 * invincible for a while. See kb/star.md, "The star item".
 */
@Data
@Prototype
@RequiredArgsConstructor
public final class Starman implements RewardLevelObject, SlopeFollowingCapable {
    private static final int RISE_TIMER_INITIAL = 61;
    private static final int RISE_MOVEMENT_THRESHOLD = 45;
    private static final int RISE_PIXEL_INTERVAL_FRAMES = 3;
    private static final int DROP_SPAWN_Y_OFFSET_PIXELS = 8;
    private static final int DROP_Y_VELOCITY_FIXED_POINT = 16;
    private static final int SPAWN_Y_OFFSET_PIXELS = -1;
    private static final int X_MOVE_SPEED_FIXED_POINT = 16;
    private static final int GRAVITY_FIXED_POINT = 3;
    private static final int MAX_FALL_FIXED_POINT = 64;
    private static final int LAUNCH_Y_VELOCITY = -56;
    private static final int BOUNCE_Y_VELOCITY = -64;
    private static final int CEILING_REBOUND_Y_VELOCITY = 4;
    private static final int SPRITE_SIZE_PIXELS = TILE_SPRITE_SIZE;

    private final LevelObjectType type = STARMAN;
    private final Reward rewardType = SCORE_1000;

    /** Injected by the bean factory; package-private setters so a test can supply the art without one. */
    @Setter(AccessLevel.PACKAGE)
    @Value("classpath:/sprites/reward/starman/star_normal.png")
    private ImageResource imageResource;

    @Setter(AccessLevel.PACKAGE)
    @Value("classpath:/sprites/reward/starman/star_red.png")
    private ImageResource redImageResource;

    private final PowerUpCollectProtection collectProtection = new PowerUpCollectProtection();
    private final GameEngine gameEngine;
    private final Coordinates offset;

    private StarmanAnimator animator;
    private Dimensions spriteDimensions;
    private Geometry spriteGeometry;

    @Setter
    private boolean expired;
    private boolean collected;
    private boolean rising = true;
    private boolean grounded;
    private boolean movingRight;
    private int riseTimer;
    private int riseSubCounter;
    private PixelPosition position;
    private FixedPointVelocity velocity = STILL;

    @PostConstruct
    void init() {
        // One pixel above the block cell's origin: the 15 px creep then ends exactly on the block's top edge.
        position = PixelPosition.ofTile(offset).plusY(SPAWN_Y_OFFSET_PIXELS);
        riseTimer = RISE_TIMER_INITIAL;
        // The star moves AWAY from the player.
        movingRight = position.x() >= closestPlayerCenterX() - SPRITE_SIZE_PIXELS / 2.0;

        spriteDimensions = new Dimensions(
            "Starman",
            imageResource.getDimensions().width() * PIXELS_TO_GAME_UNITS,
            imageResource.getDimensions().height() * PIXELS_TO_GAME_UNITS
        );
        spriteGeometry = fromTexture(gameEngine.getAssetManager(), imageResource.asTexture(), spriteDimensions);
        spriteGeometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        animator = new StarmanAnimator(imageResource.asTexture(), redImageResource.asTexture());
        animator.show(spriteGeometry);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        clipToEmergedPortion();
        positionSprite();
    }

    /** Starts the star out of the bottom of its block instead of the top: it falls out and bounces away. */
    public void dropOutOfTheBlockBelow() {
        rising = false;
        riseTimer = 0;
        position = PixelPosition.ofTile(offset).plusY(DROP_SPAWN_Y_OFFSET_PIXELS);
        velocity = velocity
            .directedX(movingRight, X_MOVE_SPEED_FIXED_POINT)
            .withY(DROP_Y_VELOCITY_FIXED_POINT);
        restoreFullSprite();
        positionSprite();
    }

    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }

        collectProtection.tick();

        if (rising) {
            tickRise();
            return;
        }

        tickMoving();
    }

    private void tickRise() {
        riseTimer--;
        if (riseTimer <= 0) {
            rising = false;
            velocity = velocity
                .directedX(movingRight, X_MOVE_SPEED_FIXED_POINT)
                .withY(LAUNCH_Y_VELOCITY);
            restoreFullSprite();
            positionSprite();
            return;
        }

        if (!isHoldingInTheBlock()) {
            if (riseSubCounter == 0) {
                position = position.plusY(-1);
                riseSubCounter = RISE_PIXEL_INTERVAL_FRAMES - 1;
            } else {
                riseSubCounter--;
            }
        }

        clipToEmergedPortion();
        positionSprite();
    }

    private boolean isHoldingInTheBlock() {
        return rising && riseTimer >= RISE_MOVEMENT_THRESHOLD;
    }

    @Override
    public boolean isExemptFromDeletionOffScreen() {
        return isHoldingInTheBlock();
    }

    private void tickMoving() {
        velocity = velocity.falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);

        moveHorizontally();
        moveVertically();

        animator.update(spriteGeometry);
        positionSprite();
    }

    private void moveHorizontally() {
        final PixelPosition proposed = position.steppedX(velocity);
        if (!velocity.stationaryX() && isWallAhead(proposed)) {
            velocity = velocity.negatedX();
        } else {
            position = proposed;
        }
    }

    private void moveVertically() {
        final PixelPosition proposed = position.steppedY(velocity);
        final OptionalDouble landingY = velocity.descending() ? findLandingY(proposed) : OptionalDouble.empty();
        if (landingY.isPresent()) {
            position = position.withY(landingY.getAsDouble());
            velocity = velocity.withY(BOUNCE_Y_VELOCITY);
            grounded = true;
        } else if (velocity.rising() && isCeilingAbove(proposed)) {
            velocity = velocity.withY(CEILING_REBOUND_Y_VELOCITY);
            grounded = false;
        } else {
            position = proposed;
            grounded = false;
        }
    }

    private boolean isCeilingAbove(final PixelPosition proposed) {
        final int centerColumn = position.plusX(SPRITE_SIZE_PIXELS / 2.0).column();
        return getCollisionGrid().isWallOrCeilingSolidTile(Coordinates.of(centerColumn, proposed.row()));
    }

    @Override
    public void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        if (collected) {
            return;
        }
        levelScenePlayer
            .getPlayerData()
            .add(rewardType.getData());
        collected = true;
        levelScenePlayer.startStarInvincibility();
    }

    /** Collectable once the 16 protection frames have run and it is out of the block. */
    @Override
    public boolean isCollectable() {
        return collectProtection.isElapsed() && !isHoldingInTheBlock();
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean detachesOnCollect() {
        return true;
    }

    private void positionSprite() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final float worldX = (float) (position.x() / TILE_SPRITE_SIZE);
        final double drawY = rising ? position.y() : position.y() + slopeDrawOffsetPixels(position);
        float worldY = (rows - 1) - (float) (drawY / TILE_SPRITE_SIZE);
        if (!rising) {
            // One logical pixel down, so its resting feet do not rasterise a pixel above the surface
            worldY -= 1f / TILE_SPRITE_SIZE;
        }
        spriteGeometry.setLocalTranslation(worldX, worldY, Z_DEPTH_ITEM_REWARD);
    }

    /** Clips the sprite to the slice above the block's top edge, so the block appears to contain the rest. */
    private void clipToEmergedPortion() {
        final float spriteWidth = spriteDimensions.width();
        final float spriteHeight = spriteDimensions.height();
        final float emerged = isHoldingInTheBlock()
            ? 0f
            : (float) clamp(offset.y() - position.y() / TILE_SPRITE_SIZE, 0.0, spriteHeight);
        final float localBottom = spriteHeight - emerged;
        final float texCoordBottom = localBottom / spriteHeight;

        final Mesh mesh = spriteGeometry.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
            0f, localBottom, 0f,
            spriteWidth, localBottom, 0f,
            spriteWidth, spriteHeight, 0f,
            0f, spriteHeight, 0f
        });
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {
            0f, texCoordBottom,
            1f, texCoordBottom,
            1f, 1f,
            0f, 1f
        });
        mesh.updateBound();
    }

    private void restoreFullSprite() {
        final float spriteWidth = spriteDimensions.width();
        final float spriteHeight = spriteDimensions.height();

        final Mesh mesh = spriteGeometry.getMesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
            0f, 0f, 0f,
            spriteWidth, 0f, 0f,
            spriteWidth, spriteHeight, 0f,
            0f, spriteHeight, 0f
        });
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        });
        mesh.updateBound();
    }

    private double closestPlayerCenterX() {
        return gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + SPRITE_SIZE_PIXELS / 2.0)
            .orElse(position.x() + SPRITE_SIZE_PIXELS / 2.0);
    }
}
