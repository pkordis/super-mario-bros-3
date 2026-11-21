package house.x1337.app.smb3.model.game;

import com.jme3.scene.shape.Quad;

import java.awt.Dimension;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;

/**
 * Encapsulates the grid dimensions of a level scene.
 *
 * @param columns total number of tile columns in the level
 * @param rows    total number of tile rows in the level
 */
public record LevelSceneDimensions(int columns, int rows) {
    public Quad toQuad() {
        return new Quad(columns, rows);
    }

    public Dimension toPixels() {
        return new Dimension(columns * TILE_SIZE, rows * TILE_SIZE);
    }
}
