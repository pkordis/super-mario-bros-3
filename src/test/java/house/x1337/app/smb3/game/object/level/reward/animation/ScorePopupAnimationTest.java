package house.x1337.app.smb3.game.object.level.reward.animation;

import com.jme3.scene.Geometry;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Where a score caption goes and how far it may rise: placed 16 px above the object, the fallback to screen
 * line 5 at 192 or more, and the stop once the line is below 4.
 */
class ScorePopupAnimationTest {
    private static final int ROWS = 30;
    private static final double SCREEN_TOP = 160;
    private static final float TOLERANCE = 1e-6f;

    private final LevelFrameCounter frameCounter = new LevelFrameCounter();
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(64, ROWS));
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(0, SCREEN_TOP, 256, SCREEN_TOP + 240));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getLevelFrameCounter()).thenReturn(frameCounter);
    }

    @Test
    @DisplayName("Over an object, the score's sprite is 16 px above the object's Y, at its X")
    void aScoreOverAnObject() {
        // Prepare
        final ScorePopupAnimation caption = caption();

        // Execute
        caption.placeOver(objectAt(80, SCREEN_TOP + 100));

        // Verify
        assertThat(spriteLeftOf(caption)).isCloseTo(80f, within(TOLERANCE));
        assertThat(spriteTopOf(caption)).isCloseTo((float) SCREEN_TOP + 84, within(TOLERANCE));
        assertThat(caption.getMaxRises()).as("From screen line 84 down to 3").isEqualTo(81);
    }

    @Test
    @DisplayName("An object less than 16 px below the screen's top puts its score at screen line 5")
    void aScoreNearTheTopFallsBack() {
        // Prepare - screen line 10 - 16 wraps to 250
        final ScorePopupAnimation caption = caption();

        // Execute
        caption.placeOver(objectAt(80, SCREEN_TOP + 10));

        // Verify
        assertThat(spriteTopOf(caption)).isCloseTo((float) SCREEN_TOP + 5, within(TOLERANCE));
        assertThat(caption.getMaxRises()).as("5, then 4, then it stops at 3").isEqualTo(2);
    }

    @Test
    @DisplayName("A score at screen line 191 stays there, and one at 192 goes to 5")
    void theFallbackStartsAt192() {
        // Prepare
        final ScorePopupAnimation low = caption();
        final ScorePopupAnimation lower = caption();

        // Execute
        low.placeAt(40, SCREEN_TOP + 191, 191);
        lower.placeAt(40, SCREEN_TOP + 192, 192);

        // Verify
        assertThat(spriteTopOf(low)).isCloseTo((float) SCREEN_TOP + 191, within(TOLERANCE));
        assertThat(spriteTopOf(lower)).isCloseTo((float) SCREEN_TOP + 5, within(TOLERANCE));
    }

    @Test
    @DisplayName("A score at screen line 3 or less does not rise at all")
    void aScoreAtTheTopDoesNotRise() {
        // Prepare
        final ScorePopupAnimation caption = caption();

        // Execute
        caption.placeAt(40, SCREEN_TOP + 3, 3);

        // Verify
        assertThat(caption.getMaxRises()).isZero();
    }

    @Test
    @DisplayName("The screen line of a level Y is measured from the screen's top, floored")
    void theScreenLine() {
        // Prepare
        final ScorePopupAnimation caption = caption();

        // Execute & Verify
        assertThat(caption.screenLine(SCREEN_TOP + 88.5)).isEqualTo(88);
        assertThat(caption.screenLine(SCREEN_TOP - 6)).as("Above the screen").isEqualTo(-6);
    }

    @Test
    @DisplayName("Starting a score gives it its first update, on this frame's count")
    void startingGivesTheFirstUpdate() {
        // Prepare
        final ScorePopupAnimation caption = startable(caption());
        caption.placeAt(40, SCREEN_TOP + 88, 88);
        frameCounter.tick();
        frameCounter.tick();
        frameCounter.tick();

        // Execute
        caption.start();

        // Verify
        assertThat(caption.getFrameIndex()).isEqualTo(1);
        assertThat(caption.getMotion().verticalOffsetAt(1)).as("Frame count 3 rises in the first stage").isEqualTo(-7);
        assertThat(caption.getMotion().verticalOffsetAt(17)).as("3 + 16 is odd: no rise every other frame")
            .isEqualTo(caption.getMotion().verticalOffsetAt(16));
    }

    private ScorePopupAnimation caption() {
        return new ScorePopupAnimation(gameEngine, new Reward.Data(), of(0, 0));
    }

    private static ScorePopupAnimation startable(final ScorePopupAnimation caption) {
        final ScorePopupAnimation spied = spy(caption);
        doReturn(mock(Texture.class)).when(spied).textureForSpriteGeometry();
        doReturn(mock(Geometry.class)).when(spied).createAndAttachSprite(any(), any());
        return spied;
    }

    private ActiveLevelObject objectAt(final double x, final double y) {
        final ActiveLevelObject object = mock(ActiveLevelObject.class);
        when(object.getPosition()).thenReturn(new PixelPosition(x, y));
        return object;
    }

    private static float spriteLeftOf(final ScorePopupAnimation caption) {
        return caption.getWorldOffset().x() * 16;
    }

    private static float spriteTopOf(final ScorePopupAnimation caption) {
        final WorldOffset worldOffset = caption.getWorldOffset();
        return (ROWS - worldOffset.y()) * 16;
    }
}
