package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.ParaGoomba;
import house.x1337.app.smb3.model.game.enemy.asset.ParaGoombaAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Singleton
@RequiredArgsConstructor
public final class ParaGoombaAnimator implements EnemyAnimator<ParaGoombaAnimatorAssets, ParaGoomba> {
    private final String framesPath = "paragoomba";
    private final Class<ParaGoombaAnimatorAssets> assetsType = ParaGoombaAnimatorAssets.class;
    private final Map<EnemyMode, ParaGoombaAnimatorAssets> assetsByMode = new HashMap<>();

    @Override
    public Texture frameTexture(final ParaGoomba paraGoomba) {
        return assetsFor(paraGoomba).walk()[paraGoomba.getWalkFrameIndex()];
    }

    public int walkFrameCount(final ParaGoomba paraGoomba) {
        return assetsFor(paraGoomba).walk().length;
    }

    public Geometry buildWingSprite(
        final ParaGoomba paraGoomba,
        final boolean leftWing
    ) {
        return buildWingQuad(paraGoomba, wingTexture(paraGoomba, leftWing));
    }

    public void applyCurrentWingFrames(final ParaGoomba paraGoomba) {
        applyWingTexture(paraGoomba.getLeftWingGeometry(), wingTexture(paraGoomba, true));
        applyWingTexture(paraGoomba.getRightWingGeometry(), wingTexture(paraGoomba, false));
    }

    private Texture wingTexture(
        final ParaGoomba paraGoomba,
        final boolean leftWing
    ) {
        final ParaGoombaAnimatorAssets assets = assetsFor(paraGoomba);
        final Texture[] pair = paraGoomba.isWingUp() ? assets.wingUp() : assets.wingDown();
        return pair[leftWing ? 0 : 1];
    }
}
