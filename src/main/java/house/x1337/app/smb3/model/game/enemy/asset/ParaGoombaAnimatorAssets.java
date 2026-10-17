package house.x1337.app.smb3.model.game.enemy.asset;

import com.jme3.texture.Texture;
import house.x1337.app.smb3.model.game.enemy.EnemyAnimatorAssets;

/**
 * The Para-Goomba's three sprite bundles. There are three rather than one because
 * {@code ParaGoomba_Draw} (dasm prg004.asm:3130) is the Goomba's body draw plus two hand-written
 * hardware sprites: the body cycles off {@code Counter_1} while the wings cycle off
 * {@code Objects_Var3}, which runs at a variable rate. Pre-composing body+wings into single frames
 * would couple two clocks the ROM keeps apart.
 *
 * @param walk     the two-frame body walk cycle, a mirror pair exactly as the Goomba's is
 * @param wingUp   the raised wing pair ({@code $CD}), left then right. The ROM stores one pattern for
 *                 both wings and mirrors the right one in hardware via {@code SPR_HFLIP} in attribute
 *                 {@code $42}; shipping the mirror as explicit art keeps the animator flip-free, as
 *                 {@code GoombaAnimator} already is.
 * @param wingDown the lowered wing pair ({@code $CF}), left then right
 */
public record ParaGoombaAnimatorAssets(
    Texture[] walk,
    Texture[] wingUp,
    Texture[] wingDown
) implements EnemyAnimatorAssets {}
