package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.PlayerIdentityAware;
import house.x1337.app.smb3.game.player.PlayerAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;

public interface LevelScenePlayerAnimator
    extends
        GameEngineAware,
        PlayerAnimator,
        PlayerIdentityAware {
    PlayerMode getPlayerMode();

    @Override
    default String getFramesParentContext() {
        return "sprites/player/%s/level/%s/"
            .formatted(
                getIdentity().getAnimationFramesPath(),
                getPlayerMode().name().toLowerCase()
            );
    }

    void update(LevelScenePlayer levelScenePlayer);
}
