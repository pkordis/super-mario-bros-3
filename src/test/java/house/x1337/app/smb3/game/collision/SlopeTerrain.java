package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.RampLevelObject;
import house.x1337.app.smb3.game.object.level.SolidLevelObject;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.SlopeProfile;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_FALLING_HIGH;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_FALLING_LOW;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_RISING_HIGH;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_RISING_LOW;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.STEEP_FALLING;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.STEEP_RISING;

/**
 * Terrain drawn as text, one character per tile: {@code .} is sky, {@code #} solid ground, {@code /} and {@code \} a
 * steep ramp rising and falling to the right, {@code a} and {@code b} the low and high half of a gentle ramp rising
 * to the right, and {@code c} and {@code d} the high and low half of one falling to the right.
 */
public final class SlopeTerrain {
    private final List<String> rows;

    public SlopeTerrain(final String... rows) {
        this.rows = List.of(rows);
    }

    public int columns() {
        return rows.getFirst().length();
    }

    public int rowCount() {
        return rows.size();
    }

    public StaticEnvironmentCollisionGrid toGrid(final GameEngine gameEngine) {
        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            gameEngine,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(toObjects());
        grid.setUnderlayObjects(emptyObjects());
        grid.setDimensions(new LevelSceneDimensions(columns(), rowCount()));
        return grid;
    }

    /**
     * @return the Y in level pixels of the ground surface under pixel column {@code x}, searching down from the top
     */
    public int surfaceYAt(final int x) {
        final int column = x / TILE_SPRITE_SIZE;
        for (int row = 0; row < rowCount(); row++) {
            final char tile = rows.get(row).charAt(column);
            if (tile == '#') {
                return row * TILE_SPRITE_SIZE;
            }
            final SlopeProfile profile = profileOf(tile);
            if (profile != null) {
                return row * TILE_SPRITE_SIZE + profile.surfaceDepthAt(x);
            }
        }
        return Integer.MAX_VALUE;
    }

    public SlopeProfile profileOf(final char tile) {
        return switch (tile) {
            case '/' -> STEEP_RISING;
            case '\\' -> STEEP_FALLING;
            case 'a' -> GENTLE_RISING_LOW;
            case 'b' -> GENTLE_RISING_HIGH;
            case 'c' -> GENTLE_FALLING_HIGH;
            case 'd' -> GENTLE_FALLING_LOW;
            default -> null;
        };
    }

    private LevelObject[][] toObjects() {
        final LevelObject[][] objects = emptyObjects();
        final LevelObject solid = SolidLevelObject.builder().build();
        for (int row = 0; row < rowCount(); row++) {
            for (int column = 0; column < columns(); column++) {
                final char tile = rows.get(row).charAt(column);
                final SlopeProfile profile = profileOf(tile);
                if (profile != null) {
                    objects[row][column] = new RampLevelObject(null, of(column, row), profile);
                } else if (tile == '#') {
                    objects[row][column] = solid;
                }
            }
        }
        return objects;
    }

    private LevelObject[][] emptyObjects() {
        final LevelObject[][] objects = new LevelObject[rowCount()][columns()];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }
}
