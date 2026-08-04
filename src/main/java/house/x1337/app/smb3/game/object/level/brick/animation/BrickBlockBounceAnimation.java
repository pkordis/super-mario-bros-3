package house.x1337.app.smb3.game.object.level.brick.animation;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.brick.animation.management.BrickBlockAnimator;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;

import java.nio.ByteBuffer;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

/**
 * Animates a brick block bouncing in place when hit from below by small Mario.
 *
 * <h2>Physics — ported from dasm {@code prg001.asm ObjNorm_BounceDU}</h2>
 *
 * <p>When small Mario hits a brick from below, the brick does not break.
 * Instead, the brick visually shifts on the Y-axis using a velocity table,
 * bouncing upward then back down over 10 frames.
 *
 * <h3>Velocity table — {@code Bouncer_PUpVel}</h3>
 * <pre>{@code
 * .byte $00, -$40, -$40, -$30, -$20, -$10, $00, $10, $20, $30, $40
 * }</pre>
 * <p>The position counter {@code Level_BlkBump_Pos} starts at 10 and decrements each frame.
 * The velocity value at index {@code Level_BlkBump_Pos} is used.
 * Negative = upward movement in NES coordinates (screen Y increases downward).
 * In jme3 (Y increases upward), we negate these values.
 *
 * <p>These values are 8-bit signed in 4.4 fixed-point format (16ths of a pixel):
 * <ul>
 *   <li>{@code -$40} = −64/16 = −4 pixels/frame (upward)</li>
 *   <li>{@code -$30} = −48/16 = −3 pixels/frame</li>
 *   <li>{@code -$20} = −32/16 = −2 pixels/frame</li>
 *   <li>{@code -$10} = −16/16 = −1 pixel/frame</li>
 *   <li>{@code $00}  = 0 (apex)</li>
 *   <li>{@code $10}  = +16/16 = +1 pixel/frame (downward)</li>
 *   <li>{@code $20}  = +32/16 = +2 pixels/frame</li>
 *   <li>{@code $30}  = +48/16 = +3 pixels/frame</li>
 *   <li>{@code $40}  = +64/16 = +4 pixels/frame</li>
 * </ul>
 *
 * <p>The sprite uses {@code Object_ApplyYVel} to update position based on velocity,
 * and after 10 frames, the tile is restored and the animation completes.
 *
 * <h3>Sound — {@code SND_PLAYERBUMP}</h3>
 * <p>A "bump" sound is queued when the block is hit.
 */
@Getter
public final class BrickBlockBounceAnimation implements GameRenderer {

    /**
     * Velocity table from dasm {@code Bouncer_PUpVel}.
     * Index 10 = position counter start; index 0 = animation end.
     * Values are in NES 4.4 fixed-point (upper nibble = integer pixels per frame).
     * <p>
     * Original NES table:
     * <pre>
     * Bouncer_PUpVel: .byte $00, -$40, -$40, -$30, -$20, -$10, $00, $10, $20, $30, $40
     *                       [0]  [1]   [2]   [3]   [4]   [5]  [6]  [7]  [8]  [9] [10]
     * </pre>
     * <p>
     * For upward bounces (hitting from below), NES negates these values.
     * Since jme3 Y-axis is inverted vs NES (jme3 positive = up, NES positive = down),
     * using the original table values directly gives correct jme3 behavior:
     * - pos 10-7: positive = brick moves UP (rising)
     * - pos 6: zero = apex
     * - pos 5-1: negative = brick moves DOWN (falling back)
     */
    private static final int[] BOUNCER_Y_VEL = {
        0x00,   // pos 0  — not used (object destroyed before this frame)
        -0x40,  // pos 1  — falling: -4 px/frame
        -0x40,  // pos 2  — falling: -4 px/frame
        -0x30,  // pos 3  — falling: -3 px/frame
        -0x20,  // pos 4  — falling: -2 px/frame
        -0x10,  // pos 5  — falling: -1 px/frame
        0x00,   // pos 6  — apex: 0
        0x10,   // pos 7  — rising: +1 px/frame
        0x20,   // pos 8  — rising: +2 px/frame
        0x30,   // pos 9  — rising: +3 px/frame
        0x40    // pos 10 — rising: +4 px/frame
    };

    /**
     * Initial bounce position counter (dasm: {@code Level_BlkBump_Pos = 10}).
     */
    private static final int INITIAL_BUMP_POS = 10;

    /**
     * Convert 4.4 fixed-point (16ths of pixel) to game-units (1 tile = 1 unit).
     * {@code value_in_pixels / TILE_SPRITE_SIZE = value_in_game_units}
     * The 4.4 fixed-point divisor is 16, so total divisor = 16 * 16 = 256.
     */
    private static final double FIXED_POINT_TO_GAME_UNITS = 1.0 / (16.0 * TILE_SPRITE_SIZE);

    /**
     * Z-depth for the bouncing sprite — in front of background tiles but behind
     * the player sprite (FOREGROUND layer is at 0.1).
     */
    private static final float BOUNCE_Z = 0.05f;

    private static final Dimensions BOUNCING_BRICK_DIMENSIONS = new Dimensions(
        "BrickBounce",
        TILE_SIZE_GAME_UNITS,
        TILE_SIZE_GAME_UNITS
    );


    private static final String BRICK_SPRITE_ASSET = "sprites/object/brick/plain/frame_0.png";

    // -- Position fields ---------------------------------------------------

    private final Offset offset;

    /**
     * Bottom-left world X of the tile.
     */
    private final float worldX;

    /**
     * Bottom-left world Y of the tile (at rest).
     * jme3 Y increases upward; tile row 0 is the top of the level.
     */
    private final float worldY;

    // -- State -------------------------------------------------------------

    private final Node rootNode;
    private final Geometry spriteGeometry;

    /**
     * Current Y offset from the tile's resting position (in game-units).
     * Positive = upward displacement.
     */
    private double yOffset;

    /**
     * Current bump position counter. Decrements from 10 to 0.
     */
    private int bumpPos;

    /**
     * Flag indicating the animation has completed (bumpPos reached 0).
     */
    private boolean expired;

    // -- Baked texture state (for hiding/restoring the tile) ---------------

    private final Geometry interactiveLayerGeometry;
    private final LevelSceneDimensions dimensions;
    private final BrickBlockAnimator brickBlockAnimator;

    /**
     * Saved RGBA pixels from the baked texture, to be restored on completion.
     * Array size = TILE_SPRITE_SIZE × TILE_SPRITE_SIZE × 4 bytes (RGBA).
     */
    private final byte[] savedPixels;

    /**
     * Current Y velocity (set by previous frame's table lookup).
     * In 4.4 fixed-point format.
     */
    private int currentVelocity;

    // ---------------------------------------------------------------------

    public BrickBlockBounceAnimation(
        final GameEngine gameEngine,
        final Offset offset,
        final BrickBlockAnimator brickBlockAnimator
    ) {
        this.offset = offset;
        this.worldX = offset.x();
        this.worldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y();
        this.rootNode = gameEngine.getRootNode();
        this.bumpPos = INITIAL_BUMP_POS;
        this.yOffset = 0.0;
        this.expired = false;
        this.currentVelocity = 0; // Initial velocity is 0 (matches NES uninitialized state)

        // Store references for texture manipulation
        this.interactiveLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);
        this.dimensions = gameEngine.getLevelScene().getDimensions();
        this.brickBlockAnimator = brickBlockAnimator;

        // Pause shimmer animation for this brick during bounce
        brickBlockAnimator.pauseAt(offset);

        // Save the original tile pixels and erase from baked texture
        this.savedPixels = new byte[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4];
        saveTilePixels();
        eraseTileFromBakedTexture();

        final Texture texture = loadTexture(gameEngine.getAssetManager(), BRICK_SPRITE_ASSET);
        this.spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            texture,
            BOUNCING_BRICK_DIMENSIONS
        );

        positionSprite();
        rootNode.attachChild(spriteGeometry);
    }

    /**
     * Advances the bounce animation by one game-tick.
     *
     * <p>From dasm {@code prg001.asm ObjNorm_BounceDU / PRG001_A5D5}:
     * <ol>
     *   <li>Apply CURRENT Y velocity to position ({@code Object_ApplyYVel})</li>
     *   <li>Update sprite display ({@code BounceBlock_Update})</li>
     *   <li>Look up NEW velocity from {@code Bouncer_PUpVel[Level_BlkBump_Pos]}</li>
     *   <li>Store new velocity for next frame</li>
     *   <li>Decrement {@code Level_BlkBump_Pos}</li>
     * </ol>
     */
    public void tick() {
        if (expired) {
            return;
        }

        // 1. Apply CURRENT velocity (set by previous frame's lookup)
        yOffset += currentVelocity * FIXED_POINT_TO_GAME_UNITS;

        // 2. Update sprite position
        positionSprite();

        // 3. Look up NEW velocity from table for NEXT frame
        currentVelocity = BOUNCER_Y_VEL[bumpPos];

        // 4. Decrement position counter
        bumpPos--;

        // 5. Check for completion (at pos=0, object is destroyed before next tick)
        if (bumpPos <= 0) {
            expired = true;
        }
    }

    public boolean isExpired() {
        return expired;
    }

    /**
     * Detaches the bounce sprite from the scene graph and restores the original
     * tile pixels to the baked texture.
     * Called after the animation completes.
     */
    public void detach() {
        rootNode.detachChild(spriteGeometry);
        restoreTilePixels();
        // Resume shimmer animation for this brick
        brickBlockAnimator.resumeAt(offset);
    }

    /**
     * Saves the RGBA pixels from the baked texture at the tile's location.
     */
    private void saveTilePixels() {
        final ByteBuffer buffer = getBakedTextureBuffer();
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        int saveIdx = 0;
        for (int spriteRow = 0; spriteRow < TILE_SPRITE_SIZE; spriteRow++) {
            final int imgRow = (dimensions.rows() - 1 - offset.y()) * TILE_SPRITE_SIZE
                    + (TILE_SPRITE_SIZE - 1 - spriteRow);
            for (int spriteCol = 0; spriteCol < TILE_SPRITE_SIZE; spriteCol++) {
                final int imgCol = offset.x() * TILE_SPRITE_SIZE + spriteCol;
                final int bufferIdx = (imgRow * imageWidth + imgCol) * 4;
                savedPixels[saveIdx++] = buffer.get(bufferIdx);     // R
                savedPixels[saveIdx++] = buffer.get(bufferIdx + 1); // G
                savedPixels[saveIdx++] = buffer.get(bufferIdx + 2); // B
                savedPixels[saveIdx++] = buffer.get(bufferIdx + 3); // A
            }
        }
    }

    /**
     * Erases the tile from the baked texture (sets all pixels to transparent).
     */
    private void eraseTileFromBakedTexture() {
        final Texture2D texture = getBakedTexture();
        final Image image = texture.getImage();
        final ByteBuffer buffer = image.getData(0);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        for (int spriteRow = 0; spriteRow < TILE_SPRITE_SIZE; spriteRow++) {
            final int imgRow = (dimensions.rows() - 1 - offset.y()) * TILE_SPRITE_SIZE
                    + (TILE_SPRITE_SIZE - 1 - spriteRow);
            for (int spriteCol = 0; spriteCol < TILE_SPRITE_SIZE; spriteCol++) {
                final int imgCol = offset.x() * TILE_SPRITE_SIZE + spriteCol;
                final int bufferIdx = (imgRow * imageWidth + imgCol) * 4;
                buffer.put(bufferIdx, (byte) 0);     // R
                buffer.put(bufferIdx + 1, (byte) 0); // G
                buffer.put(bufferIdx + 2, (byte) 0); // B
                buffer.put(bufferIdx + 3, (byte) 0); // A
            }
        }
        image.setUpdateNeeded();
    }

    /**
     * Restores the saved RGBA pixels back to the baked texture.
     */
    private void restoreTilePixels() {
        final Texture2D texture = getBakedTexture();
        final Image image = texture.getImage();
        final ByteBuffer buffer = image.getData(0);
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;

        int saveIdx = 0;
        for (int spriteRow = 0; spriteRow < TILE_SPRITE_SIZE; spriteRow++) {
            final int imgRow = (dimensions.rows() - 1 - offset.y()) * TILE_SPRITE_SIZE
                    + (TILE_SPRITE_SIZE - 1 - spriteRow);
            for (int spriteCol = 0; spriteCol < TILE_SPRITE_SIZE; spriteCol++) {
                final int imgCol = offset.x() * TILE_SPRITE_SIZE + spriteCol;
                final int bufferIdx = (imgRow * imageWidth + imgCol) * 4;
                buffer.put(bufferIdx, savedPixels[saveIdx++]);     // R
                buffer.put(bufferIdx + 1, savedPixels[saveIdx++]); // G
                buffer.put(bufferIdx + 2, savedPixels[saveIdx++]); // B
                buffer.put(bufferIdx + 3, savedPixels[saveIdx++]); // A
            }
        }
        image.setUpdateNeeded();
    }

    private Texture2D getBakedTexture() {
        return (Texture2D) interactiveLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
    }

    private ByteBuffer getBakedTextureBuffer() {
        return getBakedTexture().getImage().getData(0);
    }

    private void positionSprite() {
        spriteGeometry.setLocalTranslation(
            worldX,
            (float) (worldY + yOffset),
            BOUNCE_Z
        );
    }
}
