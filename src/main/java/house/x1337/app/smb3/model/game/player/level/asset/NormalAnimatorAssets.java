package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.NormalAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssetsMoving;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.player.level.dimension.NormalDimensions;

public record NormalAnimatorAssets(
    Texture still,
    Texture skid,
    Texture duck,
    Texture faceForward,
    Texture[] hold,
    Texture jump,
    Texture kick,
    Texture fastJump,
    Texture[] walk,
    Texture[] run
) implements PlayerAnimatorAssetsMoving, NormalDimensions {
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
