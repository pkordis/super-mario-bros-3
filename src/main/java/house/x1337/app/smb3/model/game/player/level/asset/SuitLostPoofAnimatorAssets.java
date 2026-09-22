package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.SuitLostPoofAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;

/**
 * Asset bundle for the {@code Player_SuitLost} poof, played by every suit change that is not a size
 * change: an already-big player collecting a Super Leaf (dasm {@code ObjHit_SuperLeaf}, which sets
 * {@code Player_QueueSuit = $04}) and an advanced player taking a hit (dasm {@code Player_GetHurt},
 * which sets {@code Player_QueueSuit = $02}). Both seed the same counter, {@code Player_SuitLost = $17}.
 *
 * <p>{@link #poof} holds the shared poof sprites in ROM table order
 * ({@code SuitLost_Poof_Patterns} = {@code $47,$45,$43,$41}), loaded from
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
