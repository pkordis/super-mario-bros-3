package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssetsMoving;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Prototype
@RequiredArgsConstructor
public class EmptyAnimator implements LevelScenePlayerAnimator<PlayerAnimatorAssetsMoving> {
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    @Override
    public PlayerMode getPlayerMode() {
        return null;
    }

    @Override
    public void setAssets(final PlayerAnimatorAssetsMoving animatorAssets) {
    }

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
    }
}
