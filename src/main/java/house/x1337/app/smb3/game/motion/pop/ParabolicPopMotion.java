package house.x1337.app.smb3.game.motion.pop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import static java.lang.Math.clamp;
import static java.lang.Math.floorDiv;
import static lombok.AccessLevel.PACKAGE;

@Data
@Accessors(fluent = true)
@AllArgsConstructor(access = PACKAGE)
public final class ParabolicPopMotion implements PopMotion {
    private final int spawnOffset;
    private final int riseSpeed;
    private final int gravity;
    private final int precision;
    private final int durationTicks;

    @Override
    public int verticalOffsetAt(final int tick) {
        final int elapsed = clamp(tick, 0, durationTicks);
        final int travelled = 2 * riseSpeed * elapsed - gravity * elapsed * elapsed;
        return spawnOffset + floorDiv(travelled, 2 * precision);
    }

    public PopMotion spinning(
        final int frames,
        final int ticksPerFrame,
        final int phaseTicks
    ) {
        return PopMotions.spinning(
            this,
            frames,
            ticksPerFrame,
            phaseTicks
        );
    }
}
