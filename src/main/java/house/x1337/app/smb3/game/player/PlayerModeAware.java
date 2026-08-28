package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.enumeration.PlayerMode;

public interface PlayerModeAware {
    void setMode(PlayerMode playerMode);
    PlayerMode getMode();

    default boolean isSmall() {
        return getMode().isSmall();
    }

    default boolean isLarge() {
        return getMode().isLarge();
    }
}
