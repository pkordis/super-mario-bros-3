package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.LargeToRaccoonAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;

/**
 * Asset bundle for the large→Raccoon "poof" transition played when an already-big
 * (Super) player collects a Super Leaf (dasm {@code ObjHit_SuperLeaf}: sets
 * {@code Player_QueueSuit = $04} and the poof counter {@code Player_SuitLost = $17}).
 *
 * <p>{@link #poof} holds the shared poof sprites in ROM table order
 * ({@code SuitLost_Poof_Patterns} = {@code $47,$45,$43,$41}), loaded from
 * {@code sprites/effect/poof/} — the same set the switch-block puff uses. Frame selection lives in
 * {@link house.x1337.app.smb3.model.game.effect.PoofSequence}, so this record is now just the textures.
 */
public record LargeToRaccoonAnimatorAssets(
    Texture[] poof
) implements PlayerAnimatorAssets {
    public static void loadFor(final LargeToRaccoonAnimator animator) {
        final LargeToRaccoonAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            LargeToRaccoonAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
    }
}
