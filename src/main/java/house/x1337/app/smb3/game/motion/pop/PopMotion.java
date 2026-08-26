package house.x1337.app.smb3.game.motion.pop;

public interface PopMotion {
    int durationTicks();
    int verticalOffsetAt(int tick);

    default int textureIndexAt(final int tick) {
        return 0;
    }
}
