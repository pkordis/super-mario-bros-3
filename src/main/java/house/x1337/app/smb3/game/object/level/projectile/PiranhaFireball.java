package house.x1337.app.smb3.game.object.level.projectile;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_PROJECTILE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PIRANHA_FIREBALL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;
import static java.lang.Math.floor;

@Data
@Prototype
@RequiredArgsConstructor
public final class PiranhaFireball implements ActiveLevelObject {
    public static final int ANIMATION_COUNTER_MASK = 255;
    public static final int FRAME_MASK = 3;
    public static final int FRAME_SHIFT = 2;
    public static final int FIRST_ROTATED_FRAME = 2;
    public static final int HIT_WIDTH_PIXELS = 16;
    public static final int HIT_X_OFFSET_PIXELS = 6;
    public static final int HIT_Y_OFFSET_PIXELS = 8;
    public static final int LARGE_PLAYER_HIT_HEIGHT_PIXELS = 22;
    public static final int LARGE_PLAYER_Y_OFFSET_PIXELS = 10;
    public static final int OFF_SCREEN_RIGHT_PIXELS = 248;
    public static final int SAME_SCREEN_HEIGHT_PIXELS = 256;
    public static final int SMALL_PLAYER_HIT_HEIGHT_PIXELS = 16;
    public static final int SMALL_PLAYER_Y_OFFSET_PIXELS = 18;
    private static final int SPRITE_HEIGHT_PIXELS = 16;
    private static final int SPRITE_WIDTH_PIXELS = 8;
    private static final Dimensions SPRITE_DIMENSIONS = halfTileWidth("PiranhaFireball");
    private static final DimensionsPixels SPRITE_PIXELS = new DimensionsPixels(
        SPRITE_WIDTH_PIXELS,
        SPRITE_HEIGHT_PIXELS
    );

    private final PiranhaFireballAnimator animator = getBean(PiranhaFireballAnimator.class);
    private final LevelObjectType type = PIRANHA_FIREBALL;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final GameEngine gameEngine;

    private int slot;
    private FixedPointVelocity velocity;
    private Geometry spriteGeometry;
    private PixelPosition position;
    private boolean expired;
    private int animationCounter;

    public void spawnIntoScene(
        final int slot,
        final PixelPosition topLeft,
        final FixedPointVelocity velocity
    ) {
        this.slot = slot;
        this.position = topLeft;
        this.velocity = velocity;
        this.spriteGeometry = animator.buildSprite(this);
        gameEngine.getRootNode().attachChild(spriteGeometry);
        positionSprite();
    }

    public void advance() {
        animationCounter = (animationCounter + 1) & ANIMATION_COUNTER_MASK;
        position = position.stepped(velocity);
    }

    public int frameIndex() {
        return (animationCounter >> FRAME_SHIFT) & FRAME_MASK;
    }

    public int patternIndex() {
        return frameIndex() & 1;
    }

    /** @return {@code true} on frames 2 and 3, which are drawn rotated 180° */
    public boolean isRotated() {
        return frameIndex() >= FIRST_ROTATED_FRAME;
    }

    /** @return the H-flip: the frame's own, XORed with whether the fireball moves left */
    public boolean isFlippedHorizontally() {
        return isRotated() ^ velocity.movingLeft();
    }

    public boolean isFlippedVertically() {
        return isRotated();
    }

    public boolean overlaps(final LevelScenePlayer player) {
        final boolean compact = player.isSmall() || player.getRuntimeState().isDucking();
        final int yOffset = compact ? SMALL_PLAYER_Y_OFFSET_PIXELS : LARGE_PLAYER_Y_OFFSET_PIXELS;
        final int height = compact ? SMALL_PLAYER_HIT_HEIGHT_PIXELS : LARGE_PLAYER_HIT_HEIGHT_PIXELS;
        final int dy = pixelY() + HIT_Y_OFFSET_PIXELS - (int) floor(player.getPosition().getY()) - yOffset;
        final int dx = pixelX() + HIT_X_OFFSET_PIXELS - (int) floor(player.getPosition().getX());
        return dy >= 0 && dy < height && dx >= 0 && dx < HIT_WIDTH_PIXELS;
    }

    /**
     * The hook for star power and a ducking Hammer-suit player, both of which turn the fireball into a poof
     * with the kick sound instead of being hurt.
     *
     * @param player the player it touches
     * @return {@code true} if the player destroys the fireball rather than being hurt by it
     */
    public boolean isDestroyedBy(final LevelScenePlayer player) {
        return false;
    }

    public boolean isOffScreen(final AxisAlignedBoundingBox screen) {
        final int dx = pixelX() - (int) floor(screen.left());
        final int dy = pixelY() + SPRITE_HEIGHT_PIXELS - (int) floor(screen.top());
        return dx < 0 || dx >= OFF_SCREEN_RIGHT_PIXELS || dy < 0 || dy >= SAME_SCREEN_HEIGHT_PIXELS;
    }

    /**
     * Draws the 8x16 sprite at the whole-pixel position, one pixel low as every sprite is. A flip is a
     * negative scale about the quad's bottom-left corner, so the translation moves by the quad's size.
     */
    public void positionSprite() {
        if (spriteGeometry == null) {
            return;
        }
        animator.applyCurrentFrame(this);
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final boolean flippedHorizontally = isFlippedHorizontally();
        final boolean flippedVertically = isFlippedVertically();
        final float worldX = (float) pixelX() / TILE_SPRITE_SIZE;
        final float spriteBottom = (float) (pixelY() + SPRITE_HEIGHT_PIXELS) / TILE_SPRITE_SIZE;
        final float worldY = rows - spriteBottom - 1f / TILE_SPRITE_SIZE;
        spriteGeometry.setLocalScale(flippedHorizontally ? -1f : 1f, flippedVertically ? -1f : 1f, 1f);
        spriteGeometry.setLocalTranslation(
            flippedHorizontally ? worldX + spriteDimensions.width() : worldX,
            flippedVertically ? worldY + spriteDimensions.height() : worldY,
            Z_DEPTH_PROJECTILE
        );
    }

    @Override
    public AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(position, SPRITE_PIXELS);
    }

    @Override
    public Coordinates getOffset() {
        return of(position.column(), position.row());
    }

    @Override
    public void onCollisionWith(final LevelScenePlayer player) {
        // Never in the broad-phase: PiranhaFireballMotionManager runs the player test.
    }

    private int pixelX() {
        return (int) floor(position.x());
    }

    private int pixelY() {
        return (int) floor(position.y());
    }
}
