package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssetsMoving;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;

/**
 * Draws the single dead frame ({@code dead.png}) while the player plays out its death throe (dasm
 * {@code Player_Frame = PF_DIE}, written every tick of the dying routine in prg029 PRG029_D771).
 *
 * <p>Unlike the mode animators this has no {@code assets.json} and no walk/run cycle — the death
 * sprite is a single texture under {@code sprites/player/<id>/level/dead/}. It is loaded lazily on the
 * first draw (the asset manager is only available once the engine is running) and the quad is rebuilt
 * once; thereafter the frame never changes, only the node's position does, which the player's own
 * death tick drives. The sprite is drawn left-facing, as the ROM's dead frame has no orientation.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class DeathAnimator implements LevelScenePlayerAnimator<PlayerAnimatorAssetsMoving> {
    private static final String DEAD_SPRITE_FILENAME = "dead.png";

    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    private Texture deadTexture;
    private boolean rendered;

    @Override
    public void setAssets(final PlayerAnimatorAssetsMoving animatorAssets) {
        // No asset bundle: the dead frame is a lone sprite loaded directly in update().
    }

    @Override
    public String getFramesParentContext() {
        return "sprites/player/%s/level/dead/".formatted(identity.getAnimationFramesPath());
    }

    @Override
    public void resetState() {
        rendered = false;
    }

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        if (rendered) {
            return;
        }
        if (deadTexture == null) {
            deadTexture = loadSprite(DEAD_SPRITE_FILENAME);
        }
        rebuildWithTexture(levelScenePlayer.getNode(), deadTexture, LEFT);
        rendered = true;
    }
}
