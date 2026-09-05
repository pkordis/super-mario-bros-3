package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@RequiredArgsConstructor
public enum PlayerMode {
    NORMAL(true),
    RACCOON(true),
    SHRUNK(false),
    TANOOKI(true);

    @Accessors(fluent = true)
    private final boolean isLarge;

    public boolean isSmall() {
        return !isLarge;
    }

    /**
     * Whether this suit carries the raccoon/tanooki tail — the only modes that
     * perform tail wag, powered-flight fall control and the tail attack. NORMAL
     * (big Mario) is large but tailless, so it mirrors SHRUNK for airborne
     * behaviour while mirroring RACCOON on the ground.
     */
    public boolean hasTail() {
        return this == RACCOON || this == TANOOKI;
    }
}
