package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@RequiredArgsConstructor
public enum PlayerMode {
    SHRUNK(false),
    NORMAL(true),
    RACCOON(true),
    TANOOKI(true);

    @Accessors(fluent = true)
    private final boolean isLarge;

    public boolean isSmall() {
        return !isLarge;
    }
}
