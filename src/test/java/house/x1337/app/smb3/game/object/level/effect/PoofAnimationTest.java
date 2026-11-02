package house.x1337.app.smb3.game.object.level.effect;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.WorldOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jme3.scene.Spatial.CullHint.Always;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;
import static house.x1337.app.smb3.model.game.effect.PoofSequence.poofDeath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The poof death as it plays on screen, one entry per tick from the kill tick on: nothing while the plant
 * is still drawn and while {@code Object_PoofDie} sets the timer, then {@code $43}, {@code $41},
 * {@code $45}, {@code $47} (dasm prg000.asm:2090-2140), then nothing, then gone.
 */
class PoofAnimationTest {
    private static final int NOT_DRAWN = -1;
    private static final int FRAME_OF_47 = 0;
    private static final int FRAME_OF_45 = 1;
    private static final int FRAME_OF_43 = 2;
    private static final int FRAME_OF_41 = 3;

    private final GameEngine gameEngine = mock(GameEngine.class);
    private final AnimationImageResource frames = mock(AnimationImageResource.class);

    @BeforeEach
    void prepare() {
        when(gameEngine.getRootNode()).thenReturn(new Node("root"));
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(frames.getFrameRgbData(anyInt())).thenAnswer(invocation -> new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE]);
    }

    @Test
    @DisplayName("The poof death shows nothing for 2 ticks, then $43, $41, $45, $47, then nothing until it expires")
    void thePoofDeathPlaysInTheRomsOrder() {
        // Prepare
        final PoofAnimation poof = newPoofDeath();
        final List<Integer> shown = new ArrayList<>();
        shown.add(shownFrame(poof));

        // Execute
        while (!poof.isExpired()) {
            poof.tick();
            if (!poof.isExpired()) {
                shown.add(shownFrame(poof));
            }
        }

        // Verify
        final List<Integer> expected = new ArrayList<>();
        expected.add(NOT_DRAWN);
        expected.add(NOT_DRAWN);
        repeat(expected, FRAME_OF_43, 7);
        repeat(expected, FRAME_OF_41, 8);
        repeat(expected, FRAME_OF_45, 8);
        repeat(expected, FRAME_OF_47, 7);
        expected.add(NOT_DRAWN);
        assertThat(shown).isEqualTo(expected);
    }

    @Test
    @DisplayName("The cloud's quad sits where it was asked to, at the poof depth")
    void theCloudSitsWhereItWasAsked() {
        // Prepare & Execute
        final PoofAnimation poof = newPoofDeath();

        // Verify
        assertThat(poof.getSpriteGeometry().getLocalTranslation().x).isEqualTo(3f);
        assertThat(poof.getSpriteGeometry().getLocalTranslation().y).isEqualTo(4f);
        assertThat(poof.getSpriteGeometry().getLocalTranslation().z).isEqualTo(Z_DEPTH_POOF);
    }

    private PoofAnimation newPoofDeath() {
        final PoofAnimation poofAnimation = new PoofAnimation();
        poofAnimation.configure(gameEngine, WorldOffset.of(3f, 4f, Z_DEPTH_POOF), frames, poofDeath());
        return poofAnimation;
    }

    private static int shownFrame(final PoofAnimation poof) {
        if (poof.getSpriteGeometry().getLocalCullHint() == Always) {
            return NOT_DRAWN;
        }
        final Texture texture = (Texture) poof.getMaterial().getTextureParam("ColorMap").getTextureValue();
        final Texture[][] framesByFlip = poof.getFramesByFlip();
        for (int frame = 0; frame < framesByFlip.length; frame++) {
            if (framesByFlip[frame][0] == texture || framesByFlip[frame][1] == texture) {
                return frame;
            }
        }
        throw new AssertionError("The cloud shows a texture that is not one of its frames");
    }

    private static void repeat(final List<Integer> list, final int value, final int times) {
        for (int time = 0; time < times; time++) {
            list.add(value);
        }
    }
}
