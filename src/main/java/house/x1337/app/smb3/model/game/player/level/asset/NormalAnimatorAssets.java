package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.NormalAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions;

/**
 * Asset bundle for "normal" (big) Mario. It is the raccoon ground set with the
 * tail textures removed (still, skid, duck, jump, and the 3-frame walk/run
 * cycles), plus a small-Mario-style {@code fastJumpTexture} for the airborne
 * frames — big Mario mirrors SHRUNK in the air (a single jump/fall frame, or
 * the "fast" frame while the full-P launch boost is active) and RACCOON on the
 * ground.
 */
public record NormalAnimatorAssets(
    Texture stillTexture,
    Texture skidTexture,
    Texture duckTexture,
    Texture jumpTexture,
    Texture fastJumpTexture,
    Texture[] walkFrameTextures,
    Texture[] runFrameTextures
) implements PlayerAnimatorAssets, NormalDimensions {
    private static final int[] WALK_OR_RUN_FRAME_SEQUENCE = {0, 1, 2, 1};

    public static void loadFor(final NormalAnimator animator) {
        final NormalAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            NormalAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
        animator.setSpecifications(LevelScenePlayerAnimatorSpecifications
            .builder()
            .quadWidth(QUAD_WIDTH)
            .quadHeight(QUAD_HEIGHT)
            .rightPadding(RIGHT_PADDING)
            .walkFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .runFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .build()
        );
    }
}
