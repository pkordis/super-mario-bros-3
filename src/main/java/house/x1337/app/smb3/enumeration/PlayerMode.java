package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.util.EnumValuesMatcher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
public enum PlayerMode implements EnumValuesMatcher<PlayerMode> {
    NORMAL,
    RACCOON,
    SHRUNK,
    TANOOKI;

    @Getter(lazy = true)
    @Accessors(fluent = true)
    private final boolean isLarge = initIsLarge();

    @Getter(lazy = true)
    @Accessors(fluent = true)
    private final boolean isAdvanced = initIsAdvanced();

    @Getter(lazy = true)
    @Accessors(fluent = true)
    private final boolean hasTail = initHasTail();

    private boolean initIsLarge() {
        return this != SHRUNK;
    }

    private boolean initIsAdvanced() {
        return isNoneOf(SHRUNK, NORMAL);
    }

    public boolean isSmall() {
        return !isLarge();
    }

    public boolean initHasTail() {
        return isAnyOf(RACCOON, TANOOKI);
    }
}
