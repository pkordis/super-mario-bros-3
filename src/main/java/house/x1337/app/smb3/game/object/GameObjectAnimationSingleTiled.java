package house.x1337.app.smb3.game.object;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Data;

import java.nio.ByteBuffer;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;

@Data
public abstract class GameObjectAnimationSingleTiled implements GameRenderer {
    private final LevelSceneDimensions dimensions;
    private final Offset offset;
    private final WorldOffset worldOffset;
    private final Geometry spriteGeometry;
    private final Geometry interactiveLayerGeometry;
    private final byte[] savedPixels = new byte[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4];
    private final Node rootNode;

    public abstract WorldOffset calculateTickedWorldOffset(
        Geometry spriteGeometry,
        WorldOffset currentWorldOffset
    );

    public GameObjectAnimationSingleTiled(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        this.offset = offset;
        this.worldOffset = WorldOffset.of(
            offset.x(),
            gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y(),
            0
        );
        this.rootNode = gameEngine.getRootNode();
        this.interactiveLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);
        this.dimensions = gameEngine.getLevelScene().getDimensions();
        this.spriteGeometry = generateSpriteGeometry(gameEngine);

        onAnimationStart(offset);
        saveTilePixels();
        eraseTileFromBakedTexture();
        updatePosition();
        rootNode.attachChild(spriteGeometry);
    }

    public abstract Geometry generateSpriteGeometry(final GameEngine gameEngine);

    public void onAnimationStart(final Offset offset) {
        // Do nothing by default
    }

    public void onAnimationStop(final Offset offset) {
        // Do nothing by default
    }

    public void saveTilePixels() {
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

    public void detach() {
        rootNode.detachChild(spriteGeometry);
        restoreTilePixels();
        onAnimationStop(offset);
    }

    public void eraseTileFromBakedTexture() {
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

    private ByteBuffer getBakedTextureBuffer() {
        return getBakedTexture().getImage().getData(0);
    }

    private Texture2D getBakedTexture() {
        return (Texture2D) interactiveLayerGeometry
            .getMaterial()
            .getTextureParam("ColorMap")
            .getTextureValue();
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
                buffer.put(bufferIdx, savedPixels[saveIdx++]);     // R
                buffer.put(bufferIdx + 1, savedPixels[saveIdx++]); // G
                buffer.put(bufferIdx + 2, savedPixels[saveIdx++]); // B
                buffer.put(bufferIdx + 3, savedPixels[saveIdx++]); // A
            }
        }
        image.setUpdateNeeded();
    }

    public void updatePosition() {
        final WorldOffset newWorldOffset = calculateTickedWorldOffset(spriteGeometry, worldOffset);
        spriteGeometry.setLocalTranslation(
            newWorldOffset.x(),
            newWorldOffset.y(),
            newWorldOffset.z()
        );
    }
}
