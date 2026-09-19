package house.x1337.app.smb3.ui.editor.level.enemy.create;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class EnemyTilesGridPanelTest {
    @Test
    @DisplayName("The editor grid starts with the rendering starter on the lower-left tile")
    void gridPanelDefaultsToLowerLeft() {
        // Prepare - a 3x2 grid of blank parts
        final EnemyTilesGridPanel panel = new EnemyTilesGridPanel();

        // Execute
        panel.render(blankParts(3, 2));

        // Verify - an enemy is anchored where it meets the ground
        assertThat(panel.getRenderingStarterRow()).isEqualTo(2);
        assertThat(panel.getRenderingStarterColumn()).isZero();
    }

    private int[][][] blankParts(final int rows, final int columns) {
        final int[][][] parts = new int[rows][columns][];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                parts[row][column] = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
            }
        }
        return parts;
    }
}
