package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.KoopaTroopa;
import house.x1337.app.smb3.model.game.enemy.asset.KoopaTroopaAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Singleton
@RequiredArgsConstructor
public final class KoopaTroopaAnimator implements EnemyAnimator<KoopaTroopaAnimatorAssets, KoopaTroopa> {
    private final String framesPath = "koopa/troopa";
    private final Class<KoopaTroopaAnimatorAssets> assetsType = KoopaTroopaAnimatorAssets.class;
    private final Map<EnemyMode, KoopaTroopaAnimatorAssets> assetsByMode = new HashMap<>();

    @Override
    public Texture frameTexture(final KoopaTroopa koopaTroopa) {
        return assetsFor(koopaTroopa).walk()[koopaTroopa.getWalkFrameIndex()];
    }

    public int walkFrameCount(final KoopaTroopa koopaTroopa) {
        return assetsFor(koopaTroopa).walk().length;
    }
}
