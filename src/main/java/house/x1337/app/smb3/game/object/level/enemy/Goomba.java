package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.GoombaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
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

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.LevelSceneLayerCapabilities.NON_PLAYABLE_CHARACTERS;
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
    private boolean spawnedIntoScene;
    private boolean facingRight;
    private boolean grounded;
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
        setExpired(true);
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
        spriteGeometry.setLocalTranslation(worldX, worldY, Z_DEPTH_ENEMY);
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
