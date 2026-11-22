package house.x1337.app.smb3.ui.editor.level.enemy.create;

import house.x1337.app.smb3.ui.editor.level.enemy.create.EnemyTilesGridPanel.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.event.MouseEvent.BUTTON1;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static org.assertj.core.api.Assertions.assertThat;

/** The magnified parts grid: 8 panel pixels per sprite pixel, so a part is 128 panel pixels square. */
class EnemyTilesGridPanelTest {
    private static final int RED = 0xFFFF0000;

    private final EnemyTilesGridPanel panel = new EnemyTilesGridPanel();

    @Test
    @DisplayName("The editor grid starts with the rendering starter on the lower-left tile")
    void gridPanelDefaultsToLowerLeft() {
        // Execute
        panel.render(redParts(3, 2));

        // Verify - an enemy is anchored where it meets the ground
        assertThat(panel.getRenderingStarter()).isEqualTo(of(0, 2));
    }

    @Test
    @DisplayName("A click with the rendering starter tool moves it to the clicked tile, and clears no pixel")
    void renderingStarterToolMovesTheStarter() {
        // Prepare
        final int[][][] parts = redParts(2, 2);
        panel.render(parts, of(0, 1));

        // Execute
        press(128 + 20, 20);

        // Verify
        assertThat(panel.getRenderingStarter()).isEqualTo(of(1, 0));
        assertThat(parts[0][1]).containsOnly(RED);
    }

    @Test
    @DisplayName("A click with the pixel remover clears the sprite pixel under it to transparent, and only that one")
    void pixelRemoverClearsThePixel() {
        // Prepare
        final int[][][] parts = redParts(2, 2);
        panel.render(parts, of(0, 1));
        panel.setTool(Tool.PIXEL_REMOVER);

        // Execute - panel pixel (128 + 3 * 8 + 5, 128 + 7 * 8 + 1) is sprite pixel (3, 7) of the lower-right part
        press(128 + 29, 128 + 57);

        // Verify
        assertThat(parts[1][1][7 * TILE_SPRITE_SIZE + 3]).isZero();
        assertThat(Arrays.stream(parts[1][1]).filter(pixel -> pixel == 0).count()).isEqualTo(1);
        assertThat(parts[0][0]).as("Another part").containsOnly(RED);
        assertThat(panel.getRenderingStarter()).as("The starter stays").isEqualTo(of(0, 1));
    }

    @Test
    @DisplayName("Parts that share one pixel array lose a cleared pixel together")
    void sharedPartsLoseThePixelTogether() {
        // Prepare - the same tile on both cells of the top row
        final int[][][] parts = redParts(2, 2);
        parts[0][1] = parts[0][0];
        panel.render(parts, of(0, 1));
        panel.setTool(Tool.PIXEL_REMOVER);

        // Execute
        press(0, 0);

        // Verify
        assertThat(parts[0][1][0]).isZero();
    }

    private void press(final int x, final int y) {
        panel.getMouseListeners()[0].mousePressed(new MouseEvent(panel, MOUSE_PRESSED, 0, 0, x, y, 1, false, BUTTON1));
    }

    private int[][][] redParts(final int rows, final int columns) {
        final int[][][] parts = new int[rows][columns][];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                parts[row][column] = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
                Arrays.fill(parts[row][column], RED);
            }
        }
        return parts;
    }
}
