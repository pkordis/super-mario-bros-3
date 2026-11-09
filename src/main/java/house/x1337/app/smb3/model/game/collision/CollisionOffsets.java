package house.x1337.app.smb3.model.game.collision;

import static house.x1337.app.smb3.model.game.Coordinates.of;

/**
 * Pixel-level collision probe offsets for the player.
 * <p>
 * Two {@link DirectionalProbes} instances - one per player size - provide
 * named access to the four directional {@link CollisionProbe} combos.
 */
public interface CollisionOffsets {
    DirectionalProbes LARGE_PROBES = new DirectionalProbes(
        new CollisionProbe(
            new ProbeLocation(of(8, 6), of(8, 6)),
            new ProbeLocation(of(14, 27), of(14, 14))
        ),
        new CollisionProbe(
            new ProbeLocation(of(8, 6), of(8, 6)),
            new ProbeLocation(of(1, 27), of(1, 14))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(14, 27), of(14, 14))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(1, 27), of(1, 14))
        )
    );

    DirectionalProbes SMALL_PROBES = new DirectionalProbes(
        new CollisionProbe(
            new ProbeLocation(of(8, 16), of(8, 16)),
            new ProbeLocation(of(13, 27), of(13, 20))
        ),
        new CollisionProbe(
            new ProbeLocation(of(8, 16), of(8, 16)),
            new ProbeLocation(of(2, 27), of(2, 20))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(13, 27), of(13, 20))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(2, 27), of(2, 20))
        )
    );
}
