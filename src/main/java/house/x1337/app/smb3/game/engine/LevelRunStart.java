package house.x1337.app.smb3.game.engine;

import house.x1337.app.smb3.enumeration.PlayerMode;

import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;

public record LevelRunStart(
    PlayerMode mode,
    boolean restartTimer
) {
    public static final LevelRunStart FRESH = new LevelRunStart(SHRUNK, true);

    public static LevelRunStart carryOver(final PlayerMode mode) {
        return new LevelRunStart(mode, false);
    }
}
