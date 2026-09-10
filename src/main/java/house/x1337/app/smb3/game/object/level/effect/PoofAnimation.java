package house.x1337.app.smb3.game.object.level.effect;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.effect.PoofSequence;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;

/**
 * One puff of smoke at a fixed level cell — the ROM's {@code SOBJ_POOF} special object
 * (dasm prg007 {@code SObj_Poof}).
 *
 * <p>Lifetime and frame order come from {@link PoofSequence#oneShot()}: a counter seeded at $20
 * decrements once per tick, the frame is {@code counter >> 3} (so 4 frames of 8 ticks, read descending
 * from the big puff down to dust), and at zero the effect removes itself, mirroring
 * {@code SpecialObj_Remove}.
 *
 * <p>The cloud is drawn as its own translucent quad rather than baked into the interactive-objects
 * layer, because it has to sit <em>above</em> the switch block it appears over while passing
 * <em>below</em> the empty block bouncing out of the spawner — see {@code Z_DEPTH_POOF}.
 *
 * <p>The ROM renders the 16x16 puff as two 8x16 sprites whose attributes differ by
 * {@code EOR (SPR_HFLIP | SPR_VFLIP)} and toggles the left half's V-flip every 4 ticks. Those two
 * states are exact vertical mirrors of the assembled tile, so this flips the whole quad's pixel rows
 * instead — the same image, not an approximation.
 */
@Getter
public final class PoofAnimation implements GameRenderer {
    private static final Dimensions POOF_DIMENSIONS = new Dimensions(
        "Poof",
        TILE_SIZE_GAME_UNITS,
        TILE_SIZE_GAME_UNITS
    );
    private static final DimensionsPixels POOF_PIXELS = new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE);

    private final Offset offset;
    private final PoofSequence sequence;
    private final Node rootNode;
    private final Geometry spriteGeometry;
    private final Material material;
    private final Texture[][] framesByFlip;
    private final float worldX;
    private final float worldY;

    private int counter;
    private int lastFrameIndex = -1;
    private boolean lastFlipped;
    private boolean expired;

    /**
     * Starts a puff at {@code offset}.
     *
     * @param gameEngine      the game engine
     * @param offset          the level cell the puff covers
     * @param animationFrames the shared poof frames, in ROM table order
     */
    public PoofAnimation(
        final GameEngine gameEngine,
        final Offset offset,
        final AnimationImageResource animationFrames
    ) {
        this.offset = offset;
        this.sequence = PoofSequence.oneShot();
        this.counter = PoofSequence.ONE_SHOT_TICKS;
        this.rootNode = gameEngine.getRootNode();
        this.worldX = offset.x();
        this.worldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y();
        this.framesByFlip = buildTextures(animationFrames);

        // fromTexture supplies the shared translucent, depth-test-free material every sprite quad in
        // the project uses; the per-tick path then only swaps this material's ColorMap.
        this.spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            framesByFlip[sequence.frameIndexFor(counter)][sequence.isFlippedVerticallyAt(counter) ? 1 : 0],
            POOF_DIMENSIONS
        );
        this.material = spriteGeometry.getMaterial();
        spriteGeometry.setLocalTranslation(worldX, worldY, Z_DEPTH_POOF);

        applyCurrentFrame();
        rootNode.attachChild(spriteGeometry);
    }

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

    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    private void applyCurrentFrame() {
        final int frameIndex = sequence.frameIndexFor(counter);
        final boolean flipped = sequence.isFlippedVerticallyAt(counter);
        if (frameIndex == lastFrameIndex && flipped == lastFlipped) {
            return;
        }
        lastFrameIndex = frameIndex;
        lastFlipped = flipped;
        material.setTexture("ColorMap", framesByFlip[frameIndex][flipped ? 1 : 0]);
    }

    /**
     * Pre-builds every frame upright and mirrored, so a tick only swaps a texture reference.
     */
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
