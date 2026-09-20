package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.RaccoonAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssetsMoving;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions;

public record RaccoonAnimatorAssets(
    Texture still,
    Texture skid,
    Texture duck,
    Texture jump,
    Texture[] tailFall,
    Texture[] tailFly,
    Texture[] tailAttack,
    Texture[] tailAttackInAir,
    Texture[] walk,
    Texture[] run
) implements PlayerAnimatorAssetsMoving, RaccoonDimensions {
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
            .rightPadding(TAIL_OFFSET)
            .walkFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .runFrameSequence(WALK_OR_RUN_FRAME_SEQUENCE)
            .build()
        );
    }

    public Texture tailFly(final int tailFrame) {
        return tailFly[tailFrame];
    }

    public Texture tailFall(final int tailFrame) {
        return tailFall[tailFrame];
    }
}
