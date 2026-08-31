package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;

import java.nio.ByteBuffer;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_BRICK_BLOCK_BOUNCE;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

/**
 * Animates a spent (used) block bouncing in place when hit from below.
 *
 * <h2>Physics — ported from dasm {@code prg001.asm ObjNorm_BounceDU}</h2>
 *
 * <p>The position counter {@code Level_BlkBump_Pos} starts at 10 and decrements
 * each frame. The velocity at that index is applied to the sprite's Y position.
 * After 10 frames the animation completes and the baked texture is restored.
 *
 * <h3>Velocity table — {@code Bouncer_PUpVel}</h3>
 * <pre>{@code
 * .byte $00, -$40, -$40, -$30, -$20, -$10, $00, $10, $20, $30, $40
 * }</pre>
 */
@Getter
public final class EmptyBlockBounceAnimation implements GameRenderer {

    /**
     * Velocity table from dasm {@code Bouncer_PUpVel}.
     * Index 10 = position counter start; index 0 = animation end.
     * Values are in NES 4.4 fixed-point (upper nibble = integer pixels per frame).
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

    /** Initial bounce position counter (dasm: {@code Level_BlkBump_Pos = 10}). */
    private static final int INITIAL_BUMP_POS = 10;

    /**
     * Convert 4.4 fixed-point (16ths of pixel) to game-units (1 tile = 1 unit).
     * Total divisor = 16 (fixed-point) × 16 (TILE_SPRITE_SIZE) = 256.
     */
    private static final double FIXED_POINT_TO_GAME_UNITS = 1.0 / (16.0 * TILE_SPRITE_SIZE);

    // -- Position fields ---------------------------------------------------

    private final Offset offset;
    private final float worldX;
    private final float worldY;

    // -- State -------------------------------------------------------------

    private final Node rootNode;
    private final Geometry spriteGeometry;
    private double yOffset;
    private int bumpPos;
    private boolean expired;

    // -- Baked texture state -----------------------------------------------

    private final Geometry interactiveLayerGeometry;
    private final LevelSceneDimensions dimensions;

    /** Saved RGBA pixels from the baked texture, restored on completion. */
    private final byte[] savedPixels;

    /** Current Y velocity (set by previous frame's table lookup). */
    private int currentVelocity;

    // ----------------------------------------------------------------------

    /**
     * Creates a new bounce animation for an empty block.
     *
     * @param gameEngine the game engine
     * @param offset     the tile offset of the block
     * @param tilePixels ARGB pixel data (16×16) for the empty block tile — used to
     *                   create the flying sprite texture
     */
    public EmptyBlockBounceAnimation(
        final GameEngine gameEngine,
        final Offset offset,
        final ImageResource imageResource
    ) {
        this.offset = offset;
        this.worldX = offset.x();
        this.worldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y();
        this.rootNode = gameEngine.getRootNode();
        this.bumpPos = INITIAL_BUMP_POS;
        this.yOffset = 0.0;
        this.expired = false;
        this.currentVelocity = 0;

        this.interactiveLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);
        this.dimensions = gameEngine.getLevelScene().getDimensions();

        // Save the current baked-texture pixels (the freshly-painted empty block tile)
        // and erase them so only the flying sprite is visible during the bounce.
        this.savedPixels = new byte[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4];
        saveTilePixels();
        eraseTileFromBakedTexture();

        // Create the flying sprite geometry from the tile pixel data
        this.spriteGeometry = createSpriteFromPixels(
            gameEngine.getAssetManager(),
            imageResource.getRgbData()
        );

        positionSprite();
        rootNode.attachChild(spriteGeometry);
    }

    /**
     * Advances the bounce animation by one game-tick.
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

        // 5. Check for completion
        if (bumpPos <= 0) {
            expired = true;
        }
    }

    /**
     * Detaches the bounce sprite from the scene graph and restores the tile pixels
     * to the baked texture.
     */
    public void detach() {
        rootNode.detachChild(spriteGeometry);
        restoreTilePixels();
    }

    /**
     * Creates a jme3 Geometry for the bouncing sprite from raw ARGB pixel data.
     */
    private Geometry createSpriteFromPixels(final AssetManager assetManager, final int[] argbPixels) {
        // Convert ARGB int[] to RGBA ByteBuffer (jme3 format)
        final ByteBuffer buffer = BufferUtils.createByteBuffer(TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4);
        for (int y = 0; y < TILE_SPRITE_SIZE; y++) {
            for (int x = 0; x < TILE_SPRITE_SIZE; x++) {
                final int argb = argbPixels[y * TILE_SPRITE_SIZE + x];
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));         // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();

        final Image image = new Image(
            Image.Format.RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            buffer,
            ColorSpace.sRGB
        );
        final Texture2D texture = new Texture2D(image);
        texture.setMagFilter(com.jme3.texture.Texture.MagFilter.Nearest);
        texture.setMinFilter(com.jme3.texture.Texture.MinFilter.NearestNoMipMaps);

        final Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setDepthTest(false);

        final Quad quad = new Quad(TILE_SIZE_GAME_UNITS, TILE_SIZE_GAME_UNITS);
        final Geometry geometry = new Geometry("EmptyBlockBounce", quad);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Translucent);
        return geometry;
    }

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
                savedPixels[saveIdx++] = buffer.get(bufferIdx);
                savedPixels[saveIdx++] = buffer.get(bufferIdx + 1);
                savedPixels[saveIdx++] = buffer.get(bufferIdx + 2);
                savedPixels[saveIdx++] = buffer.get(bufferIdx + 3);
            }
        }
    }

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
                buffer.put(bufferIdx, (byte) 0);
                buffer.put(bufferIdx + 1, (byte) 0);
                buffer.put(bufferIdx + 2, (byte) 0);
                buffer.put(bufferIdx + 3, (byte) 0);
            }
        }
        image.setUpdateNeeded();
    }

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
                buffer.put(bufferIdx, savedPixels[saveIdx++]);
                buffer.put(bufferIdx + 1, savedPixels[saveIdx++]);
                buffer.put(bufferIdx + 2, savedPixels[saveIdx++]);
                buffer.put(bufferIdx + 3, savedPixels[saveIdx++]);
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
            Z_DEPTH_BRICK_BLOCK_BOUNCE
        );
    }
}
