package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopa;
import house.x1337.app.smb3.model.game.enemy.asset.KoopaParaTroopaAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Singleton
@RequiredArgsConstructor
public final class KoopaParaTroopaAnimator implements EnemyAnimator<KoopaParaTroopaAnimatorAssets, KoopaParaTroopa> {
    public static final int FLIPPED_WING = 0;
    public static final int UNFLIPPED_WING = 1;

    private final String framesPath = "koopa/paratroopa";
    private final Class<KoopaParaTroopaAnimatorAssets> assetsType = KoopaParaTroopaAnimatorAssets.class;
    private final Map<EnemyMode, KoopaParaTroopaAnimatorAssets> assetsByMode = new HashMap<>();

    @Override
    public Texture frameTexture(final KoopaParaTroopa koopaParaTroopa) {
        return assetsFor(koopaParaTroopa).walk()[koopaParaTroopa.getWalkFrameIndex()];
    }

    public int walkFrameCount(final KoopaParaTroopa koopaParaTroopa) {
        return assetsFor(koopaParaTroopa).walk().length;
    }

    public Geometry buildWingSprite(final KoopaParaTroopa koopaParaTroopa) {
        return buildWingQuad(koopaParaTroopa, wingTexture(koopaParaTroopa));
    }

    public void applyWingFrame(final KoopaParaTroopa koopaParaTroopa) {
        applyWingTexture(koopaParaTroopa.getWingGeometry(), wingTexture(koopaParaTroopa));
    }

    private Texture wingTexture(final KoopaParaTroopa koopaParaTroopa) {
        final KoopaParaTroopaAnimatorAssets assets = assetsFor(koopaParaTroopa);
        final Texture[] pair = koopaParaTroopa.isWingUp() ? assets.wingUp() : assets.wingDown();
        return pair[koopaParaTroopa.isFacingRight() ? FLIPPED_WING : UNFLIPPED_WING];
    }
}
