package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.Animator;
import house.x1337.app.smb3.game.object.level.enemy.EnemyLevelObject;
import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.enemy.EnemyAnimatorAssets;
import house.x1337.app.smb3.util.GameRenderer;

import java.util.Map;

import static com.jme3.material.RenderState.FaceCullMode.Off;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;

public interface EnemyAnimator<A extends EnemyAnimatorAssets, E extends EnemyLevelObject>
    extends
        Animator,
        GameRenderer {
    String SPRITES_CONTEXT = "sprites/enemy/%s/%s/";

    String getFramesPath();
    Class<A> getAssetsType();
    Map<EnemyMode, A> getAssetsByMode();
    Texture frameTexture(E enemy);

    default Geometry buildSprite(final E enemy) {
        final Geometry geometry = fromTexture(
            enemy.getAssetManager(),
            frameTexture(enemy),
            enemy.getSpriteDimensions()
        );
        // Enemies are mirrored by negative X scale when a mode needs it, which reverses the winding.
        geometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        return geometry;
    }

    default void applyCurrentFrame(final E enemy) {
        final Geometry spriteGeometry = enemy.getSpriteGeometry();
        if (spriteGeometry == null) {
            return;
        }
        spriteGeometry.getMaterial().setTexture("ColorMap", frameTexture(enemy));
    }

    default A assetsFor(final E enemy) {
        return getAssetsByMode().computeIfAbsent(
            enemy.getMode(),
            mode -> loadAssets(mode, enemy.getAssetManager())
        );
    }

    @Override
    default void reset() {
        getAssetsByMode().clear();
    }

    private A loadAssets(final EnemyMode mode, final AssetManager assetManager) {
        final String framesParentContext = SPRITES_CONTEXT.formatted(getFramesPath(), mode.getFramesFolder());
        return AnimatorAssetsLoader.load(
            getAssetsType(),
            framesParentContext,
            filename -> loadSprite(assetManager, framesParentContext + filename)
        );
    }

    private Texture loadSprite(final AssetManager assetManager, final String path) {
        final Texture texture = assetManager.loadTexture(path);
        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);
        return texture;
    }
}
