package house.x1337.app.smb3.model.game.motion;

import house.x1337.app.smb3.model.game.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The sprite-pixel position every active object carries: Y grows down, one tile is 16 pixels, and
 * the sub-pixel remainder lives in the {@code double} rather than in a ROM-style fraction
 * accumulator.
 */
class PixelPositionTest {
    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("A tile cell converts to the pixel coordinates of its top-left corner")
    void tileCellConvertsToItsTopLeftCornerInPixels() {
        // Prepare
        final Offset cell = Offset.of(3, 7);

        // Execute
        final PixelPosition position = PixelPosition.ofTile(cell);

        // Verify
        assertThat(position.x()).isCloseTo(48.0, within(TOLERANCE));
        assertThat(position.y()).isCloseTo(112.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Replacing one axis leaves the other exactly as it was")
    void replacingOneAxisLeavesTheOtherAlone() {
        // Prepare
        final PixelPosition position = new PixelPosition(10.5, 20.25);

        // Execute
        final PixelPosition movedX = position.withX(99.0);
        final PixelPosition movedY = position.withY(99.0);

        // Verify
        assertThat(movedX.y()).as("Y untouched").isCloseTo(20.25, within(TOLERANCE));
        assertThat(movedY.x()).as("X untouched").isCloseTo(10.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("Offsetting by a delta is how an inset collision box is anchored")
    void offsettingByADeltaAnchorsAnInsetBox() {
        // Prepare - the Goomba's Object_BoundBox row 01 inset
        final PixelPosition spriteTopLeft = new PixelPosition(64.0, 32.0);

        // Execute
        final PixelPosition boxTopLeft = spriteTopLeft.plus(1, 2);

        // Verify
        assertThat(boxTopLeft.x()).isCloseTo(65.0, within(TOLERANCE));
        assertThat(boxTopLeft.y()).isCloseTo(34.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Stepping by a velocity advances both axes by that frame's pixel movement")
    void steppingAdvancesBothAxes() {
        // Prepare - 4.4 fixed point, so 8 is half a pixel and -32 is two pixels up
        final PixelPosition position = new PixelPosition(100.0, 50.0);
        final FixedPointVelocity velocity = new FixedPointVelocity(8, -32);

        // Execute
        final PixelPosition stepped = position.stepped(velocity);

        // Verify
        assertThat(stepped.x()).isCloseTo(100.5, within(TOLERANCE));
        assertThat(stepped.y()).isCloseTo(48.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("Single-axis stepping moves only that axis, so the other can be collided separately")
    void singleAxisSteppingMovesOnlyThatAxis() {
        // Prepare
        final PixelPosition position = new PixelPosition(100.0, 50.0);
        final FixedPointVelocity velocity = new FixedPointVelocity(8, -32);

        // Execute
        final PixelPosition steppedX = position.steppedX(velocity);
        final PixelPosition steppedY = position.steppedY(velocity);

        // Verify
        assertThat(steppedX.x()).isCloseTo(100.5, within(TOLERANCE));
        assertThat(steppedX.y()).as("Y held back for its own collision pass").isCloseTo(50.0, within(TOLERANCE));
        assertThat(steppedY.y()).isCloseTo(48.0, within(TOLERANCE));
        assertThat(steppedY.x()).as("X held back for its own collision pass").isCloseTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("The tile column and row floor, so any point inside a cell reports that cell")
    void tileColumnAndRowFloorIntoTheContainingCell() {
        // Prepare
        final PixelPosition insideCell = new PixelPosition(31.9, 16.1);

        // Execute & Verify
        assertThat(insideCell.column()).isEqualTo(1);
        assertThat(insideCell.row()).isEqualTo(1);
        assertThat(new PixelPosition(32.0, 32.0).column()).as("Exactly on the boundary").isEqualTo(2);
    }

    @Test
    @DisplayName("Flooring runs the right way for negative coordinates, off the left edge of a level")
    void flooringGoesTheRightWayForNegativeCoordinates() {
        // Prepare - a mushroom that has rolled past column 0
        final PixelPosition offLeftEdge = new PixelPosition(-0.5, 8.0);

        // Execute & Verify - truncation would give 0 here and wrongly probe the first column
        assertThat(offLeftEdge.column()).isEqualTo(-1);
    }
}
