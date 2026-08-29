package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.RaccoonAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions;

public record RaccoonAnimatorAssets(
    Texture stillTexture,
    Texture skidTexture,
    Texture duckTexture,
    Texture jumpTexture,
    Texture[] tailFallTextures,
    Texture[] tailFlyTextures,
    Texture[] tailAttackTextures,
    Texture[] tailAttackInAirTextures,
    Texture[] walkFrameTextures,
    Texture[] runFrameTextures
) implements PlayerAnimatorAssets, RaccoonDimensions {
    private static final int[] WALK_OR_RUN_FRAME_SEQUENCE = {0, 1, 2, 1};

    public static void loadFor(final RaccoonAnimator animator) {
        final RaccoonAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            RaccoonAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
        animator.setSpecifications(LevelScenePlayerAnimatorSpecifications
            .builder()
            .quadWidth(QUAD_WIDTH)
            .quadHeight(QUAD_HEIGHT)
            .tailOffset(TAIL_OFFSET)
            .walkFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .runFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .build()
        );
    }

    public Texture tailFlyTexture(final int tailFrame) {
        return tailFlyTextures[tailFrame];
    }

    public Texture tailFallTexture(final int tailFrame) {
        return tailFallTextures[tailFrame];
    }
}
