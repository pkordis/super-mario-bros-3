package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.Goomba;
import house.x1337.app.smb3.model.game.enemy.asset.GoombaAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Singleton
@RequiredArgsConstructor
public final class GoombaAnimator implements EnemyAnimator<GoombaAnimatorAssets, Goomba> {
    private final String framesPath = "goomba";
    private final Class<GoombaAnimatorAssets> assetsType = GoombaAnimatorAssets.class;
    private final Map<EnemyMode, GoombaAnimatorAssets> assetsByMode = new HashMap<>();

    @Override
    public Texture frameTexture(final Goomba goomba) {
        return assetsFor(goomba).walkFrameTextures()[goomba.getWalkFrameIndex()];
    }

    public int walkFrameCount(final Goomba goomba) {
        return assetsFor(goomba).walkFrameTextures().length;
    }
}
