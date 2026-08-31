package house.x1337.app.smb3.model.game.collision;

import house.x1337.app.smb3.model.game.DimensionsPixels;

/**
 * An axis-aligned bounding box in <b>sprite-pixel space</b> — the same coordinate space the
 * player position ({@code PlayerPosition}) and every {@code ActiveLevelObject} live in: X grows
 * right, Y grows <b>down</b>, origin at the level's top-left, one tile = 16 sprite-pixels.
 *
 * <p>This is the shared currency of dynamic (moving-object) collision: both a player's hitbox and
 * an object's collision box are expressed as an {@code Aabb}, and overlap is a single
 * {@link #intersects(AxisAlignedBoundingBox)} call. It is deliberately distinct from the static terrain
 * {@code CollisionGrid}, which resolves the player against tile-aligned solids via directional
 * probes rather than box overlap.
 *
 * <p>Edges follow the half-open convention {@code [left, right)} × {@code [top, bottom)}, so boxes
 * that merely share an edge do not count as overlapping.
 *
 * @param left   smallest X (inclusive)
 * @param top    smallest Y (inclusive) — the visually <em>upper</em> edge, since Y grows down
 * @param right  largest X (exclusive)
 * @param bottom largest Y (exclusive) — the visually <em>lower</em> edge
 */
public record AxisAlignedBoundingBox(double left, double top, double right, double bottom) {
    /**
     * @param other the box to test against
     * @return {@code true} if the two boxes overlap on both axes (edge-only contact excluded)
     */
    public boolean intersects(final AxisAlignedBoundingBox other) {
        return right > other.left
            && left < other.right
            && bottom > other.top
            && top < other.bottom;
    }

    public static AxisAlignedBoundingBox ofSize(
        final double left,
        final double top,
        final DimensionsPixels dimensions
    ) {
        return new AxisAlignedBoundingBox(left, top, left + dimensions.width(), top + dimensions.height());
    }
}
