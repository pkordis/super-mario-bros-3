package house.x1337.app.smb3.game.level.completion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fireworks' shapes and fade steps, read from their text files, and the reader they share, tried on the small
 * grids under {@code src/test/resources/grid/}.
 */
class CharacterGridResourceTest {
    private final CharacterGridResource reader = new CharacterGridResource() {
    };

    @Test
    @DisplayName("A grid is read row by row, top first, whatever its line endings and with blank lines at the end "
        + "left out")
    void aGridIsReadRowByRow() {
        // Execute
        final String[] lf = reader.readGrid("classpath:/grid/three_by_three.txt", 3, "#.");
        final String[] crlf = reader.readGrid("classpath:/grid/three_by_three_crlf.txt", 3, "#.");

        // Verify
        assertThat(lf).containsExactly("#.#", ".#.", "#.#");
        assertThat(crlf).containsExactly(lf);
    }

    @Test
    @DisplayName("A grid with too few rows, a short row or a character it may not hold is refused, naming the file")
    void aBadGridIsRefused() {
        // Execute & Verify
        assertThatThrownBy(() -> reader.readGrid("classpath:/grid/two_rows.txt", 3, "#."))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("two_rows.txt")
            .hasMessageContaining("2 rows");
        assertThatThrownBy(() -> reader.readGrid("classpath:/grid/short_row.txt", 3, "#."))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("row 1 has 2 cells");
        assertThatThrownBy(() -> reader.readGrid("classpath:/grid/odd_character.txt", 3, "#."))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("row 1, column 1 holds 'x'");
        assertThatThrownBy(() -> reader.readGrid("classpath:/grid/missing.txt", 3, "#."))
            .isInstanceOf(UncheckedIOException.class)
            .hasMessageContaining("missing.txt");
    }

    @Test
    @DisplayName("Each fireworks shape is read from its own file, a 16 x 16 grid of dots")
    void theShapesAreReadFromTheirFiles() {
        // Execute & Verify
        for (final FireworksShape shape : FireworksShape.values()) {
            assertThat(shape.getPath()).isEqualTo(
                "classpath:/sprites/object/card/fireworks/shape/" + shape.name().toLowerCase() + ".txt"
            );
            int dots = 0;
            for (int row = 0; row < 16; row++) {
                for (int column = 0; column < 16; column++) {
                    dots += shape.hasDot(column, row) ? 1 : 0;
                }
            }
            assertThat(dots).as("%s has dots", shape).isPositive();
        }
        assertThat(FireworksShape.MUSHROOM.hasDot(5, 0)).as("The mushroom's cap, top row").isTrue();
        assertThat(FireworksShape.MUSHROOM.hasDot(4, 0)).isFalse();
        assertThat(FireworksShape.STAR.hasDot(7, 0)).as("The star's tip").isTrue();
    }

    @Test
    @DisplayName("Each fade step is read from its own file, an 8 x 8 grid of 16 x 16 px blocks")
    void theFadesAreReadFromTheirFiles() {
        // Execute & Verify
        assertThat(Arrays.stream(FireworksFade.values()).map(FireworksFade::getStep))
            .as("One fade per step, 1 to 5")
            .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        for (final FireworksFade fade : FireworksFade.values()) {
            assertThat(fade.getPath()).isEqualTo(
                "classpath:/sprites/object/card/fireworks/fade/" + fade.name().toLowerCase() + ".txt"
            );
        }
        for (int row = 0; row < 16; row++) {
            for (int column = 0; column < 16; column++) {
                final boolean middle = column >= 6 && column < 10 && row >= 6 && row < 10;
                assertThat(FireworksFade.MIDDLE.isSeen(column, row)).as("Middle (%d, %d)", column, row)
                    .isEqualTo(middle);
                assertThat(FireworksFade.MIDDLE.isBright(column, row)).isFalse();
                assertThat(FireworksFade.ALL_BRIGHT.isBright(column, row)).as("All bright (%d, %d)", column, row)
                    .isTrue();
            }
        }
    }
}
