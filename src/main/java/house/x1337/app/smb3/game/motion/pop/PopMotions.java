package house.x1337.app.smb3.game.motion.pop;

public interface PopMotions {
    static BlockCoinPopMotion blockCoin() {
        return new BlockCoinPopMotion();
    }

    static PoppedOutCoinPopMotion poppedOutCoin() {
        return new PoppedOutCoinPopMotion();
    }

    static ScoreRisePopMotion scoreRise(
        final int firstUpdateFrame,
        final int maxRises
    ) {
        return new ScoreRisePopMotion(firstUpdateFrame, maxRises);
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
