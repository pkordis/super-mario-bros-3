package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import static house.x1337.app.smb3.GameConstants.TILE_SCALE;

@Getter
@RequiredArgsConstructor
public enum PlayerMode {
    SHRUNK(false, 12, 15),
    NORMAL(true, 24, 15),
    RACOON(true, 24, 15),
    TANOOKI(true, 24, 15);

    @Accessors(fluent = true)
    private final boolean isLarge;
    private final int width;
    private final int height;

    public int getWidth() {
        return width * TILE_SCALE;
    }

    public int getHeight() {
        return height * TILE_SCALE;
    }

    public boolean isSmall() {
        return !isLarge;
    }
}
