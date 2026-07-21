package house.x1337.app.smb3.model.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerIdentityType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Getter
@Prototype
@RequiredArgsConstructor
public class PlayerIdentity {
    private PlayerIdentityType type;

    public static PlayerIdentity fromType(final PlayerIdentityType type) {
        final PlayerIdentity playerIdentity = getBean(PlayerIdentity.class);
        playerIdentity.type = type;
        return playerIdentity;
    }

    public String getAnimationFramesPath() {
        return switch (type) {
            case MARIO -> "mario";
            case LUIGI -> "luigi";
        };
    }
}
