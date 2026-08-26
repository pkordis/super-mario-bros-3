package house.x1337.app.smb3.model.game;

import com.jme3.scene.shape.Quad;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;

public record Dimensions(
    String name,
    float width,
    float height
) {
    public static Dimensions halfTileWidth(final String name) {
        return new Dimensions(
            name,
            TILE_SIZE_GAME_UNITS / 2,
            TILE_SIZE_GAME_UNITS
        );
    }

    public static Dimensions halfTileHeight(final String name) {
        return new Dimensions(
            name,
            TILE_SIZE_GAME_UNITS,
            TILE_SIZE_GAME_UNITS / 2
        );
    }

    public Quad toQuad() {
        return new Quad(width, height);
    }
}
