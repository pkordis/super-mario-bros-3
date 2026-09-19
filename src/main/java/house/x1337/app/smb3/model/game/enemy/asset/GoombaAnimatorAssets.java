package house.x1337.app.smb3.model.game.enemy.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.model.game.enemy.EnemyAnimatorAssets;

public record GoombaAnimatorAssets(
    Texture[] walkFrameTextures
) implements EnemyAnimatorAssets {}
