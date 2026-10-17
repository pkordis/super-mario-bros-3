package house.x1337.app.smb3.jme3.core;

import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.GeometryComparator;
import com.jme3.scene.Geometry;

/**
 * Orders a render bucket by the world Z its geometries were given, back to front.
 *
 * <p>This replaces jME's {@code TransparentComparator}, which sorts by
 * {@code getWorldBound().distanceToEdge(camera.getLocation())} — the distance from the camera to the
 * <em>nearest corner</em> of each bounding box. In a 2D scene that is not a depth order at all: the
 * camera sits in front of the middle of the screen, so a sprite's distance is dominated by how far it
 * is from the screen centre on X and Y, and the hundredths of a game-unit that separate our
 * {@code Z_DEPTH_*} constants are lost in the noise. Two sprites of the same object that are offset
 * from each other — a Para-Goomba's wings against its body — therefore swap over as the object moves
 * across the screen, and a sprite cannot opt into being drawn in front of another by choosing a depth
 * at all (see {@code ForemostSpriteOverlay}, which only exists because of that).
 *
 * <p>Sorting on Z alone makes the {@code Z_DEPTH_*} ladder in {@code GameConstants} authoritative and
 * the result stable: a sprite's cover order depends on nothing but the depth it asked for. Depth
 * testing stays off (see {@code GameRenderer#fromTexture}), so this order is the only thing deciding
 * what covers what, and overlapping transparent quads still blend rather than z-reject.
 *
 * <p>Geometries at equal depth compare equal, and jME sorts the bucket with a stable merge sort, so
 * they keep the order they were attached in.
 */
public final class DepthOrderedGeometryComparator implements GeometryComparator {
    /**
     * Ignored: the depths being compared are already world-space, and a parallel-projection 2D camera
     * looking down -Z adds nothing to them.
     */
    @Override
    public void setCamera(final Camera camera) {
    }

    @Override
    public int compare(
        final Geometry first,
        final Geometry second
    ) {
        // Ascending: the smallest depth is the farthest from a camera placed at +Z, and a bucket is
        // drawn in list order, so the farthest must come first for back-to-front blending.
        return Float.compare(
            first.getWorldTranslation().z,
            second.getWorldTranslation().z
        );
    }
}
