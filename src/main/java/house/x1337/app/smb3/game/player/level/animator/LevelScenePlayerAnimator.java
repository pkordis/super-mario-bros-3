package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.player.PlayerIdentityAware;
import house.x1337.app.smb3.game.player.PlayerAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;

import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;

public interface LevelScenePlayerAnimator<A extends PlayerAnimatorAssets>
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

    default Texture loadSprite(final String filename) {
        final Texture texture = getAssetManager().loadTexture(getFramesParentContext() + filename);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }

    void setAssets(final A animatorAssets);
    void update(LevelScenePlayer levelScenePlayer);
}
