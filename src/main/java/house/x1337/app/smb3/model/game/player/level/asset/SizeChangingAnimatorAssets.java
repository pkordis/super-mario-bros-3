package house.x1337.app.smb3.model.game.player.level.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.SizeChangingAnimator;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.loader.PlayerAnimatorAssetsLoader;

import static java.lang.Math.clamp;

public record SizeChangingAnimatorAssets(
    Texture[] sizeChanging
) implements PlayerAnimatorAssets {
    /**
     * The ROM's {@code Player_GrowFrames} table, indexed by {@code Player_Grow >> 2}. Entries point into
     * {@link #sizeChanging} — {@code 0} = still shrunk, {@code 1} = mid-transition, {@code 2} = still
     * normal — so read forwards (grow) the sequence settles on normal, and read backwards (shrink) it
     * settles on shrunk.
     */
    public static final int[] TRANSITION_FRAMES = {2, 1, 2, 1, 2, 1, 0, 1, 0, 1, 0, 1};

    private static final int TRANSITION_FRAME_SHIFT = 2;

    /**
     * Resolves the sprite index for the current counter value and direction, mirroring the ROM's draw
     * routine (prg029 @ PRG029_D224): {@code X = Player_Grow >> 2} for a grow, and
     * {@code X = $0B - (Player_Grow >> 2)} for a shrink (@ PRG029_D22E, taken whenever the suit is
     * small). The one counter and one table serve both directions; only this index is reversed.
     *
     * @param growShrinkCounter the shared {@code Player_Grow} countdown, {@code $2f} down to {@code 0}
     * @param shrinking         {@code true} for Normal→Shrunk, {@code false} for Shrunk→Normal
     * @return an index into {@link #sizeChanging}
     */
    public static int frameIndexFor(final int growShrinkCounter, final boolean shrinking) {
        final int step = clamp(growShrinkCounter >> TRANSITION_FRAME_SHIFT, 0, TRANSITION_FRAMES.length - 1);
        final int tableIndex = shrinking ? TRANSITION_FRAMES.length - 1 - step : step;
        return TRANSITION_FRAMES[tableIndex];
    }

    public static void loadFor(final SizeChangingAnimator animator) {
        final SizeChangingAnimatorAssets assets = PlayerAnimatorAssetsLoader.load(
            SizeChangingAnimatorAssets.class,
            animator
        );
        animator.setAssets(assets);
    }
}
