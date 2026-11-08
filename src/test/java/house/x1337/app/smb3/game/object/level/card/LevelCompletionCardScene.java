package house.x1337.app.smb3.game.object.level.card;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.enumeration.resource.LevelCompletionCardImageResource;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;

import static com.jme3.texture.Image.Format.RGBA8;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a card needs to be placed in a mocked engine: a scene size, an asset manager, a root node and a texture per
 * face. The textures are blank stand-ins, one per face, so a test can tell which one the sprite shows.
 */
final class LevelCompletionCardScene {
    static final int COLUMNS = 64;
    static final int ROWS = 12;

    private LevelCompletionCardScene() {
    }

    /**
     * Stubs the parts of {@code gameEngine} that {@link LevelCompletionCard#spawnIntoScene(Coordinates)} reads.
     *
     * @param gameEngine the mocked engine
     * @param levelScene the mocked scene the engine returns
     * @return the root node the card's sprite is attached to
     */
    static Node prepare(final GameEngine gameEngine, final LevelScene levelScene) {
        final Node rootNode = new Node("root");
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(rootNode);
        return rootNode;
    }

    /** @return a stand-in for the injected card images, with a texture per face */
    @SuppressWarnings("unchecked")
    static EnumeratedImageResource<LevelCompletionCardImageResource> cardImages() {
        final EnumeratedImageResource<LevelCompletionCardImageResource> cardImages =
            mock(EnumeratedImageResource.class);
        for (final LevelCompletionCardImageResource face : LevelCompletionCardImageResource.values()) {
            final Texture2D texture = blankTexture();
            texture.setName(face.name());
            when(cardImages.getTextureFor(face)).thenReturn(texture);
        }
        return cardImages;
    }

    /**
     * Builds a card as the spawner does and places it on {@code cell}.
     *
     * @param gameEngine the engine, prepared with {@link #prepare}
     * @param cell       the card's cell
     * @return the placed card
     */
    static LevelCompletionCard place(final GameEngine gameEngine, final Coordinates cell) {
        final LevelCompletionCard card = new LevelCompletionCard(gameEngine);
        card.setCardImages(cardImages());
        card.spawnIntoScene(cell);
        return card;
    }

    private static Texture2D blankTexture() {
        final int bytes = TILE_SPRITE_SIZE * TILE_SPRITE_SIZE * 4;
        return new Texture2D(new Image(
            RGBA8,
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE,
            BufferUtils.createByteBuffer(bytes),
            ColorSpace.Linear
        ));
    }
}
