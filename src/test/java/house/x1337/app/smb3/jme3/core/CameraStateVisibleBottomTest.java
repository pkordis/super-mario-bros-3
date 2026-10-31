package house.x1337.app.smb3.jme3.core;

import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The line a pit death is measured against (dasm prg008 @ PRG008_A3FA: the sprite's top on screen line
 * 192, the bottom of the playfield): the lower of the level's bottom and the view's bottom. The view is
 * 12 rows, 192 px, tall.
 */
class CameraStateVisibleBottomTest {
    private static final double VIEW_HEIGHT_PIXELS = 192;

    @ParameterizedTest(name = "{0} rows, view {1} to {2}, sprite top {3}: below = {4}")
    @CsvSource({
        // A 27-row level with the camera at its bottom: the view's bottom is the level's, 432
        "27, 240, 432, 431.9, false",
        "27, 240, 432, 432, true",
        // The camera scrolled up in flight: the view ends at 300, but the level's floor is still at 432
        "27, 108, 300, 300, false",
        "27, 108, 300, 431, false",
        "27, 108, 300, 432, true",
        // An 8-row level, 128 px, centred in the view: the view's bottom is 32 px below the level's
        "8, -32, 160, 128, false",
        "8, -32, 160, 159.5, false",
        "8, -32, 160, 160, true"
    })
    @DisplayName("A sprite is below the visible bottom once its top reaches the lower of the two bottoms")
    void theLineIsTheLowerOfTheLevelAndTheViewBottom(
        final int rows,
        final double viewTop,
        final double viewBottom,
        final double spriteTop,
        final boolean below
    ) {
        // Prepare
        final CameraState cameraState = cameraOver(rows, viewTop, viewBottom);
        final PlayerPosition position = new PlayerPosition();
        position.setY(spriteTop);

        // Execute
        final boolean result = cameraState.isBelowVisibleBottom(position);

        // Verify
        assertThat(viewBottom - viewTop).as("The fixture's view is 192 px").isCloseTo(VIEW_HEIGHT_PIXELS,
            within(1e-9));
        assertThat(result).as("Level bottom %s px", rows * TILE_SPRITE_SIZE).isEqualTo(below);
    }

    @ParameterizedTest(name = "Sprite top {0}")
    @CsvSource({"-500", "-16", "0"})
    @DisplayName("Nothing above the view ever counts, which is the ROM's Player_AboveTop gate")
    void nothingAboveTheViewCounts(final double spriteTop) {
        // Prepare
        final CameraState cameraState = cameraOver(27, 240, 432);
        final PlayerPosition position = new PlayerPosition();
        position.setY(spriteTop);

        // Execute
        final boolean result = cameraState.isBelowVisibleBottom(position);

        // Verify
        assertThat(result).isFalse();
    }

    private static CameraState cameraOver(final int rows, final double viewTop, final double viewBottom) {
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getPendingRows()).thenReturn(rows);
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(0, viewTop, 256, viewBottom));
        doCallRealMethod().when(cameraState).isBelowVisibleBottom(any());
        return cameraState;
    }
}
