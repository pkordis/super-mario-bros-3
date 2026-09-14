package house.x1337.app.smb3.model.game;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public record DimensionsPixels(
    int width,
    int height
) {
    public static DimensionsPixels fullTile() {
        return new DimensionsPixels(
            TILE_SPRITE_SIZE,
            TILE_SPRITE_SIZE
        );
    }

    public Dimensions toDimensions() {
        return new Dimensions("IntegerDimensions", width, height);
    }
}
