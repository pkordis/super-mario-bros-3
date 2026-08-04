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
 * Animates the "100" score popup that appears when a coin expires.
 *
 * <h2>Implementation — ported from dasm {@code prg007.asm}</h2>
 *
 * <h3>Score spawn (PRG007_AE28)</h3>
 * <pre>{@code
 *   JSR Score_FindFreeSlot
 *   LDA #$85
 *   STA Scores_Value,Y       ; 100 points ($85 & $7F = 5, index into score table)
 *   LDA #$30
 *   STA Scores_Counter,Y     ; 48 frame lifetime
 *   LDA CoinPUp_Y,X
 *   CMP #192
 *   BLT use_coin_y           ; If coin Y < 192, use it
 *   LDA #$05                 ; Otherwise clamp to Y=5
 *   STA Scores_Y,Y
 *   LDA CoinPUp_X,X
 *   SUB #$04                 ; Center the score sprite
 *   STA Scores_X,Y
 * }</pre>
 *
 * <h3>Rise animation (PRG007_AB04)</h3>
 * <pre>{@code
 *   LDA Scores_Counter,X
 *   LSR A
 *   LSR A
 *   LSR A
 *   LSR A
 *   TAY                      ; Y = counter >> 4 (0-3)
 *   LDA <Counter_1
 *   AND Score_RiseCounterMask,Y
 *   BNE no_rise              ; skip if (Counter_1 & mask) != 0
 *   LDA Scores_Y,X
 *   CMP #$04
 *   BLT no_rise              ; stop rising if Y < 4
 *   DEC Scores_Y,X           ; rise by 1 pixel
 * }</pre>
 *
 * <h3>Sprite data</h3>
 * <p>The "100" is a single 16×8 sprite combining the "1" and "00" glyphs.
 */
@Getter
public final class ScorePopupAnimation implements GameRenderer {

    // -- Animation constants from dasm --

    /**
     * Initial counter value (dasm: $30 = 48 decimal).
     */
    private static final int INITIAL_COUNTER = 0x30;

    /**
     * Rise mask values indexed by (counter >> 4).
     * dasm: Score_RiseCounterMask: .byte $03, $01, $00, $00
     *
     * <p>The score rises when {@code (tick & mask) == 0}:
     * <ul>
     *   <li>Index 0 (counter 0-15): mask $03 → rises every 4 ticks</li>
     *   <li>Index 1 (counter 16-31): mask $01 → rises every 2 ticks</li>
     *   <li>Index 2 (counter 32-47): mask $00 → rises every tick</li>
     *   <li>Index 3 (counter 48+): mask $00 → rises every tick (initial state)</li>
     * </ul>
     *
     * <p>Since counter counts DOWN from 48, the progression is:
     * <ul>
     *   <li>Counter 48-32: index 3-2, mask $00 → rises every tick (fast)</li>
     *   <li>Counter 31-16: index 1, mask $01 → rises every 2 ticks (medium)</li>
     *   <li>Counter 15-0: index 0, mask $03 → rises every 4 ticks (slow)</li>
     * </ul>
     */
    private static final int[] RISE_COUNTER_MASK = {0x03, 0x01, 0x00, 0x00};

    /**
     * Minimum Y threshold — score stops rising when Y < 4 NES pixels.
     * dasm: {@code CMP #$04; BLT no_rise}
     */
    private static final double MIN_Y_THRESHOLD = 4.0 / TILE_SPRITE_SIZE;

    /**
     * Rise amount per tick when rising is allowed: 1 NES pixel.
     * dasm: {@code DEC Scores_Y,X}
     */
    private static final double RISE_AMOUNT = 1.0 / TILE_SPRITE_SIZE;

    /**
     * X offset from coin position: -4 NES pixels (centers the 16px-wide score).
     * dasm: {@code LDA CoinPUp_X,X; SUB #$04}
     */
    private static final double X_OFFSET = -4.0 / TILE_SPRITE_SIZE;

    /**
     * Sprite dimensions: 16×8 NES pixels ("1" + "00" glyphs combined).
     */
    private static final Dimensions SPRITE_DIMENSIONS = new Dimensions(
        "Score100",
        16.0f / TILE_SPRITE_SIZE,
        8.0f / TILE_SPRITE_SIZE
    );

    /**
     * Z-depth for the score — in front of the coin.
     */
    private static final float SCORE_Z = 0.07f;

    /**
     * Asset path for the combined "100" sprite (patterns $5B + $69).
     */
    private static final String SCORE_ASSET = "sprites/object/score/score_100.png";

    // -- Position fields --

    private final Offset offset;
    private final float baseWorldX;
    private final float baseWorldY;

    // -- State --

    private final Node rootNode;
    private final Geometry spriteGeometry;
    private double yOffset;
    private int counter;
    private int tick;
    private boolean expired;

    // ----------------------------------------------------------------------

    /**
     * Creates a new score popup animation at the coin's final position.
     *
     * @param gameEngine  the game engine
     * @param offset      the tile offset of the block that was hit
     * @param coinWorldX  the world X position of the expired coin
     * @param coinWorldY  the world Y position of the expired coin
     */
    public ScorePopupAnimation(
            final GameEngine gameEngine,
            final Offset offset,
            final float coinWorldX,
            final float coinWorldY) {
        this.offset = offset;
        // dasm: LDA CoinPUp_X,X; SUB #$04 → center the score above the coin
        this.baseWorldX = coinWorldX + (float) X_OFFSET;
        // Score spawns at coin's Y position (no vertical offset in dasm)
        this.baseWorldY = coinWorldY;
        this.rootNode = gameEngine.getRootNode();

        this.yOffset = 0.0;
        this.counter = INITIAL_COUNTER;
        this.tick = 0;
        this.expired = false;

        final Texture texture = loadTexture(gameEngine.getAssetManager(), SCORE_ASSET);
        this.spriteGeometry = fromTexture(gameEngine.getAssetManager(), texture, SPRITE_DIMENSIONS);

        positionSprite();
        rootNode.attachChild(spriteGeometry);
    }

    /**
     * Advances the score popup animation by one game-tick.
     *
     * <p>Ported from dasm {@code PRG007_AB04}:
     * <pre>{@code
     *   LDA Scores_Counter,X
     *   LSR A  ; >> 1
     *   LSR A  ; >> 2
     *   LSR A  ; >> 3
     *   LSR A  ; >> 4
     *   TAY    ; Y = counter >> 4
     *   LDA <Counter_1
     *   AND Score_RiseCounterMask,Y
     *   BNE no_rise
     *   LDA Scores_Y,X
     *   CMP #$04
     *   BLT no_rise
     *   DEC Scores_Y,X
     * }</pre>
     */
    public void tick() {
        if (expired) {
            return;
        }

        tick++;
        counter--;

        if (counter <= 0) {
            expired = true;
            return;
        }

        // Calculate mask index: counter >> 4 (clamped to valid range)
        final int maskIdx = Math.min(counter >> 4, RISE_COUNTER_MASK.length - 1);
        final int mask = RISE_COUNTER_MASK[maskIdx];

        // Rise when (tick & mask) == 0 and Y is above threshold
        // Note: dasm uses Counter_1 (global frame counter), we use our tick counter
        if ((tick & mask) == 0 && (baseWorldY + yOffset) > MIN_Y_THRESHOLD) {
            yOffset += RISE_AMOUNT;
        }

        positionSprite();
    }

    /**
     * Returns true when the score popup has finished.
     */
    public boolean isExpired() {
        return expired;
    }

    /**
     * Detaches the score sprite from the scene graph.
     */
    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    private void positionSprite() {
        spriteGeometry.setLocalTranslation(baseWorldX, (float) (baseWorldY + yOffset), SCORE_Z);
    }
}
