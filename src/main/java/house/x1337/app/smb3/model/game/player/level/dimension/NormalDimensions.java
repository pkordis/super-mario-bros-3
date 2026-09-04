package house.x1337.app.smb3.model.game.player.level.dimension;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * Sprite dimensions for "normal" (big, non-powered) Mario. Every frame shares a
 * single 24×32 canvas (matching raccoon's footprint), so one quad width serves
 * all states. The 16px body is authored flush to the left edge of that canvas,
 * leaving an 8px transparent margin on the right; {@link #RIGHT_PADDING} feeds
 * the flip so the body stays aligned with the collision box when facing right.
 */
public interface NormalDimensions {
    float SPRITE_WIDTH_PIXELS = TILE_SPRITE_SIZE * 2.0f - TILE_SPRITE_SIZE / 2.0f;
    float SPRITE_HEIGHT_PIXELS = TILE_SPRITE_SIZE * 2.0f;
    float BODY_WIDTH_PIXELS = TILE_SPRITE_SIZE;

    float QUAD_WIDTH = SPRITE_WIDTH_PIXELS * PIXELS_TO_GAME_UNITS;
    float QUAD_HEIGHT = SPRITE_HEIGHT_PIXELS * PIXELS_TO_GAME_UNITS;
    float RIGHT_PADDING = (SPRITE_WIDTH_PIXELS - BODY_WIDTH_PIXELS) * PIXELS_TO_GAME_UNITS;
}
