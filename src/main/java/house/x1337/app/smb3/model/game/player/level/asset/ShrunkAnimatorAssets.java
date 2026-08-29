package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.ShrunkAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.player.level.dimension.ShrunkDimensions;

public record ShrunkAnimatorAssets(
    Texture stillTexture,
    Texture walkTexture,
    Texture skidTexture,
    Texture jumpTexture,
    Texture fastJumpTexture,
    Texture[] walkFrameTextures,
    Texture[] runFrameTextures
) implements PlayerAnimatorAssets, ShrunkDimensions {
    private static final int[] WALK_OR_RUN_FRAME_SEQUENCE = {0, 1, 0, 1};

    public static void loadFor(final ShrunkAnimator animator) {
        final ShrunkAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            ShrunkAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
        animator.setSpecifications(LevelScenePlayerAnimatorSpecifications
            .builder()
            .quadWidth(QUAD_WIDTH)
            .quadHeight(QUAD_HEIGHT)
            .tailOffset(0f)
            .walkFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .runFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .build()
        );
    }
}
