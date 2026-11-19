package house.x1337.app.smb3.model.game.collision;

import static house.x1337.app.smb3.model.game.Coordinates.of;

/**
 * Pixel-level collision probe offsets for the player.
 * <p>
 * One {@link DirectionalProbes} instance per player size provides named access to the four directional
 * {@link CollisionProbe} combos. The sloped sets, used near a ramp, raise the in-front probes so the ground rising
 * ahead on a slope is not taken for a wall.
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

    DirectionalProbes LARGE_SLOPED_PROBES = new DirectionalProbes(
        new CollisionProbe(
            new ProbeLocation(of(8, 6), of(8, 6)),
            new ProbeLocation(of(14, 24), of(14, 12))
        ),
        new CollisionProbe(
            new ProbeLocation(of(8, 6), of(8, 6)),
            new ProbeLocation(of(1, 24), of(1, 12))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(14, 24), of(14, 12))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(1, 24), of(1, 12))
        )
    );

    DirectionalProbes SMALL_SLOPED_PROBES = new DirectionalProbes(
        new CollisionProbe(
            new ProbeLocation(of(8, 16), of(8, 16)),
            new ProbeLocation(of(13, 24), of(13, 23))
        ),
        new CollisionProbe(
            new ProbeLocation(of(8, 16), of(8, 16)),
            new ProbeLocation(of(2, 24), of(2, 23))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(13, 24), of(13, 23))
        ),
        new CollisionProbe(
            new ProbeLocation(of(4, 32), of(11, 32)),
            new ProbeLocation(of(2, 24), of(2, 23))
        )
    );
}
