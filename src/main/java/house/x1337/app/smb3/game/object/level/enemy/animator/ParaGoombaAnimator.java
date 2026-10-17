package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.enemy.ParaGoomba;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.enemy.asset.ParaGoombaAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.jme3.material.RenderState.FaceCullMode.Off;

/**
 * Draws a Para-Goomba as <b>three</b> quads - the Goomba body plus a wing either side - because
 * {@code ParaGoomba_Draw} (dasm prg004.asm:3130) does exactly that: it calls
 * {@code GroundTroop_DrawMirrored}, the ground Goomba's own unmodified body draw, and then
 * hand-writes two more hardware sprites.
 *
 * <p>The two clocks must stay apart, which is why the wings are not baked into composed body frames:
 * the body frame is {@code (Counter_1 >> 3) & 1} while the wing pattern is {@code Objects_Var3 & $08},
 * and {@code Var3} advances at {@code +1} or {@code +3} a frame depending on whether the Para-Goomba
 * is rising out of its big hop.
 *
 * <p>Like {@link GoombaAnimator} this is a singleton holding no per-entity state: the wing clock and
 * the walk frame index are per-object slots and live on the entity.
 */
@Getter
@Singleton
@RequiredArgsConstructor
public final class ParaGoombaAnimator implements EnemyAnimator<ParaGoombaAnimatorAssets, ParaGoomba> {
    /**
     * A wing is one 8x16 hardware sprite - half a tile wide, a full tile tall (dasm prg004.asm:3143+
     * writes a single {@code Sprite_RAM} entry per wing, and the object is {@code OA1_HEIGHT32}, i.e.
     * drawn from 8x16 sprites).
     */
    public static final Dimensions WING_DIMENSIONS = Dimensions.halfTileWidth("ParaGoombaWing");

    private final String framesPath = "paragoomba";
    private final Class<ParaGoombaAnimatorAssets> assetsType = ParaGoombaAnimatorAssets.class;
    private final Map<EnemyMode, ParaGoombaAnimatorAssets> assetsByMode = new HashMap<>();

    /**
     * The body sprite for this tick - the current step of the walk cycle, unchanged from the ground
     * Goomba's. There is no squashed frame here: a stomped Para-Goomba sheds its wings and hands over
     * to a real {@link house.x1337.app.smb3.game.object.level.enemy.Goomba}, which owns that frame.
     */
    @Override
    public Texture frameTexture(final ParaGoomba paraGoomba) {
        return assetsFor(paraGoomba).walk()[paraGoomba.getWalkFrameIndex()];
    }

    public int walkFrameCount(final ParaGoomba paraGoomba) {
        return assetsFor(paraGoomba).walk().length;
    }

    /**
     * Builds one wing quad. Face culling is off for the same reason the body's is - these quads can be
     * scaled negatively - and the art ships pre-mirrored, so nothing here flips.
     *
     * @param paraGoomba the Para-Goomba the wing belongs to
     * @param leftWing   {@code true} for the wing on the body's left, {@code false} for its right
     * @return the wing's geometry, not yet attached to the scene
     */
    public Geometry buildWingSprite(
        final ParaGoomba paraGoomba,
        final boolean leftWing
    ) {
        final Geometry geometry = fromTexture(
            paraGoomba.getAssetManager(),
            wingTexture(paraGoomba, leftWing),
            WING_DIMENSIONS
        );
        geometry.getMaterial().getAdditionalRenderState().setFaceCullMode(Off);
        return geometry;
    }

    /**
     * Repaints both wings for the beat the Para-Goomba is now on. Called only on the tick the flap
     * actually turns over, the same way the body frame is applied only when it flips.
     *
     * @param paraGoomba the Para-Goomba whose wings to repaint
     */
    public void applyCurrentWingFrames(final ParaGoomba paraGoomba) {
        applyWingFrame(paraGoomba, paraGoomba.getLeftWingGeometry(), true);
        applyWingFrame(paraGoomba, paraGoomba.getRightWingGeometry(), false);
    }

    private void applyWingFrame(
        final ParaGoomba paraGoomba,
        final Geometry wingGeometry,
        final boolean leftWing
    ) {
        if (wingGeometry == null) {
            return;
        }
        wingGeometry.getMaterial().setTexture("ColorMap", wingTexture(paraGoomba, leftWing));
    }

    /**
     * The ROM stores one pattern byte for both wings - {@code $CD} raised, {@code $CF} lowered - and
     * mirrors the right one through {@code SPR_HFLIP} in its attribute byte. Here the mirror is art, so
     * the pair is indexed rather than flipped.
     */
    private Texture wingTexture(
        final ParaGoomba paraGoomba,
        final boolean leftWing
    ) {
        final ParaGoombaAnimatorAssets assets = assetsFor(paraGoomba);
        final Texture[] pair = paraGoomba.isWingUp() ? assets.wingUp() : assets.wingDown();
        return pair[leftWing ? 0 : 1];
    }
}
