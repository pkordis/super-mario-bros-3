package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.GameConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlayerAppearance {
    SMALL(12, 15);

    private final int width;
    private final int height;

    public int getWidth() {
        return width * GameConstants.TILE_SCALE;
    }

    public int getHeight() {
        return height * GameConstants.TILE_SCALE;
    }
}
