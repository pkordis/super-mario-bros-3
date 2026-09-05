package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.ShrunkToNormalAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;

public record ShrunkToNormalAnimatorAssets(
    Texture[] growing
) implements PlayerAnimatorAssets {
    public static final int[] GROW_FRAMES = {2, 1, 2, 1, 2, 1, 0, 1, 0, 1, 0, 1};

    public static void loadFor(final ShrunkToNormalAnimator animator) {
        final ShrunkToNormalAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            ShrunkToNormalAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
    }
}
