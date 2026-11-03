package house.x1337.app.smb3.game.object.level.projectile;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.projectile.asset.PiranhaFireballAnimatorAssets;
import house.x1337.app.smb3.util.GameRenderer;
import lombok.RequiredArgsConstructor;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;

@Singleton
@RequiredArgsConstructor
public final class PiranhaFireballAnimator implements Animator, GameRenderer {
    public static final String FRAMES_CONTEXT = "sprites/projectile/piranha_fireball/";

    private PiranhaFireballAnimatorAssets assets;

    public Geometry buildSprite(final PiranhaFireball fireball) {
        final Geometry geometry = fromTexture(
            fireball.getAssetManager(),
            frameTexture(fireball),
            fireball.getSpriteDimensions()
        );
        geometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        return geometry;
    }

    public void applyCurrentFrame(final PiranhaFireball fireball) {
        final Geometry spriteGeometry = fireball.getSpriteGeometry();
        if (spriteGeometry == null) {
            return;
        }
        spriteGeometry.getMaterial().setTexture("ColorMap", frameTexture(fireball));
    }

    public Texture frameTexture(final PiranhaFireball fireball) {
        return assetsFor(fireball).frames()[fireball.patternIndex()];
    }

    @Override
    public void reset() {
        assets = null;
    }

    private PiranhaFireballAnimatorAssets assetsFor(final PiranhaFireball fireball) {
        if (assets == null) {
            final AssetManager assetManager = fireball.getAssetManager();
            assets = AnimatorAssetsLoader.load(
                PiranhaFireballAnimatorAssets.class,
                FRAMES_CONTEXT,
                filename -> loadSprite(assetManager, FRAMES_CONTEXT + filename)
            );
        }
        return assets;
    }

    private static Texture loadSprite(final AssetManager assetManager, final String path) {
        final Texture texture = assetManager.loadTexture(path);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }
}
