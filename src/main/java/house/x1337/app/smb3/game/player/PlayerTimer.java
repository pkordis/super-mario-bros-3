package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.event.GameEventBusAware;
import house.x1337.app.smb3.model.event.PlayerTimerCleared;
import house.x1337.app.smb3.model.event.PlayerTimerExpired;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;

@Data
@Slf4j
public final class PlayerTimer implements GameEventBusAware {
    private static final int DEFAULT_INITIAL_TIME = 300;

    private boolean active;
    private boolean paused;
    private int frameTick;
    private int initialTime = DEFAULT_INITIAL_TIME;
    private int time;

    public void start() {
        if (!active) {
            if (time == 0) {
                time = initialTime;
            }
            active = true;
            paused = false;
            frameTick = 0;
        }
    }

    public void pause() {
        if (active) {
            active = false;
            paused = true;
        }
    }

    public boolean isShown() {
        return active || paused;
    }

    public void clear() {
        active = false;
        paused = false;
        time = 0;
        frameTick = 0;
        publish(new PlayerTimerCleared());
    }

    public boolean tick() {
        if (!active) {
            return false;
        }

        frameTick++;
        if (frameTick >= SIMULATION_FPS) {
            frameTick = 0;
            time--;

            if (time <= 0) {
                time = 0;
                active = false;
                publish(new PlayerTimerExpired());
            }
            log.debug("Player time left: {}", time);
            return true;
        }
        return false;
    }
}
