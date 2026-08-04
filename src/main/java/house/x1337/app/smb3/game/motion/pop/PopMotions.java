package house.x1337.app.smb3.game.motion.pop;

public interface PopMotions {
    static ParabolicPopMotion parabolic(
        final int spawnOffset,
        final int riseSpeed,
        final int gravity,
        final int precision,
        final int durationTicks
    ) {
        return new ParabolicPopMotion(spawnOffset, riseSpeed, gravity, precision, durationTicks);
    }

    static DeceleratingRisePopMotion deceleratingRise(
        final int stageTicks,
        final int stages
    ) {
        return new DeceleratingRisePopMotion(stageTicks, stages);
    }

    static SpinningPopMotion spinning(
        final PopMotion motion,
        final int frames,
        final int ticksPerFrame,
        final int phaseTicks
    ) {
        return new SpinningPopMotion(motion, frames, ticksPerFrame, phaseTicks);
    }
}
