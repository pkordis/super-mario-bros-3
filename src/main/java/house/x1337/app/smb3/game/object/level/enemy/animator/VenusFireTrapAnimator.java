package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.VenusFireTrap;
import house.x1337.app.smb3.model.game.enemy.asset.VenusFireTrapAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;

/**
 * Builds the fire plant's head and stem. The art faces left and looks down; {@link VenusFireTrap} mirrors
 * the head quad toward the player with negative scales, which {@link EnemyAnimator#buildSprite} allows by
 * turning face culling off.
 */
@Getter
@Singleton
@RequiredArgsConstructor
public final class VenusFireTrapAnimator
    implements
        PiranhaPlantFamilyAnimator<VenusFireTrapAnimatorAssets, VenusFireTrap> {
    private final String framesPath = "piranha/venus";
    private final Class<VenusFireTrapAnimatorAssets> assetsType = VenusFireTrapAnimatorAssets.class;
    private final Map<EnemyMode, VenusFireTrapAnimatorAssets> assetsByMode = new HashMap<>();

    @Override
    public Texture frameTexture(final VenusFireTrap venusFireTrap) {
        return assetsFor(venusFireTrap).head()[venusFireTrap.getHeadFrameIndex()];
    }

    @Override
    public Geometry buildSprite(final VenusFireTrap venusFireTrap) {
        final Geometry head = PiranhaPlantFamilyAnimator.super.buildSprite(venusFireTrap);
        head.setQueueBucket(Transparent);
        return head;
    }

    public Geometry buildStemSprite(final VenusFireTrap venusFireTrap) {
        final Geometry stem = fromTexture(
            venusFireTrap.getAssetManager(),
            assetsFor(venusFireTrap).stem(),
            venusFireTrap.getSpriteDimensions()
        );
        stem.setQueueBucket(Transparent);
        return stem;
    }
}
