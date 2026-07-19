package house.x1337.app.smb3.model.game.collision;

import house.x1337.app.smb3.model.game.Offset;

/**
 * A pair of probe offsets for a single axis (vertical or horizontal).
 * Named fields replace the raw {@code Offset[]} array.
 *
 * <ul>
 *   <li>For vertical probes: {@code first} = ground-left, {@code second} = ground-right</li>
 *   <li>For horizontal probes: {@code first} = in-front-lower, {@code second} = in-front-upper</li>
 * </ul>
 */
public record ProbeLocation(Offset first, Offset second) {
}
