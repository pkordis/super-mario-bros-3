package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.SuitLostPoofAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;

/**
 * Asset bundle for the suit-lost poof, played by every suit change that is not a size change: an
 * already-big player collecting a Super Leaf and an advanced player taking a hit. Both start the same
 * poof counter at {@code 23}.
 *
 * <p>{@link #poof} holds the shared poof sprites in frame-table order, loaded from
 * {@code sprites/effect/poof/} — the same set the switch-block puff uses. Frame selection lives in
 * {@link house.x1337.app.smb3.model.game.effect.PoofSequence}, so this record is now just the textures.
 */
public record SuitLostPoofAnimatorAssets(
    Texture[] poof
) implements PlayerAnimatorAssets {
    public static void loadFor(final SuitLostPoofAnimator animator) {
        final SuitLostPoofAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            SuitLostPoofAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
    }
}
