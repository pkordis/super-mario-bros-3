package house.x1337.app.smb3.game.object.level.reward.core;

public final class PowerUpCollectProtection {
    public static final int FRAMES = 16;

    private int framesRemaining = FRAMES;

    public void tick() {
        if (framesRemaining > 0) {
            framesRemaining--;
        }
    }

    public boolean isElapsed() {
        return framesRemaining == 0;
    }
}
