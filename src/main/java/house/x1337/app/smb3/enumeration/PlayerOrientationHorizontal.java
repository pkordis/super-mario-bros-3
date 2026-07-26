package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlayerOrientationHorizontal {
    LEFT("Left"),
    RIGHT("Right");

    private final String label;

    public final PlayerOrientationHorizontal oppositeIf(final boolean flipped) {
        if (!flipped) {
            return this;
        }
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
