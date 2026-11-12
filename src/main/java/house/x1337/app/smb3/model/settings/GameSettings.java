package house.x1337.app.smb3.model.settings;

import house.x1337.app.smb3.enumeration.TimerPace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static house.x1337.app.smb3.enumeration.TimerPace.REAL_TIME;

/**
 * Every generic game setting, one field each. A field's initial value is that setting's default, used when the saved
 * settings do not override it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public final class GameSettings {
    private TimerPace timerPace = REAL_TIME;

    /** @return a copy, so a caller can change it without touching these settings */
    public GameSettings copy() {
        return new GameSettings(timerPace);
    }
}
