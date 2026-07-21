package house.x1337.app.smb3.enumeration;

import house.x1337.app.smb3.model.game.player.PlayerIdentity;

public enum PlayerIdentityType {
    LUIGI,
    MARIO;

    public PlayerIdentity identity() {
        return PlayerIdentity.fromType(this);
    }
}
