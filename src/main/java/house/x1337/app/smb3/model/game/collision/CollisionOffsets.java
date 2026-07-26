package house.x1337.app.smb3.model.game.collision;

import static house.x1337.app.smb3.model.game.Offset.of;

/**
 * Pixel-level collision probe offsets ported from the SMB3 disassembly
 * <p>
 * Two {@link DirectionalProbes} instances - one per player size - provide
 * named access to the four directional {@link CollisionProbe} combos.
 */
public interface CollisionOffsets {
    DirectionalProbes LARGE_PROBES = new DirectionalProbes(
        new CollisionProbe(
            new ProbeLocation(of(0x08, 0x06), of(0x08, 0x06)),
            new ProbeLocation(of(0x0E, 0x1B), of(0x0E, 0x0E))
        ),
        new CollisionProbe(
            new ProbeLocation(of(0x08, 0x06), of(0x08, 0x06)),
            new ProbeLocation(of(0x01, 0x1B), of(0x01, 0x0E))
        ),
        new CollisionProbe(
            new ProbeLocation(of(0x04, 0x20), of(0x0B, 0x20)),
            new ProbeLocation(of(0x0E, 0x1B), of(0x0E, 0x0E))
        ),
        new CollisionProbe(
            new ProbeLocation(of(0x04, 0x20), of(0x0B, 0x20)),
            new ProbeLocation(of(0x01, 0x1B), of(0x01, 0x0E))
        )
    );

    DirectionalProbes SMALL_PROBES = new DirectionalProbes(
        new CollisionProbe(
            new ProbeLocation(of(0x08, 0x10), of(0x08, 0x10)),
            new ProbeLocation(of(0x0D, 0x1B), of(0x0D, 0x14))
        ),
        new CollisionProbe(
            new ProbeLocation(of(0x08, 0x10), of(0x08, 0x10)),
            new ProbeLocation(of(0x02, 0x1B), of(0x02, 0x14))
        ),
        new CollisionProbe(
            new ProbeLocation(of(0x04, 0x20), of(0x0B, 0x20)),
            new ProbeLocation(of(0x0D, 0x1B), of(0x0D, 0x14))
        ),
        new CollisionProbe(
            new ProbeLocation(of(0x04, 0x20), of(0x0B, 0x20)),
            new ProbeLocation(of(0x02, 0x1B), of(0x02, 0x14))
        )
    );
}
