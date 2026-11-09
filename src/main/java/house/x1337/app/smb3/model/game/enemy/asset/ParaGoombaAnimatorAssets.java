package house.x1337.app.smb3.model.game.enemy.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.model.game.enemy.EnemyAnimatorAssets;

/**
 * The Para-Goomba's three sprite bundles. There are three rather than one because the Para-Goomba is
 * drawn as the Goomba's body plus two separate wing sprites: the body cycles off the level frame counter
 * while the wings cycle off the Para-Goomba's own wing clock, which runs at a variable rate.
 * Pre-composing body+wings into single frames would couple two clocks that are kept apart.
 *
 * @param walk     the two-frame body walk cycle, a mirror pair exactly as the Goomba's is
 * @param wingUp   the raised wing pair, left then right. The right wing is the left one mirrored;
 *                 shipping the mirror as explicit art keeps the animator flip-free, as
 *                 {@code GoombaAnimator} already is.
 * @param wingDown the lowered wing pair, left then right
 */
public record ParaGoombaAnimatorAssets(
    Texture[] walk,
    Texture[] wingUp,
    Texture[] wingDown
) implements EnemyAnimatorAssets {}
