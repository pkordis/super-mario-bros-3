package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_PIPE_RIGHT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a Goomba pipe stamp may go. The scene is 6 columns by 6 rows. A side pipe opening to the right has its body on
 * column 1 and its lip on column 2, rows 3 and 4. A side pipe opening to the left has its lip on (4, 1) and its body on
 * (5, 1). No pipe portal is placed anywhere.
 */
class EnemyApplicationRulesTest {
    private static final int SIZE = 6;

    private final Tile[][] tiles = new Tile[SIZE][SIZE];
    private final EnemyStamp goombaPipe = stampOf(GOOMBA_PIPE_RIGHT);

    @BeforeEach
    void prepare() {
        put(of(1, 3), PIPE_BODY_PART);
        put(of(1, 4), PIPE_BODY_PART);
        put(of(2, 3), PIPE_TERMINATION_PART);
        put(of(2, 4), PIPE_TERMINATION_PART);
        put(of(4, 1), PIPE_TERMINATION_PART);
        put(of(5, 1), PIPE_BODY_PART);
    }

    @Test
    @DisplayName("A Goomba pipe goes on the cell right of a pipe termination tile, beside either lip cell")
    void goesRightOfATermination() {
        // Execute
        final boolean besideTheLowerCell = isValidOn(of(3, 4));
        final boolean besideTheUpperCell = isValidOn(of(3, 3));

        // Verify
        assertThat(besideTheLowerCell).isTrue();
        assertThat(besideTheUpperCell).isTrue();
    }

    @Test
    @DisplayName("A Goomba pipe goes on the cell left of a pipe termination tile too")
    void goesLeftOfATermination() {
        // Execute
        final boolean valid = isValidOn(of(3, 1));

        // Verify
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("A Goomba pipe over pipe body is refused, even beside a pipe termination tile")
    void isRefusedOverPipeBody() {
        // Execute
        final boolean valid = isValidOn(of(1, 4));

        // Verify
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("A Goomba pipe on the lip itself, or away from any pipe termination tile, is refused")
    void isRefusedAwayFromATermination() {
        // Execute
        final boolean onTheLip = isValidOn(of(2, 4));
        final boolean inTheOpen = isValidOn(of(4, 4));

        // Verify
        assertThat(onTheLip).as("Pipe body on its left, nothing on its right").isFalse();
        assertThat(inTheOpen).isFalse();
    }

    @Test
    @DisplayName("A Goomba pipe a pixel off its cell is refused")
    void isRefusedOffTheCell() {
        // Prepare
        final EnemyPlacement onTheCell = goombaPipe.placedAt(of(3, 4));

        // Execute
        final boolean valid = goombaPipe.validateApplication(tiles, onTheCell.movedBy(1, 0));

        // Verify
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Every other enemy goes anywhere, on any pixel")
    void otherEnemiesHaveNoRules() {
        // Prepare
        final EnemyStamp goomba = stampOf(GOOMBA_NORMAL);

        // Execute
        final boolean valid = goomba.validateApplication(null, goomba.placedAtPixel(of(37, 3)));

        // Verify
        assertThat(valid).isTrue();
    }

    private boolean isValidOn(final Coordinates cell) {
        return goombaPipe.validateApplication(tiles, goombaPipe.placedAt(cell));
    }

    private EnemyStamp stampOf(final EnemyType type) {
        return EnemyStamp.builder()
            .id(type.name())
            .enemyType(type)
            .tiles(new Tile[][] {{tile(ENEMY_PART)}, {tile(ENEMY_PART)}})
            .renderingStarter(of(0, 1))
            .build();
    }

    private void put(final Coordinates cell, final TileType type) {
        tiles[cell.y()][cell.x()] = tile(type);
    }

    private Tile tile(final TileType type) {
        return Tile.builder().id(type.ordinal() + 1).type(type).build();
    }
}
