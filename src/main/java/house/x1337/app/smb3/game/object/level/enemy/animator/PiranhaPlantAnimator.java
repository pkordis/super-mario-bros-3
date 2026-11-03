package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.PiranhaPlant;
import house.x1337.app.smb3.model.game.enemy.asset.PiranhaPlantAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.jme3.renderer.queue.RenderQueue.Bucket.Transparent;

@Getter
@Singleton
@RequiredArgsConstructor
public final class PiranhaPlantAnimator
    implements
        PiranhaPlantFamilyAnimator<PiranhaPlantAnimatorAssets, PiranhaPlant> {
    private final String framesPath = "piranha/plant";
    private final Class<PiranhaPlantAnimatorAssets> assetsType = PiranhaPlantAnimatorAssets.class;
    private final Map<EnemyMode, PiranhaPlantAnimatorAssets> assetsByMode = new HashMap<>();

    @Override
    public Texture frameTexture(final PiranhaPlant piranhaPlant) {
        return assetsFor(piranhaPlant).head()[piranhaPlant.getHeadFrameIndex()];
    }

    @Override
    public Geometry buildSprite(final PiranhaPlant piranhaPlant) {
        final Geometry head = PiranhaPlantFamilyAnimator.super.buildSprite(piranhaPlant);
        head.setQueueBucket(Transparent);
        return head;
    }

    public Geometry buildStemSprite(final PiranhaPlant piranhaPlant) {
        final Geometry stem = fromTexture(
            piranhaPlant.getAssetManager(),
            assetsFor(piranhaPlant).stem(),
            piranhaPlant.getSpriteDimensions()
        );
        stem.setQueueBucket(Transparent);
        return stem;
    }
}
