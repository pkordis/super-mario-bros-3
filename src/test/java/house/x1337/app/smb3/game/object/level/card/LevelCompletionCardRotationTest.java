package house.x1337.app.smb3.game.object.level.card;

import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.STAR;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The untouched card turns Mushroom, Flower, Star on every tick where the level frame counter {@code & 7 == 0}, and
 * not while gameplay is halted.
 */
class LevelCompletionCardRotationTest {
    private static final Coordinates CELL = of(4, 3);

    private final LevelFrameCounter counter = new LevelFrameCounter();
    private final LevelCompletionCardMotionManager manager = new LevelCompletionCardMotionManager();
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt())).thenReturn(new AxisAlignedBoundingBox(0, 0, 256, 192));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelFrameCounter()).thenReturn(counter);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);
        when(gameEngine.getLevelCompletion()).thenReturn(mock(LevelCompletion.class));
        LevelCompletionCardScene.prepare(gameEngine, mock(LevelScene.class));
    }

    @ParameterizedTest(name = "Placed on tick {0}, the face first changes {1} ticks later")
    @CsvSource({"8, 8", "11, 5", "15, 1", "16, 8"})
    @DisplayName("The card turns every 8 ticks in phase with the counter, starting 1 to 8 ticks after it is placed")
    void theFacesFollowTheCounter(final int placedOnTick, final int firstChangeAfter) {
        // Prepare
        tickCounterTo(placedOnTick);
        final LevelCompletionCard card = place();

        // Execute
        final List<Integer> changes = new ArrayList<>();
        final List<LevelCompletionCardType> faces = new ArrayList<>();
        LevelCompletionCardType previous = card.getFace();
        for (int tick = 1; tick <= firstChangeAfter + 16; tick++) {
            runTick();
            if (card.getFace() != previous) {
                changes.add(tick);
                faces.add(card.getFace());
                previous = card.getFace();
            }
        }

        // Verify
        assertThat(changes).as("Ticks after the placement where the face changed")
            .containsExactly(firstChangeAfter, firstChangeAfter + 8, firstChangeAfter + 16);
        assertThat(faces).containsExactly(FLOWER, STAR, MUSHROOM);
        assertThat(textureShown(card)).as("The sprite follows the face").isEqualTo(MUSHROOM.name());
    }

    @Test
    @DisplayName("A new card shows the Mushroom")
    void aNewCardShowsTheMushroom() {
        // Prepare & Execute
        final LevelCompletionCard card = LevelCompletionCardScene.place(gameEngine, CELL);

        // Verify
        assertThat(card.getFace()).isEqualTo(MUSHROOM);
        assertThat(textureShown(card)).as("The sprite shows it").isEqualTo(MUSHROOM.name());
    }

    @Test
    @DisplayName("While gameplay is halted the card does not turn, though the counter goes on")
    void haltedTicksDoNotTurnTheCard() {
        // Prepare - placed on tick 4, so the first un-halted turn would be tick 8
        tickCounterTo(4);
        final LevelCompletionCard card = place();

        // Execute - halted from tick 5 to tick 12, past the turn on tick 8
        for (int tick = 5; tick <= 12; tick++) {
            counter.tick();
            manager.updateWhileHalted();
        }

        // Verify
        assertThat(card.getFace()).as("Through the halt").isEqualTo(MUSHROOM);
        for (int tick = 13; tick < 16; tick++) {
            runTick();
        }
        assertThat(card.getFace()).as("Before the next multiple of 8").isEqualTo(MUSHROOM);
        runTick();
        assertThat(card.getFace()).as("On tick 16").isEqualTo(FLOWER);
    }

    private LevelCompletionCard place() {
        final LevelCompletionCard card = LevelCompletionCardScene.place(gameEngine, CELL);
        manager.spawn(card);
        manager.update();
        return card;
    }

    private static String textureShown(final LevelCompletionCard card) {
        return card.getSpriteGeometry().getMaterial().getTextureParam("ColorMap").getTextureValue().getName();
    }

    private void tickCounterTo(final int tick) {
        while (counter.getTicks() < tick) {
            counter.tick();
        }
    }

    private void runTick() {
        counter.tick();
        manager.update();
    }
}
