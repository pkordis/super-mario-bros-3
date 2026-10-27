package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.KoopaTroopa;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.enemy.asset.KoopaTroopaAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static house.x1337.app.smb3.model.game.Dimensions.fullTile;
import static house.x1337.app.smb3.model.game.Dimensions.halfTileWidth;

@Getter
@Singleton
@RequiredArgsConstructor
public final class KoopaTroopaAnimator implements EnemyAnimator<KoopaTroopaAnimatorAssets, KoopaTroopa> {
    public static final Dimensions SHELL_DIMENSIONS = fullTile("KoopaTroopaShell");
    public static final Dimensions SHELL_FOOT_DIMENSIONS = halfTileWidth("KoopaTroopaShellFoot");

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

    public Geometry buildShellSprite(final KoopaTroopa koopaTroopa) {
        return buildFlippableSprite(koopaTroopa, assetsFor(koopaTroopa).shell(), SHELL_DIMENSIONS);
    }

    public void applyShellFrame(final KoopaTroopa koopaTroopa) {
        final Geometry shellGeometry = koopaTroopa.getShellGeometry();
        if (shellGeometry == null) {
            return;
        }
        final KoopaTroopaAnimatorAssets assets = assetsFor(koopaTroopa);
        final Texture[] spin = assets.shellSpin();
        final Texture texture = koopaTroopa.isRolling()
            ? spin[koopaTroopa.getSpinBeat() % spin.length]
            : assets.shell();
        shellGeometry.getMaterial().setTexture("ColorMap", texture);
    }

    public Geometry buildShellFootSprite(
        final KoopaTroopa koopaTroopa,
        final boolean leftFoot
    ) {
        final Texture texture = assetsFor(koopaTroopa).shellFeet()[leftFoot ? 0 : 1];
        return buildFlippableSprite(koopaTroopa, texture, SHELL_FOOT_DIMENSIONS);
    }

    private Geometry buildFlippableSprite(
        final KoopaTroopa koopaTroopa,
        final Texture texture,
        final Dimensions dimensions
    ) {
        final Geometry geometry = fromTexture(koopaTroopa.getAssetManager(), texture, dimensions);
        geometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        return geometry;
    }
}
