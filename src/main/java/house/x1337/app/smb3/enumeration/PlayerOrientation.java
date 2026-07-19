package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlayerOrientation {
    LEFT("Left"),
    RIGHT("Right");

    private final String label;

    public final PlayerOrientation opposite() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
