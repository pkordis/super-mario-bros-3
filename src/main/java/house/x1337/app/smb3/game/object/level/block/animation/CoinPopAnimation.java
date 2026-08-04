package house.x1337.app.smb3.game.object.level.block.animation;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Animates a coin popping out of a ? block or brick.
 *
 * <h2>Physics and timing — ported from dasm {@code prg007.asm CoinPUp_UpdateAndDraw}</h2>
 *
 * <h3>Coin patterns (8x16 sprite)</h3>
 * <pre>{@code
 * CoinPUp_Patterns:   .byte $49, $4F, $4D, $4F
 * CoinPUp_Attributes: .byte SPR_PAL3, SPR_PAL3 | SPR_HFLIP, SPR_PAL3, SPR_PAL3
 * }</pre>
 *
 * <p>Animation sequence (each phase lasts 4 ticks):
 * <ul>
 *   <li>Phase 0 (counter 1-4): pattern $49 (front), no flip</li>
 *   <li>Phase 1 (counter 5-8): pattern $4F (angled), H-flipped</li>
 *   <li>Phase 2 (counter 9-12): pattern $4D (edge/thin), no flip</li>
 *   <li>Phase 3 (counter 13-16): pattern $4F (angled), no flip</li>
 *   <li>Then repeats...</li>
 * </ul>
 *
 * <h3>Physics</h3>
 * <ul>
 *   <li>Initial Y velocity: -5 (upward in NES screen coords)</li>
 *   <li>Y position updated EVERY tick: Y += YVel</li>
 *   <li>Gravity: YVel++ every 4 ticks (when counter & 3 == 0)</li>
 *   <li>Animation ends when YVel reaches +5 (after gravity applied)</li>
 * </ul>
 *
 * <h3>Timing analysis</h3>
 * <p>Counter starts at 1. Gravity applies at counter = 4, 8, 12, ..., 40.
 * That's 10 gravity applications: velocity goes -5 → -4 → ... → +4 → +5.
 * Total ticks = 40. Animation frames cycle through 0→1→2→3→0→1→2 (2.5 cycles).
 */
@Getter
public final class CoinPopAnimation implements GameRenderer {

    // -- Texture indices for our loaded frames --
    private static final int TEX_FRONT = 0;   // $49 - wide front view
    private static final int TEX_EDGE = 1;    // $4D - thin edge view  
    private static final int TEX_ANGLED = 2;  // $4F - angled view

    /**
     * Frame sequence matching dasm CoinPUp_Patterns: $49, $4F, $4D, $4F
     * Maps animation phase (0-3) to texture index.
     */
    private static final int[] FRAME_TEXTURE = {TEX_FRONT, TEX_ANGLED, TEX_EDGE, TEX_ANGLED};

    /**
     * Horizontal flip for each animation phase.
     * dasm CoinPUp_Attributes: SPR_PAL3, SPR_PAL3|SPR_HFLIP, SPR_PAL3, SPR_PAL3
     * Only phase 1 (second frame) is H-flipped.
     */
    private static final boolean[] FRAME_H_FLIP = {false, true, false, false};

    /**
     * Initial upward Y velocity in NES units.
     */
    private static final int INITIAL_Y_VEL_NES = -5;

    /**
     * Terminal velocity that ends the animation.
     */
    private static final int TERMINAL_Y_VEL_NES = 5;

    /**
     * Coin sprite dimensions: 8×16 NES pixels.
     */
    private static final Dimensions COIN_DIMENSIONS = new Dimensions(
        "CoinPop",
        8.0f / TILE_SPRITE_SIZE,
        16.0f / TILE_SPRITE_SIZE
    );

    /**
     * Z-depth for the coin — in front of blocks but behind the score popup.
     */
    private static final float COIN_Z = 0.06f;

    /**
     * Asset paths for the 3 unique coin frames.
     */
    private static final String[] COIN_FRAME_ASSETS = {
        "sprites/object/coin/frame_0.png",  // TEX_FRONT - $49
        "sprites/object/coin/frame_1.png",  // TEX_EDGE  - $4D  
        "sprites/object/coin/frame_2.png",  // TEX_ANGLED - $4F
    };

    // -- Position fields --

    private final Offset offset;
    private final float worldX;
    private final float baseWorldY;

    // -- State --

    private final Node rootNode;
    private final Texture[] coinTextures;
    private final Geometry spriteGeometry;
    private int yPosNes;
    private int yVelNes;
    private int counter;
    private boolean expired;
    private int currentPhase;

    // ----------------------------------------------------------------------

    /**
     * Creates a new coin pop animation.
     *
     * @param gameEngine the game engine
     * @param offset     the tile offset of the block that was hit
     */
    public CoinPopAnimation(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        this.offset = offset;
        // Coin X: center of the block minus half coin width
        this.worldX = offset.x() + 0.5f - COIN_DIMENSIONS.width() / 2;
        // Base Y: top of the block in world coordinates
        this.baseWorldY = gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y() + 1.0f;
        this.rootNode = gameEngine.getRootNode();

        // NES state - Y position in NES pixels relative to spawn point
        // Starts at 0 (at block top), velocity will move it upward (negative Y in NES = up)
        this.yPosNes = 0;
        this.yVelNes = INITIAL_Y_VEL_NES;
        this.counter = 1;  // dasm: counter starts at 1
        this.expired = false;
        this.currentPhase = 0;

        // Load all coin frame textures
        this.coinTextures = new Texture[COIN_FRAME_ASSETS.length];
        for (int i = 0; i < COIN_FRAME_ASSETS.length; i++) {
            coinTextures[i] = loadTexture(gameEngine.getAssetManager(), COIN_FRAME_ASSETS[i]);
        }

        // Create sprite geometry with first frame
        this.spriteGeometry = fromTexture(
            gameEngine.getAssetManager(),
            coinTextures[FRAME_TEXTURE[0]],
            COIN_DIMENSIONS
        );

        positionSprite();
        rootNode.attachChild(spriteGeometry);
    }

    /**
     * Advances the coin animation by one game-tick.
     *
     * <p>Ported directly from dasm {@code CoinPUp_UpdateAndDraw}:
     * <pre>{@code
     *   INC CoinPUp_Counter,X       ; counter++
     *   LDA CoinPUp_Y,X
     *   ADD CoinPUp_YVel,X
     *   STA CoinPUp_Y,X             ; Y += YVel every tick
     *   LDA CoinPUp_Counter,X
     *   AND #$03
     *   BNE skip_gravity            ; skip gravity if (counter & 3) != 0
     *   INC CoinPUp_YVel,X          ; YVel++
     *   LDA CoinPUp_YVel,X
     *   CMP #$05
     *   BEQ coin_expired            ; end when YVel == 5
     * skip_gravity:
     *   ; ... draw code using (counter >> 2) & 3 for frame index
     * }</pre>
     */
    public void tick() {
        if (expired) {
            return;
        }

        // Increment counter first (matches dasm: INC CoinPUp_Counter,X)
        counter++;

        // Apply velocity to position EVERY tick (matches dasm: ADD CoinPUp_YVel,X)
        yPosNes += yVelNes;

        // Apply gravity every 4 ticks (when counter & 0x03 == 0)
        if ((counter & 0x03) == 0) {
            yVelNes++;

            // Check for terminal velocity (animation end)
            if (yVelNes == TERMINAL_Y_VEL_NES) {
                expired = true;
                return;
            }
        }

        // Update animation phase: (counter >> 2) & 3
        final int newPhase = (counter >> 2) & 0x03;
        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            updateSpriteFrame();
        }

        positionSprite();
    }

    /**
     * Returns true when the coin has completed its arc.
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * Detaches the coin sprite from the scene graph.
     */
    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    /**
     * Returns the current world Y position of the coin.
     * Used by the score popup to spawn at the coin's final position.
     *
     * @return current Y position in world coordinates
     */
    public float getCurrentWorldY() {
        // NES Y increases downward, jme3 Y increases upward
        // So we negate the NES offset
        return baseWorldY - (float) yPosNes / TILE_SPRITE_SIZE;
    }

    /**
     * Returns the current world X position of the coin.
     *
     * @return current X position in world coordinates
     */
    public float getCurrentWorldX() {
        return worldX;
    }

    private void updateSpriteFrame() {
        final int textureIdx = FRAME_TEXTURE[currentPhase];
        spriteGeometry.getMaterial().setTexture("ColorMap", coinTextures[textureIdx]);

        // Apply horizontal flip via scale
        final boolean hFlip = FRAME_H_FLIP[currentPhase];
        final float scaleX = hFlip ? -1f : 1f;
        spriteGeometry.setLocalScale(scaleX, 1f, 1f);
    }

    private void positionSprite() {
        final boolean hFlip = FRAME_H_FLIP[currentPhase];
        // When H-flipped, shift X by sprite width to keep visual position stable
        final float hShift = hFlip ? COIN_DIMENSIONS.width() : 0f;

        // Convert NES Y position to world Y
        // NES: positive yPosNes = downward, jme3: positive Y = upward
        final float worldY = baseWorldY - (float) yPosNes / TILE_SPRITE_SIZE;

        spriteGeometry.setLocalTranslation(
            worldX + hShift,
            worldY,
            COIN_Z
        );
    }
}
