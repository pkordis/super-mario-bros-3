package house.x1337.app.smb3.game.object.level.card;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.converter.EnumeratedImageResourceConverter;
import house.x1337.app.smb3.enumeration.resource.LevelCompletionCardImageResource;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelFrameCounter;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.EnumeratedImageResource;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.FLOWER;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The card spawns as the scroll reaches it and is deleted when it leaves the screen, as any object; placed again,
 * it starts on the Mushroom.
 */
class LevelCompletionCardSpawnTest {
    private static final int SCREEN_WIDTH = 256;
    private static final Coordinates CELL = of(20, 8);
    private static final double CARD_X = CELL.x() * (double) TILE_SPRITE_SIZE;

    private final LevelFrameCounter counter = new LevelFrameCounter();
    private final LevelCompletionCardMotionManager manager = new LevelCompletionCardMotionManager();
    private final Node rootNode = new Node("root");

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private EnumeratedImageResource<LevelCompletionCardImageResource> cardImages;
    private LevelCompletion levelCompletion;
    private double screenLeft;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == ImageResource.class) {
                return new ImageResource();
            }
            if (type == LevelCompletionCardMotionManager.class) {
                return manager;
            }
            if (type == LevelCompletionCardSpawner.class) {
                return new LevelCompletionCardSpawner(invocation.<GameEngine>getArgument(1));
            }
            if (type == LevelCompletionCard.class) {
                // The spawner places it; the bean only arrives with its images injected.
                final LevelCompletionCard card = new LevelCompletionCard(invocation.<GameEngine>getArgument(1));
                card.setCardImages(cardImages);
                return card;
            }
            throw new IllegalArgumentException("Unexpected bean " + type);
        });
        cardImages = new EnumeratedImageResourceConverter<LevelCompletionCardImageResource>()
            .convert(LevelCompletionCardImageResource.class.getName());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(64, 12));
        when(levelScene.getLevelCompletionCard()).thenReturn(CELL);

        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenAnswer(invocation -> new AxisAlignedBoundingBox(screenLeft, 0, screenLeft + SCREEN_WIDTH, 192));

        final GameEngine gameEngine = mock(GameEngine.class);
        levelCompletion = new LevelCompletion(gameEngine);
        final ActiveObjectGrid<ActiveLevelObject> broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getLevelFrameCounter()).thenReturn(counter);
        when(gameEngine.getLevelCompletion()).thenReturn(levelCompletion);
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        when(gameEngine.getRootNode()).thenReturn(rootNode);
        levelCompletion.reset();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The card is not placed beyond the right look-ahead column, and is placed once in it")
    void theCardSpawnsAsTheScrollReachesIt() {
        // Prepare - the card's column starts 16 px past the right look-ahead (screenRight + 16)
        screenLeft = CARD_X - SCREEN_WIDTH - 2 * TILE_SPRITE_SIZE;

        // Execute & Verify
        runTick();
        assertThat(manager.getActiveInstances()).as("Out of reach").isEmpty();
        screenLeft += TILE_SPRITE_SIZE;
        runTick();
        assertThat(manager.getActiveInstances()).as("In the look-ahead column").hasSize(1);
        assertThat(rootNode.getChildren()).as("Its sprite is in the scene").hasSize(1);
        runTick();
        assertThat(manager.getActiveInstances()).as("Placed once").hasSize(1);
    }

    @Test
    @DisplayName("Scrolled off screen and back, the card is a new one showing the Mushroom")
    void aRespawnedCardShowsTheMushroom() {
        // Prepare - on screen, and left to turn to the Flower
        screenLeft = CARD_X - 64;
        runTick();
        final LevelCompletionCard first = manager.getActiveInstances().getFirst();
        while (first.getFace() != FLOWER) {
            runTick();
        }

        // Execute - past the delete edge on the left (x <= screenLeft - 48), then back
        screenLeft = CARD_X + 48;
        runTick();
        final boolean deleted = manager.getActiveInstances().isEmpty();
        final int sceneChildrenWhileAway = rootNode.getChildren().size();
        screenLeft = CARD_X - 64;
        runTick();

        // Verify
        assertThat(deleted).as("Deleted off screen").isTrue();
        assertThat(sceneChildrenWhileAway).as("Its sprite left the scene").isZero();
        assertThat(manager.getActiveInstances()).hasSize(1);
        final LevelCompletionCard second = manager.getActiveInstances().getFirst();
        assertThat(second).as("A new card").isNotSameAs(first);
        assertThat(second.getFace()).isEqualTo(MUSHROOM);
        assertThat(rootNode.getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("Just inside the left delete edge the card stays, and just past it is deleted")
    void theLeftDeleteEdge() {
        // Prepare
        screenLeft = CARD_X - 64;
        runTick();

        // Execute & Verify
        screenLeft = CARD_X + 47;
        runTick();
        assertThat(manager.getActiveInstances()).as("x > screenLeft - 48").hasSize(1);
        screenLeft = CARD_X + 48;
        runTick();
        assertThat(manager.getActiveInstances()).as("x <= screenLeft - 48").isEmpty();
    }

    @Test
    @DisplayName("A scene without a card places none")
    void noCardNoSpawn() {
        // Prepare
        when(levelCompletion.getGameEngine().getLevelScene().getLevelCompletionCard()).thenReturn(null);
        levelCompletion.reset();
        screenLeft = CARD_X - 64;

        // Execute
        runTick();

        // Verify
        assertThat(manager.getActiveInstances()).isEmpty();
    }

    private void runTick() {
        counter.tick();
        levelCompletion.spawnCardComingIntoView();
        manager.update();
    }
}
