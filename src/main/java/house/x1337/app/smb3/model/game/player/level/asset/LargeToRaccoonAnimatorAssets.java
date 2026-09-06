package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.LargeToRaccoonAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;

/**
 * Asset bundle for the large→Raccoon "poof" transition played when an already-big
 * (Super) player collects a Super Leaf (dasm {@code ObjHit_SuperLeaf}: sets
 * {@code Player_QueueSuit = $04} and the poof counter {@code Player_SuitLost = $17}).
 */
public record LargeToRaccoonAnimatorAssets(
    Texture[] poof
) implements PlayerAnimatorAssets {
    /**
     * Poof frame per 4-tick step of the countdown. dasm prg029
     * {@code Player_SuitLost_DoPoof} selects its pattern with
     * {@code (Player_SuitLost & $0C) >> 2} indexing {@code SuitLost_Poof_Patterns}
     * ({@code $47,$45,$43,$41}). Indexed here by {@code poofCounter >> 2} (steps
     * 5→0 as the counter drains from 23), it yields the visible order
     * big_dust → small_dust → big_puff → small_puff → big_dust → small_dust.
     * Values index {@link #poof}: 0=small_dust, 1=big_dust, 2=small_puff, 3=big_puff.
     */
    public static final int[] POOF_FRAMES = {0, 1, 2, 3, 0, 1};

    public static void loadFor(final LargeToRaccoonAnimator animator) {
        final LargeToRaccoonAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            LargeToRaccoonAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
    }
}
