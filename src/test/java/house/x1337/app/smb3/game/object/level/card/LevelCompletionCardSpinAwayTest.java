package house.x1337.app.smb3.game.object.level.card;

import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.game.object.level.card.LevelCompletionCardScene.ROWS;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.floorDiv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The collected card's spin-away. Each tick here is one frame as {@code GameEngine} runs it: the level frame counter
 * goes up first, then the managers update.
 */
class LevelCompletionCardSpinAwayTest {
    private static final Coordinates CELL = of(20, 8);
    private static final int CARD_TOP = CELL.y() * TILE_SPRITE_SIZE;
    private static final int FACE_ON = 0;
    private static final int NARROW = 1;
    private static final int EDGE_ON = 2;

    private final LevelFrameCounter frameCounter = new LevelFrameCounter();
    private final AtomicReference<AxisAlignedBoundingBox> screen =
        new AtomicReference<>(new AxisAlignedBoundingBox(200, 0, 456, 192));
    private final AnimationImageResource frames = mock(AnimationImageResource.class);
    private final AnimationImageResource twinkle = mock(AnimationImageResource.class);
    private final LevelCompletion levelCompletion = mock(LevelCompletion.class);
    private GameEngine gameEngine;
    private Node rootNode;

    @BeforeEach
    void prepare() {
        gameEngine = mock(GameEngine.class);
        rootNode = LevelCompletionCardScene.prepare(gameEngine, mock(LevelScene.class));
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt())).thenAnswer(invocation -> screen.get());
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getLevelFrameCounter()).thenReturn(frameCounter);
        when(gameEngine.getLevelCompletion()).thenReturn(levelCompletion);
        when(frames.getFrameRgbData(anyInt())).thenAnswer(invocation -> new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE]);
        when(twinkle.getFrameRgbData(anyInt())).thenAnswer(invocation -> new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE]);
    }

    @Test
    @DisplayName("On the touch the card is gone and its spin-away starts in its place, with the face it showed")
    void theTouchStartsTheSpinAway() {
        // Prepare
        final LevelCompletionCardMotionManager cards = new LevelCompletionCardMotionManager();
        final LevelCompletionCardSpinAwayMotionManager spinAways = mock(LevelCompletionCardSpinAwayMotionManager.class);
        doReturn(List.of(cards, spinAways)).when(gameEngine).getMotionManagers();
        final LevelCompletionCard card = LevelCompletionCardScene.place(gameEngine, CELL);
        cards.spawn(card);
        card.onCollisionWith(mock(LevelScenePlayer.class));

        // Execute
        cards.postCollision();

        // Verify
        verify(spinAways).spawn(card);
        assertThat(cards.getActiveInstances()).isEmpty();
        assertThat(rootNode.hasChild(card.getSpriteGeometry())).isFalse();
    }

    @Test
    @DisplayName("The spin-away starts where the card was, face-on, at the card's depth, and uses its face's pictures")
    void itStartsWhereTheCardWas() {
        // Execute
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(FLOWER);

        // Verify
        assertThat(spinAway.getSpriteGeometry().getLocalTranslation().x).isEqualTo(CELL.x());
        assertThat(spinAway.getSpriteGeometry().getLocalTranslation().y).isEqualTo(ROWS - 1 - CELL.y());
        assertThat(spinAway.getSpriteGeometry().getLocalTranslation().z).isEqualTo(Z_DEPTH_ITEM_REWARD);
        assertThat(rootNode.hasChild(spinAway.getSpriteGeometry())).isTrue();
        assertThat(shownPicture(spinAway)).isEqualTo(FACE_ON);
        verify(frames).getFrameRgbData(3);
        verify(frames).getFrameRgbData(4);
        verify(frames).getFrameRgbData(5);
        verify(frames, never()).getFrameRgbData(0);
    }

    @Test
    @DisplayName("From the next tick it rises 3, 2, 3, 2 px a tick, and keeps its level X while the screen scrolls")
    void itRisesStraightUp() {
        // Prepare
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        final List<Float> rises = new ArrayList<>();
        float bottom = bottomOf(spinAway);

        // Execute
        for (int tick = 0; tick < 8; tick++) {
            screen.set(new AxisAlignedBoundingBox(200 + 2 * tick, 0, 456 + 2 * tick, 192));
            tick(spinAway);
            rises.add(bottom - bottomOf(spinAway));
            bottom = bottomOf(spinAway);
        }

        // Verify
        assertThat(rises).containsExactly(3f, 2f, 3f, 2f, 3f, 2f, 3f, 2f);
        assertThat(spinAway.getSpriteGeometry().getLocalTranslation().x).isEqualTo(CELL.x());
    }

    @Test
    @DisplayName("It turns face-on, narrow, edge-on, narrow, face-on, only on ticks where the frame counter & 3 is 0")
    void itSpinsEvery4Ticks() {
        // Prepare - the touch on frame counter 5, so the first turn is 3 ticks later, on 8
        frameCounter.tick();
        frameCounter.tick();
        frameCounter.tick();
        frameCounter.tick();
        frameCounter.tick();
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(STAR);
        final List<Integer> shown = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 19; tick++) {
            tick(spinAway);
            shown.add(shownPicture(spinAway));
        }

        // Verify
        assertThat(shown).containsExactly(
            FACE_ON, FACE_ON, NARROW, NARROW, NARROW, NARROW,
            EDGE_ON, EDGE_ON, EDGE_ON, EDGE_ON, NARROW, NARROW, NARROW, NARROW,
            FACE_ON, FACE_ON, FACE_ON, FACE_ON, NARROW
        );
    }

    @Test
    @DisplayName("It is removed on the first tick it starts with its bottom above the screen's top, and not before")
    void itLeavesOffTheTop() {
        // Prepare - the screen's top is 40 px above the card's top
        screen.set(new AxisAlignedBoundingBox(200, CARD_TOP - 40, 456, CARD_TOP + 152));
        final LevelCompletionCardSpinAwayMotionManager manager = new LevelCompletionCardSpinAwayMotionManager();
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        manager.getActiveAnimations().add(spinAway);
        int moves = 0;
        while (CARD_TOP + floorDiv(-40 * moves, 16) + TILE_SPRITE_SIZE >= CARD_TOP - 40) {
            moves++;
        }

        // Execute & Verify
        for (int tick = 0; tick < moves; tick++) {
            frameCounter.tick();
            manager.update();
            assertThat(manager.getActiveAnimations()).as("Tick %d", tick).hasSize(1);
        }
        frameCounter.tick();
        manager.update();
        assertThat(moves).as("Its bottom passes 56 px up").isEqualTo(23);
        assertThat(manager.getActiveAnimations()).isEmpty();
        assertThat(rootNode.hasChild(spinAway.getSpriteGeometry())).isFalse();
    }

    @Test
    @DisplayName("While gameplay is halted it neither moves nor turns, and stays on screen")
    void itFreezesWhileHalted() {
        // Prepare
        final LevelCompletionCardSpinAwayMotionManager manager = new LevelCompletionCardSpinAwayMotionManager();
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        manager.getActiveAnimations().add(spinAway);
        final float bottom = bottomOf(spinAway);

        // Execute
        for (int tick = 0; tick < 8; tick++) {
            frameCounter.tick();
            manager.updateWhileHalted();
        }

        // Verify
        assertThat(bottomOf(spinAway)).isEqualTo(bottom);
        assertThat(shownPicture(spinAway)).isEqualTo(FACE_ON);
        assertThat(rootNode.hasChild(spinAway.getSpriteGeometry())).isTrue();
    }

    @Test
    @DisplayName("A level reset removes it")
    void aResetRemovesIt() {
        // Prepare
        final LevelCompletionCardSpinAwayMotionManager manager = new LevelCompletionCardSpinAwayMotionManager();
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        manager.getActiveAnimations().add(spinAway);

        // Execute
        manager.reset();

        // Verify
        assertThat(manager.getActiveAnimations()).isEmpty();
        assertThat(rootNode.hasChild(spinAway.getSpriteGeometry())).isFalse();
    }

    @Test
    @DisplayName("It is never in the active-object grid, so nothing collides with it")
    void itIsNeverInTheGrid() {
        // Prepare
        final LevelCompletionCardSpinAwayMotionManager manager = new LevelCompletionCardSpinAwayMotionManager();
        manager.getActiveAnimations().add(spinAway(MUSHROOM));

        // Execute
        for (int tick = 0; tick < 8; tick++) {
            frameCounter.tick();
            manager.update();
            manager.postCollision();
        }

        // Verify
        verify(gameEngine, never()).getActiveObjectGrid();
    }

    @Test
    @DisplayName("The manager starts a spin-away from the collected card's cell and face, with its pictures")
    void theManagerStartsItFromTheCard() {
        // Prepare
        final LevelCompletionCardSpinAwayMotionManager manager = new LevelCompletionCardSpinAwayMotionManager();
        final LevelCompletionCard card = mock(LevelCompletionCard.class);
        when(card.getGameEngine()).thenReturn(gameEngine);
        when(card.getOffset()).thenReturn(CELL);
        when(card.getFace()).thenReturn(STAR);
        final LevelCompletionCardSpinAwayAnimation animation = mock(LevelCompletionCardSpinAwayAnimation.class);

        // Execute
        try (MockedStatic<StaticBeanFactory> beans = mockStatic(StaticBeanFactory.class)) {
            beans.when(() -> StaticBeanFactory.getBean(LevelCompletionCardSpinAwayAnimation.class))
                .thenReturn(animation);
            manager.spawn(card);
        }

        // Verify
        verify(animation).configure(gameEngine, CELL, STAR, manager.getAnimationFrames(), manager.getTwinkleFrames());
        assertThat(manager.getActiveAnimations()).containsExactly(animation);
    }

    @Test
    @DisplayName("In the fanfare it rises as in its flight, drifting left 1 px on its first tick and every 16th after, "
        + "and stays on screen whatever the screen's top")
    void inTheFanfareItDriftsLeft() {
        // Prepare - the screen's top far below the card
        when(levelCompletion.isFanfare()).thenReturn(true);
        when(levelCompletion.getFanfareCard()).thenReturn(FanfareCard.FLYING);
        screen.set(new AxisAlignedBoundingBox(200, CARD_TOP + 100, 456, CARD_TOP + 292));
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        final int startX = CELL.x() * TILE_SPRITE_SIZE;
        final List<Integer> xs = new ArrayList<>();

        // Execute
        for (int tick = 1; tick <= 34; tick++) {
            tick(spinAway);
            xs.add(spinAway.getX());
        }

        // Verify
        assertThat(xs.getFirst()).as("Tick 1").isEqualTo(startX - 1);
        assertThat(xs.get(15)).as("Tick 16").isEqualTo(startX - 1);
        assertThat(xs.get(16)).as("Tick 17").isEqualTo(startX - 2);
        assertThat(xs.get(33)).as("Tick 34").isEqualTo(startX - 3);
        assertThat(spinAway.getSpriteGeometry().getLocalTranslation().x * TILE_SPRITE_SIZE).isEqualTo(startX - 3);
        assertThat(bottomOf(spinAway)).as("Risen as in its flight")
            .isEqualTo(CARD_TOP + floorDiv(-40 * 34, TILE_SPRITE_SIZE) + TILE_SPRITE_SIZE);
        assertThat(spinAway.isExpired()).as("Above the screen's top, still there").isFalse();
    }

    @Test
    @DisplayName("In the fanfare the drift stops for good once its X modulo 16 is 7, 9 px left of where it started")
    void theDriftStopsAtSeven() {
        // Prepare
        when(levelCompletion.isFanfare()).thenReturn(true);
        when(levelCompletion.getFanfareCard()).thenReturn(FanfareCard.FLYING);
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        final int startX = CELL.x() * TILE_SPRITE_SIZE;

        // Execute & Verify
        for (int tick = 1; tick < 129; tick++) {
            tick(spinAway);
        }
        assertThat(spinAway.getX()).as("Tick 128").isEqualTo(startX - 8);
        tick(spinAway);
        assertThat(spinAway.getX()).as("Tick 129").isEqualTo(startX - 9);
        for (int tick = 0; tick < 100; tick++) {
            tick(spinAway);
        }
        assertThat(spinAway.getX()).as("Stopped").isEqualTo(startX - 9);
    }

    @Test
    @DisplayName("In the fanfare it shows the twinkle it is told to, in place, and is removed once it is gone")
    void inTheFanfareItTwinklesThenGoes() {
        // Prepare
        when(levelCompletion.isFanfare()).thenReturn(true);
        final LevelCompletionCardSpinAwayMotionManager manager = new LevelCompletionCardSpinAwayMotionManager();
        final LevelCompletionCardSpinAwayAnimation spinAway = spinAway(MUSHROOM);
        manager.getActiveAnimations().add(spinAway);
        final float bottom = bottomOf(spinAway);

        // Execute & Verify
        when(levelCompletion.getFanfareCard()).thenReturn(FanfareCard.SMALL_TWINKLE);
        frameCounter.tick();
        manager.update();
        assertThat(shownTexture(spinAway)).as("Small").isSameAs(spinAway.getTwinkles()[0]);
        when(levelCompletion.getFanfareCard()).thenReturn(FanfareCard.LARGE_TWINKLE);
        frameCounter.tick();
        manager.update();
        assertThat(shownTexture(spinAway)).as("Large").isSameAs(spinAway.getTwinkles()[1]);
        assertThat(bottomOf(spinAway)).as("In place").isEqualTo(bottom);
        when(levelCompletion.getFanfareCard()).thenReturn(FanfareCard.GONE);
        frameCounter.tick();
        manager.update();
        assertThat(manager.getActiveAnimations()).isEmpty();
        assertThat(rootNode.hasChild(spinAway.getSpriteGeometry())).isFalse();
        verify(twinkle).getFrameRgbData(0);
        verify(twinkle).getFrameRgbData(1);
    }

    private LevelCompletionCardSpinAwayAnimation spinAway(final LevelCompletionCardType face) {
        final LevelCompletionCardSpinAwayAnimation spinAway = new LevelCompletionCardSpinAwayAnimation();
        spinAway.configure(gameEngine, CELL, face, frames, twinkle);
        return spinAway;
    }

    private void tick(final LevelCompletionCardSpinAwayAnimation spinAway) {
        frameCounter.tick();
        spinAway.tick();
    }

    private static float bottomOf(final LevelCompletionCardSpinAwayAnimation spinAway) {
        return (ROWS - spinAway.getSpriteGeometry().getLocalTranslation().y) * TILE_SPRITE_SIZE;
    }

    private static int shownPicture(final LevelCompletionCardSpinAwayAnimation spinAway) {
        final Texture texture = shownTexture(spinAway);
        final Texture[] pictures = spinAway.getPictures();
        for (int picture = 0; picture < pictures.length; picture++) {
            if (pictures[picture] == texture) {
                assertThat(spinAway.getPicture()).as("The frame's picture").isEqualTo(picture);
                return picture;
            }
        }
        throw new AssertionError("The spin-away shows a texture that is not one of its pictures");
    }

    private static Texture shownTexture(final LevelCompletionCardSpinAwayAnimation spinAway) {
        return (Texture) spinAway.getMaterial().getTextureParam("ColorMap").getTextureValue();
    }
}
