package house.x1337.app.smb3.game.object.level.effect;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.Animation;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.effect.PoofSequence;
import lombok.Getter;

import static com.jme3.scene.Spatial.CullHint.Always;
import static com.jme3.scene.Spatial.CullHint.Inherit;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

@Getter
@Prototype
public final class PoofAnimation implements Animation {
    private static final Dimensions POOF_DIMENSIONS = new Dimensions(
        "Poof",
        TILE_SIZE_GAME_UNITS,
        TILE_SIZE_GAME_UNITS
    );
    private static final DimensionsPixels POOF_PIXELS = new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE);

    private Geometry spriteGeometry;
    private Material material;
    private Node rootNode;
    private PoofSequence sequence;
    private Texture[][] framesByFlip;
    private WorldOffset worldOffset;
    private int counter;
    private int lastFrameIndex = -1;
    private boolean lastFlipped;
    private boolean expired;

    /**
     * Starts a cloud whose quad's bottom-left corner is {@code worldOffset}, played by {@code sequence}.
     * The one constructor, so the bean factory always resolves it; {@code PoofMotionManager} turns a level
     * cell or a pixel position into the world offset.
     *
     * @param gameEngine      the game engine
     * @param worldOffset     where the cloud's quad goes, at the depth it is drawn
     * @param animationFrames the shared poof frames, in frame-index order
     */
    public void configure(
        final GameEngine gameEngine,
        final WorldOffset worldOffset,
        final AnimationImageResource animationFrames,
        final PoofSequence sequence
    ) {
        this.sequence = sequence;
        this.counter = sequence.startTicks();
        this.rootNode = gameEngine.getRootNode();
        this.worldOffset = worldOffset;
        this.framesByFlip = buildTextures(animationFrames);
        this.spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            framesByFlip[sequence.frameIndexFor(counter)][sequence.isFlippedVerticallyAt(counter) ? 1 : 0],
            POOF_DIMENSIONS
        );
        this.worldOffset.applyTo(spriteGeometry);
        this.material = spriteGeometry.getMaterial();

        applyCurrentFrame();
        rootNode.attachChild(spriteGeometry);
    }

    @Override
    public void tick() {
        if (expired) {
            return;
        }
        if (counter == 0) {
            expired = true;
            return;
        }
        counter--;
        applyCurrentFrame();
    }

    @Override
    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    private void applyCurrentFrame() {
        spriteGeometry.setCullHint(sequence.isDrawnAt(counter) ? Inherit : Always);
        final int frameIndex = sequence.frameIndexFor(counter);
        final boolean flipped = sequence.isFlippedVerticallyAt(counter);
        if (frameIndex == lastFrameIndex && flipped == lastFlipped) {
            return;
        }
        lastFrameIndex = frameIndex;
        lastFlipped = flipped;
        material.setTexture("ColorMap", framesByFlip[frameIndex][flipped ? 1 : 0]);
    }

    private Texture[][] buildTextures(final AnimationImageResource animationFrames) {
        final Texture[][] textures = new Texture[PoofSequence.FRAME_COUNT][2];
        for (int frame = 0; frame < PoofSequence.FRAME_COUNT; frame++) {
            final int[] pixels = animationFrames.getFrameRgbData(frame);
            textures[frame][0] = loadTexture(pixels, POOF_PIXELS);
            textures[frame][1] = loadTexture(pixels, POOF_PIXELS, true);
        }
        return textures;
    }
}
