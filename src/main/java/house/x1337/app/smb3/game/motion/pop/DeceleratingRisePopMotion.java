package house.x1337.app.smb3.game.motion.pop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import static java.lang.Math.clamp;
import static lombok.AccessLevel.PACKAGE;

@Data
@Accessors(fluent = true)
@AllArgsConstructor(access = PACKAGE)
public final class DeceleratingRisePopMotion implements PopMotion {
    private final int stageTicks;
    private final int stages;

    @Override
    public int durationTicks() {
        return stageTicks * stages;
    }

    @Override
    public int verticalOffsetAt(final int tick) {
        final int elapsed = clamp(tick, 0, durationTicks());
        int offset = -4;
        for (int stage = 0; stage < stages; stage++) {
            final int ticksInStage = clamp(elapsed - (long) stage * stageTicks, 0, stageTicks);
            offset += ticksInStage / (1 << stage);
        }
        return offset;
    }
}
