package house.x1337.app.smb3.game.motion.pop;

public interface PopMotions {
    static BlockCoinPopMotion blockCoin() {
        return new BlockCoinPopMotion();
    }

    static DeceleratingRisePopMotion deceleratingRise(
        final int stageTicks,
        final int stages
    ) {
        return new DeceleratingRisePopMotion(stageTicks, stages);
    }

    static PoppedOutCoinPopMotion poppedOutCoin() {
        return new PoppedOutCoinPopMotion();
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
