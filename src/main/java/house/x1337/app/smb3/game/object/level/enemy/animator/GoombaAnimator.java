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

    /**
     * The Goomba's sprite for this tick: the flattened frame once stomped, otherwise the current step of
     * the walk cycle (dasm {@code ObjState_Squashed} substitutes frame 3 for the whole squish).
     */
    @Override
    public Texture frameTexture(final Goomba goomba) {
        final GoombaAnimatorAssets assets = assetsFor(goomba);
        return goomba.isSquished()
            ? assets.squished()
            : assets.walk()[goomba.getWalkFrameIndex()];
    }

    public int walkFrameCount(final Goomba goomba) {
        return assetsFor(goomba).walk().length;
    }
}
