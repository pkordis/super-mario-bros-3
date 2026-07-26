package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlayerOrientationVertical {
    DOWN("Down"),
    SUSTAINED("Sustained"),
    UP("Up");

    private final String label;

    public final PlayerOrientationVertical oppositeIf(final boolean flipped) {
        if (!flipped) {
            return this;
        }
        return switch (this) {
            case DOWN -> UP;
            case SUSTAINED -> SUSTAINED;
            case UP -> DOWN;
        };
    }
}
