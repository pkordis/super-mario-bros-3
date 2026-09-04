package house.x1337.app.smb3.model.game.collision;

import house.x1337.app.smb3.model.game.DimensionsPixels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AxisAlignedBoundingBox overlap in sprite-pixel space (Y-down, half-open edges)")
class AxisAlignedBoundingBoxTest {

    @Test
    @DisplayName("overlapping boxes intersect, symmetrically")
    void overlappingBoxesIntersect() {
        final AxisAlignedBoundingBox a = new AxisAlignedBoundingBox(0, 0, 10, 10);
        final AxisAlignedBoundingBox b = new AxisAlignedBoundingBox(5, 5, 15, 15);
        assertThat(a.intersects(b)).isTrue();
        assertThat(b.intersects(a)).isTrue();
    }

    @Test
    @DisplayName("boxes sharing only an edge do not intersect")
    void edgeContactDoesNotIntersect() {
        final AxisAlignedBoundingBox box = new AxisAlignedBoundingBox(0, 0, 10, 10);
        final AxisAlignedBoundingBox touchingRight = new AxisAlignedBoundingBox(10, 0, 20, 10);
        final AxisAlignedBoundingBox touchingBelow = new AxisAlignedBoundingBox(0, 10, 10, 20);
        assertThat(box.intersects(touchingRight)).isFalse();
        assertThat(box.intersects(touchingBelow)).isFalse();
    }

    @Test
    @DisplayName("fully separated boxes do not intersect")
    void separatedBoxesDoNotIntersect() {
        final AxisAlignedBoundingBox a = new AxisAlignedBoundingBox(0, 0, 10, 10);
        final AxisAlignedBoundingBox faraway = new AxisAlignedBoundingBox(100, 100, 110, 110);
        assertThat(a.intersects(faraway)).isFalse();
    }

    @Test
    @DisplayName("a contained box intersects its container")
    void containedBoxIntersects() {
        final AxisAlignedBoundingBox outer = new AxisAlignedBoundingBox(0, 0, 100, 100);
        final AxisAlignedBoundingBox inner = new AxisAlignedBoundingBox(10, 10, 20, 20);
        assertThat(outer.intersects(inner)).isTrue();
        assertThat(inner.intersects(outer)).isTrue();
    }

    @Test
    @DisplayName("ofSize derives right/bottom from width/height")
    void ofSizeComputesFarEdges() {
        final DimensionsPixels dimensions = new DimensionsPixels(16, 14);
        final AxisAlignedBoundingBox box = AxisAlignedBoundingBox.ofSize(4, 8, dimensions);
        assertThat(box.left()).isEqualTo(4.0);
        assertThat(box.top()).isEqualTo(8.0);
        assertThat(box.right()).isEqualTo(20.0);
        assertThat(box.bottom()).isEqualTo(22.0);
    }
}
