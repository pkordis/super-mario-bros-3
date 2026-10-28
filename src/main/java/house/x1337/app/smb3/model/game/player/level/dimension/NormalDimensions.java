package house.x1337.app.smb3.model.game.player.level.dimension;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public interface NormalDimensions {
    float SPRITE_WIDTH_PIXELS = TILE_SPRITE_SIZE * 2.0f - TILE_SPRITE_SIZE / 2.0f;
    float SPRITE_HEIGHT_PIXELS = TILE_SPRITE_SIZE * 2.0f;
    float BODY_WIDTH_PIXELS = TILE_SPRITE_SIZE;

    float QUAD_WIDTH = SPRITE_WIDTH_PIXELS * PIXELS_TO_GAME_UNITS;
    float QUAD_HEIGHT = SPRITE_HEIGHT_PIXELS * PIXELS_TO_GAME_UNITS;
    float RIGHT_PADDING = (SPRITE_WIDTH_PIXELS - BODY_WIDTH_PIXELS) * PIXELS_TO_GAME_UNITS;

    float KICK_LEADING_OFFSET = RIGHT_PADDING;
}
