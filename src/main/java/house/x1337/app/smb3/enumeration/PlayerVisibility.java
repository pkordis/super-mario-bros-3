package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlayerVisibility {
    BACKGROUND(0.015F),
    FOREGROUND(0.1F);

    private final float playerZ;

    public PlayerVisibility opposite() {
        if (this == BACKGROUND) {
            return FOREGROUND;
        }
        return BACKGROUND;
    }
}
