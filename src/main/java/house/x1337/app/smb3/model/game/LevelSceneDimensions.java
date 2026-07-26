package house.x1337.app.smb3.model.game;

import com.jme3.scene.shape.Quad;

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
}
