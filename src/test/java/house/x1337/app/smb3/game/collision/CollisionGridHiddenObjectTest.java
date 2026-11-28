package house.x1337.app.smb3.game.collision;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.BakedLayerPainter;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.UP;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A hidden object lets everything through, and is struck only by the head of a rising player. See
 * {@code kb/invisible-block.md}.
 */
class CollisionGridHiddenObjectTest {
    private static final int SIZE = 8;
    private static final int HIDDEN_ROW = 2;
    private static final int HIDDEN_COLUMN = 2;

    private final HiddenObject hiddenObject = mock(HiddenObject.class);

    @Test
    @DisplayName("Rising with the head in a hidden object strikes it from below and stops the rise")
    void risingIntoItStrikesIt() {
        // Prepare - a big player's head probe (X+8, Y+6) lands on the hidden cell
        final LevelScenePlayer player = largePlayer(JUMPING, 32, 34, -2);
        final StaticEnvironmentCollisionGrid grid = gridWithAHiddenObject();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        verify(hiddenObject).onCollisionFromBelow(player);
        assertThat(player.getPosition().getDY()).as("The rise is over").isGreaterThan(0);
        assertThat(player.getRuntimeState().isHitCeiling()).as("Ceiling hit").isTrue();
    }

    @Test
    @DisplayName("Falling with the head in a hidden object passes through it")
    void fallingThroughItDoesNothing() {
        // Prepare
        final LevelScenePlayer player = largePlayer(FALLING, 32, 34, 1);
        final StaticEnvironmentCollisionGrid grid = gridWithAHiddenObject();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        verify(hiddenObject, never()).onCollisionFromBelow(any());
        assertThat(player.getPosition().getDY()).as("Still falling").isEqualTo(1);
    }

    @Test
    @DisplayName("Rising clear of the hidden object's cell does not strike it")
    void risingBesideItDoesNothing() {
        // Prepare
        final LevelScenePlayer player = largePlayer(JUMPING, 96, 34, -2);
        final StaticEnvironmentCollisionGrid grid = gridWithAHiddenObject();

        // Execute
        grid.handleCollision(player, false);

        // Verify
        verify(hiddenObject, never()).onCollisionFromBelow(any());
        assertThat(player.getPosition().getDY()).as("Still rising").isEqualTo(-2);
    }

    @Test
    @DisplayName("A hidden object is not solid to the player's body")
    void itIsNotSolid() {
        // Prepare
        final LevelScenePlayer player = largePlayer(FALLING, 32, 34, 1);
        final StaticEnvironmentCollisionGrid grid = gridWithAHiddenObject();

        // Execute & Verify - its cell is open at the head and at the feet
        assertThat(grid.collidesAtOffset(player, of(8, 6))).as("Head").isFalse();
        assertThat(grid.collidesAtOffset(player, of(8, 32))).as("Feet").isFalse();
    }

    @Test
    @DisplayName("Hidden objects are erased from the layer that painted them and the others are left alone")
    void onlyHiddenObjectsAreErased() {
        // Prepare
        final HiddenObject visibleObject = mock(HiddenObject.class);
        when(visibleObject.isHidden()).thenReturn(false);
        final Geometry bakedLayer = mock(Geometry.class);
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS)).thenReturn(bakedLayer);
        final StaticEnvironmentCollisionGrid grid = gridWithAHiddenObject(gameEngine);
        grid.getObjects()[5][5] = visibleObject;
        final LevelSceneDimensions dimensions = new LevelSceneDimensions(SIZE, SIZE);

        // Execute
        grid.eraseHiddenObjects();

        // Verify
        verify(hiddenObject).eraseFromBakedTexture(bakedLayer, dimensions, of(HIDDEN_COLUMN, HIDDEN_ROW));
        verify(visibleObject, never()).eraseFromBakedTexture(any(), any(), any());
    }

    private StaticEnvironmentCollisionGrid gridWithAHiddenObject() {
        return gridWithAHiddenObject(null);
    }

    private StaticEnvironmentCollisionGrid gridWithAHiddenObject(final GameEngine gameEngine) {
        when(hiddenObject.isHidden()).thenReturn(true);
        when(hiddenObject.isCollidable()).thenReturn(false);
        final LevelObject[][] objects = emptyCells();
        objects[HIDDEN_ROW][HIDDEN_COLUMN] = hiddenObject;

        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            gameEngine,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(objects);
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(SIZE, SIZE));
        return grid;
    }

    private LevelObject[][] emptyCells() {
        final LevelObject[][] objects = new LevelObject[SIZE][SIZE];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private LevelScenePlayer largePlayer(
        final PlayerMovement movement,
        final double x,
        final double y,
        final double dy
    ) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        position.setDY(dy);
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(movement);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, UP));
        when(player.isLarge()).thenReturn(true);
        return player;
    }

    /** A level object that can be erased from a baked layer, like a block. */
    private interface HiddenObject extends LevelObject, BakedLayerPainter {
    }
}
