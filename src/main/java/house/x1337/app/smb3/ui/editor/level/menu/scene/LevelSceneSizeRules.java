package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_X;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_Y;

/**
 * The sizes a level scene may have, in tiles. The smallest fills the screen. A side may be up to
 * {@value #MAX_SIDE} tiles long, but a scene holds at most {@value #MAX_CELLS} tiles: every layer is a grid of all
 * of them, held in memory and stored as one flat list, so a scene near the largest sides would not fit.
 */
@Singleton
public class LevelSceneSizeRules {
    static final int MIN_COLUMNS = VIEWPORT_TILES_X;
    static final int MIN_ROWS = VIEWPORT_TILES_Y;
    static final int MAX_SIDE = 65535;
    static final int MAX_CELLS = 1_000_000;

    /**
     * @return why a scene of this size is refused, or nothing when it is allowed
     */
    public Optional<String> validate(
        final int columns,
        final int rows
    ) {
        if (columns < MIN_COLUMNS || rows < MIN_ROWS) {
            return Optional.of("A scene is at least %d columns by %d rows, the size of the screen."
                .formatted(MIN_COLUMNS, MIN_ROWS));
        }
        if (columns > MAX_SIDE || rows > MAX_SIDE) {
            return Optional.of("A scene is at most %d tiles on each side.".formatted(MAX_SIDE));
        }
        final long cells = (long) columns * rows;
        if (cells > MAX_CELLS) {
            return Optional.of("A scene holds at most %d tiles, and %d columns by %d rows is %d."
                .formatted(MAX_CELLS, columns, rows, cells));
        }
        return Optional.empty();
    }

    /**
     * The bounds, as the size dialog explains them.
     */
    public String describe() {
        return "%d to %d columns, %d to %d rows, at most %d tiles in all."
            .formatted(MIN_COLUMNS, MAX_SIDE, MIN_ROWS, MAX_SIDE, MAX_CELLS);
    }
}
