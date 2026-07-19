package house.x1337.app.smb3.model.game.collision;

import house.x1337.app.smb3.model.game.Offset;

/**
 * A pair of vertical and horizontal collision probe locations for a given
 * size/direction combination. Each {@link ProbeLocation} holds two named
 * {@link Offset} points representing the probe positions.
 */
public record CollisionProbe(
    ProbeLocation vertical,
    ProbeLocation horizontal) {
}
