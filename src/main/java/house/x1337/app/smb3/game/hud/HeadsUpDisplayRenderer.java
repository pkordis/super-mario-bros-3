package house.x1337.app.smb3.game.hud;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;
import house.x1337.app.smb3.enumeration.PlayerIdentityType;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.util.GameMath;

import java.nio.ByteBuffer;

import static com.jme3.material.RenderState.BlendMode.Off;
import static house.x1337.app.smb3.GameConstants.TILE_SCALE;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.ARROW_DARK;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.ARROW_LIT;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.LUIGI_LEFT;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.LUIGI_RIGHT;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.MARIO_LEFT;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.MARIO_RIGHT;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.P_LEFT_DARK;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.P_LEFT_LIT;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.P_RIGHT_DARK;
import static house.x1337.app.smb3.enumeration.HeadsUpDisplayGlyph.P_RIGHT_LIT;
import static java.lang.Math.clamp;
import static java.lang.Math.pow;

/**
 * Programmatic renderer for the SMB3 status bar HUD.
 *
 * <p>Uses the pixel-perfect static base image ({@code hud_base.png}) as the
 * background - this provides the exact frame borders, "WORLD" text, card slot
 * outlines, and all other static elements without any approximation.
 *
 * <p>Dynamic content (digits, P-meter arrows, icons) is rendered by blitting
 * CHR-extracted glyph images over the positions identified by the colored
 * zones in the {@code hud_basic.png} template.
 *
 * <p>The final 240×48 image is scaled by {@code TILE_SCALE} (4×) using
 * nearest-neighbor interpolation for pixel-perfect NES rendering.
 *
 * <p>Zone positions (from hud_base.png template analysis):
 * <pre>
 * Top sub-row (y=16-23):
 *   Col 5  (x=40):   World number digit
 *   Cols 7-12 (x=56-103): P-meter 6 arrows
 *   Cols 13-14 (x=104-119): [P] indicator
 *   Col 16 (x=128): Coin count 3 digits (after static '$' symbol)
 *
 * Bottom sub-row (y=24-31):
 *   Cols 1-2 (x=8-23): Mario/Luigi icon
 *   Col 5 (x=40): Lives digit
 *   Cols 7-13 (x=56-111): Score 7 digits
 *   Cols 17-19 (x=136-159): Timer 3 digits (when active)
 *
 * Card slots (both rows, y=16-31):
 *   Cols 21-22 (x=168-183): Card 1
 *   Cols 24-25 (x=192-207): Card 2
 *   Cols 27-28 (x=216-231): Card 3
 * </pre>
 */
public interface HeadsUpDisplayRenderer extends HeadsUpDisplayFontRenderer, GameEngineAware, GameMath {
    // Top sub-row (y=16-23)
    int TOP_Y = 16;
    int WORLD_NUM_X = 40;
    int P_METER_X = 56;
    int P_METER_ARROW_COUNT = 6;
    int P_INDICATOR_X = 104;
    int COIN_X = 144;

    // Bottom sub-row (y=24-31)
    int BOTTOM_Y = 24;
    int PLAYER_ICON_X = 8;
    int LIVES_X = 40;
    int SCORE_X = 56;
    int SCORE_DIGITS = 7;
    int TIMER_X = 128;
    int TIMER_DIGITS = 3;

    /**
     * Renders the HUD by copying the static base image and overlaying dynamic
     * content based on the current {@link PlayerData}.
     *
     * @param data the current player state to render
     * @return int array of ARGB pixels (240×48, row-major, top-to-bottom)
     */
    default int[] renderToPixels(final PlayerData data) {
        // Start from the pixel-perfect base image
        final int[] clonedBaseImagePixels = getBaseImage().copy().getRgbData();

        // --- Top sub-row: world number + P-meter + coins ---
        blitGlyph(clonedBaseImagePixels, digitGlyph(clampDigit(data.getWorld())), WORLD_NUM_X, TOP_Y);
        renderPMeter(clonedBaseImagePixels, data.getPMeter(), data.isPMeterFull());
        renderRightAligned(clonedBaseImagePixels, data.getCoins(), COIN_X, TOP_Y);

        // --- Bottom sub-row: player icon + lives + score + timer ---
        // Note: the '×' symbol and '$' coin symbol are static in hud_base.png
        renderPlayerIcon(clonedBaseImagePixels, data.getIdentity().getType());
        renderLives(clonedBaseImagePixels, data.getLives());
        renderDigits(clonedBaseImagePixels, data.getScore(), SCORE_DIGITS, SCORE_X);

        if (data.haveTimerActive()) {
            renderDigits(clonedBaseImagePixels, data.getTimer(), TIMER_DIGITS, TIMER_X);
        }

        return clonedBaseImagePixels;
    }

    /**
     * Creates or updates a jME3 {@link Geometry} from the given player state,
     * scaling it by {@code TILE_SCALE} for display in the HUD viewport.
     */
    default void renderToGeometry(
        final Node hudRoot,
        final PlayerData state
    ) {
        final int[] pixels = renderToPixels(state);
        final int scaledWidth = HUD_WIDTH * TILE_SCALE;
        final int scaledHeight = HUD_HEIGHT * TILE_SCALE;
        final ByteBuffer buffer = BufferUtils.createByteBuffer(scaledWidth * scaledHeight * 4);

        // jME3 expects bottom-to-top row order; scale with nearest-neighbor
        for (int imgRow = 0; imgRow < scaledHeight; imgRow++) {
            final int srcRow = (scaledHeight - 1 - imgRow) / TILE_SCALE;
            for (int imgCol = 0; imgCol < scaledWidth; imgCol++) {
                final int srcCol = imgCol / TILE_SCALE;
                final int argb = pixels[srcRow * HUD_WIDTH + srcCol];
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));         // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        final Texture2D texture = toTexture(buffer, new DimensionsPixels(scaledWidth, scaledHeight));
        final Geometry cachedGeometry = (Geometry) hudRoot.getChild("HudTexture");
        if (cachedGeometry != null) {
            cachedGeometry.getMaterial().setTexture("ColorMap", texture);
            return;
        }
        final Geometry geometry = new Geometry("HudTexture", new Quad(1f, 1f));
        final Material material = new Material(
            getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md"
        );
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(Off);
        geometry.setMaterial(material);
        hudRoot.attachChild(geometry);
    }

    private void renderPMeter(final int[] pixels, final int pMeter, final boolean full) {
        for (int i = 0; i < P_METER_ARROW_COUNT; i++) {
            final HeadsUpDisplayGlyph arrow = (pMeter > i) ? ARROW_LIT : ARROW_DARK;
            blitGlyph(pixels, arrow, P_METER_X + i * CELL_WIDTH, TOP_Y);
        }

        // [P] indicator (2 cells wide)
        if (full) {
            blitGlyph(pixels, P_LEFT_LIT, P_INDICATOR_X, TOP_Y);
            blitGlyph(pixels, P_RIGHT_LIT, P_INDICATOR_X + CELL_WIDTH, TOP_Y);
        } else {
            blitGlyph(pixels, P_LEFT_DARK, P_INDICATOR_X, TOP_Y);
            blitGlyph(pixels, P_RIGHT_DARK, P_INDICATOR_X + CELL_WIDTH, TOP_Y);
        }
    }

    private void renderPlayerIcon(
        final int[] pixels,
        final PlayerIdentityType identity
    ) {
        switch (identity) {
            case MARIO:
                blitGlyph(pixels, MARIO_LEFT, PLAYER_ICON_X, BOTTOM_Y);
                blitGlyph(pixels, MARIO_RIGHT, PLAYER_ICON_X + CELL_WIDTH, BOTTOM_Y);
                break;
            case LUIGI:
                blitGlyph(pixels, LUIGI_LEFT, PLAYER_ICON_X, BOTTOM_Y);
                blitGlyph(pixels, LUIGI_RIGHT, PLAYER_ICON_X + CELL_WIDTH, BOTTOM_Y);
                break;
            default:
                throw new IllegalArgumentException("Unknown playerIdentity: " + identity);
        }
    }

    private void renderLives(
        final int[] pixels,
        final int lives
    ) {
        final int clamped = clamp(lives, 0, 99);
        renderRightAligned(pixels, clamped, LIVES_X, BOTTOM_Y);
    }

    /**
     * Renders an integer right-aligned from the given anchor position. Only
     * significant digits are drawn (no leading zeros). The least significant
     * digit is always placed at {@code rightX}, and additional digits expand
     * to the left.
     *
     * @param pixels the pixel buffer
     * @param value  the integer value to render (must be ≥ 0)
     * @param rightX x-coordinate of the rightmost (LSD) cell
     * @param y      y-coordinate of the cell row
     */
    private void renderRightAligned(
        final int[] pixels,
        final int value,
        final int rightX,
        final int y
    ) {
        if (value <= 0) {
            blitGlyph(pixels, digitGlyph(0), rightX, y);
            return;
        }
        int remaining = value;
        int offsetX = rightX;
        while (remaining > 0) {
            blitGlyph(pixels, digitGlyph(remaining % 10), offsetX, y);
            remaining /= 10;
            offsetX -= CELL_WIDTH;
        }
    }

    private void renderDigits(
        final int[] pixels,
        final int value,
        final int digitCount,
        final int startX
    ) {
        final int maxVal = (int) pow(10, digitCount) - 1;
        final int clamped = clamp(value, 0, maxVal);
        for (int i = 0; i < digitCount; i++) {
            final int power = (int) pow(10, digitCount - 1 - i);
            final int digit = (clamped / power) % 10;
            blitGlyph(pixels, digitGlyph(digit), startX + i * CELL_WIDTH, BOTTOM_Y);
        }
    }

    /**
     * Blits a hud-font glyph onto the pixel buffer at the given position.
     * Only non-transparent pixels are drawn (alpha > 0), preserving the
     * static base image underneath transparent areas of the glyph.
     */
    private void blitGlyph(
        final int[] pixels,
        final HeadsUpDisplayGlyph glyph,
        final int posX,
        final int posY
    ) {
        final int[] glyphPixels = pixels(glyph);
        for (int row = 0; row < CELL_HEIGHT; row++) {
            final int destY = posY + row;
            if (destY < 0 || destY >= HUD_HEIGHT) {
                continue;
            }
            for (int col = 0; col < CELL_WIDTH; col++) {
                final int destX = posX + col;
                if (destX < 0 || destX >= HUD_WIDTH) {
                    continue;
                }
                final int argb = glyphPixels[row * CELL_WIDTH + col];
                final int alpha = (argb >> 24) & 0xFF;
                if (alpha > 0) {
                    pixels[destY * HUD_WIDTH + destX] = argb;
                }
            }
        }
    }
}
