package house.x1337.app.smb3.model.game.enemy.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.model.game.enemy.EnemyAnimatorAssets;

/**
 * @param walk   the two-frame ground walk cycle
 * @param squished the flattened sprite a stomped Goomba wears for its last 16 frames (dasm
 *                             {@code ObjState_Squashed} draws frame 3). Its art fills only the lower
 *                             half of the tile, so it sits on the ground without moving the quad.
 */
public record GoombaAnimatorAssets(
    Texture[] walk,
    Texture squished
) implements EnemyAnimatorAssets {}
