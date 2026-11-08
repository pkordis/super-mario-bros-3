package house.x1337.app.smb3.game.engine;

import lombok.Getter;

@Getter
public final class LevelFrameCounter {
    private int ticks;

    public void tick() {
        ticks++;
    }

    public void reset() {
        ticks = 0;
    }
}
