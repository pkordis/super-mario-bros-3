package house.x1337.app.smb3.game.motion.pop;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import static lombok.AccessLevel.PACKAGE;

@Data
@Accessors(fluent = true)
@AllArgsConstructor(access = PACKAGE)
public final class SpinningPopMotion implements PopMotion {
    private final PopMotion motion;
    private final int frames;
    private final int ticksPerFrame;
    private final int phaseTicks;

    @Override
    public int durationTicks() {
        return motion.durationTicks();
    }

    @Override
    public int verticalOffsetAt(final int tick) {
        return motion.verticalOffsetAt(tick);
    }

    @Override
    public int textureIndexAt(final int tick) {
        return (tick + phaseTicks) / ticksPerFrame % frames;
    }
}
