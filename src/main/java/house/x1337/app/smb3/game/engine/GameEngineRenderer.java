package house.x1337.app.smb3.game.engine;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.GameRenderer;

import java.nio.ByteBuffer;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

public interface GameEngineRenderer extends Application, GameRenderer {
    default void renderLevelTiles(final Node cameraTarget) {
        final GameEngine gameEngine = (GameEngine) this;
        final LevelScene levelScene = gameEngine.getLevelScene();
        if (levelScene == null) {
            return;
        }
        final List<LevelScene.LevelSceneLayer> layers = levelScene.getLayersBottomToTop();
        final LevelSceneDimensions dimensions = levelScene.getDimensions();

        positionCameraTarget(levelScene, cameraTarget, dimensions);

        for (final LevelScene.LevelSceneLayer layer : layers) {
            final Geometry layerGeometry = bakeLayerGeometry(layer, dimensions);
            layerGeometry.setLocalTranslation(0, 0, layer.getType().getZ());
            gameEngine.getRootNode().attachChild(layerGeometry);
        }

        final List<? extends GameObjectAnimator<?>> animators = getBean(GameObjectAnimator.Registry.class).getAll();
        final Geometry interactiveObjectlayerGeometry = getLayerGeometry(INTERACTIVE_OBJECTS);
        gameEngine
            .enqueue(() -> animators.forEach(animator ->
                animator.registerLevel(
                    interactiveObjectlayerGeometry,
                    dimensions
                )
            )
        );
    }

    default Geometry getLayerGeometry(final String layerName) {
        final GameEngine gameEngine = (GameEngine) this;
        final Node rootNode = gameEngine.getRootNode();
        final Spatial spatial = rootNode.getChild(layerName);
        if (spatial instanceof Geometry geometry) {
            return geometry;
        }
        throw new IllegalStateException("No geometry found for layer: " + layerName);
    }

    /**
     * Bakes a single layer's tiles into a {@code (columns × TILE_SPRITE_SIZE)} by
     * {@code (rows × TILE_SPRITE_SIZE)} RGBA texture and wraps it in a level-sized {@link Quad}.
     * Empty, virtual or non-renderable cells become fully transparent pixels.
     */
    private Geometry bakeLayerGeometry(
        final LevelScene.LevelSceneLayer layer,
        final LevelSceneDimensions dimensions
    ) {
        final Tile[][] tiles = layer.getTiles();
        // jme3 expects the ByteBuffer in bottom-to-top row order, so imgRow 0 = bottom of image =
        // bottom of the level = tile row (rows - 1), sprite pixel row (TILE_SPRITE_SIZE - 1).
        final int imageWidth = dimensions.columns() * TILE_SPRITE_SIZE;
        final int imageHeight = dimensions.rows() * TILE_SPRITE_SIZE;
        final ByteBuffer buffer = BufferUtils.createByteBuffer(imageWidth * imageHeight * 4);

        for (int imgRow = 0; imgRow < imageHeight; imgRow++) {
            final int tileRow = dimensions.rows() - 1 - (imgRow / TILE_SPRITE_SIZE);
            final int spritePixelRow = TILE_SPRITE_SIZE - 1 - (imgRow % TILE_SPRITE_SIZE);
            for (int imgCol = 0; imgCol < imageWidth; imgCol++) {
                final int tileCol = imgCol / TILE_SPRITE_SIZE;
                final int spritePixelCol = imgCol % TILE_SPRITE_SIZE;
                final Tile tile = tiles[tileRow][tileCol];
                if (tile.isRenderable()) {
                    final int argb = tile.getArgbData()[spritePixelRow * TILE_SPRITE_SIZE + spritePixelCol];
                    buffer.put((byte) ((argb >> 16) & 0xFF));
                    buffer.put((byte) ((argb >> 8) & 0xFF));
                    buffer.put((byte) (argb & 0xFF));
                    buffer.put((byte) ((argb >> 24) & 0xFF));
                } else {
                    // Empty / virtual tile — fully transparent pixel
                    buffer.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
                }
            }
        }
        final Texture2D texture = toTexture(buffer, imageWidth, imageHeight);
        final Material material = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        // Layers overlap in depth; rely on the back-to-front transparent sort for ordering rather
        // than the depth buffer, so overlapping transparent quads blend instead of z-rejecting.
        material.getAdditionalRenderState().setDepthWrite(false);
        material.getAdditionalRenderState().setDepthTest(false);

        final Geometry geometry = new Geometry(layer.getName(), dimensions.toQuad());
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geometry;
    }

    private void positionCameraTarget(
        final LevelScene levelScene,
        final Node cameraTarget,
        final LevelSceneDimensions dimensions
    ) {
        final float startX;
        final float startY;
        if (levelScene.getRenderingStarterColumn() != null && levelScene.getRenderingStarterRow() != null) {
            startX = levelScene.getRenderingStarterColumn();
            startY = (dimensions.rows() - 1) - levelScene.getRenderingStarterRow();
        } else {
            startX = dimensions.columns() / 2.0F;
            startY = dimensions.rows() / 2.0F;
        }
        cameraTarget.setLocalTranslation(startX, startY, 0);
    }
}
