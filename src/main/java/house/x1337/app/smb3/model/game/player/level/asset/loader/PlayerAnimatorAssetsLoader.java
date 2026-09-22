package house.x1337.app.smb3.model.game.player.level.asset.loader;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimator;
import house.x1337.app.smb3.model.game.asset.loader.AnimatorAssetsLoader;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssets;
import lombok.NoArgsConstructor;

import java.util.function.Function;

import static lombok.AccessLevel.PRIVATE;

/**
 * Loads a player animator's {@code assets.json} — the suit folder resolved from the animator's own
 * {@code getFramesParentContext()}, with its {@code loadSprite} as the texture loader. The parsing
 * itself is the shared {@link AnimatorAssetsLoader}, which the enemy animators use too.
 */
@NoArgsConstructor(access = PRIVATE)
public final class PlayerAnimatorAssetsLoader {
    public static <AA extends PlayerAnimatorAssets, A extends LevelScenePlayerAnimator<AA>> AA load(
        final Class<AA> type,
        final A animator
    ) {
        final Function<String, Texture> spriteLoader = animator::loadSprite;
        return AnimatorAssetsLoader.load(type, animator.getFramesParentContext(), spriteLoader);
    }
}
