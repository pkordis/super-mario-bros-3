package house.x1337.app.smb3.game.object.level.card;

import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelCompletionCardType.MUSHROOM;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The card is hit-tested with bound box 1 ({@code OAT_BOUNDBOX01}: left 1, width 13, top 2, height 8; dasm
 * prg000.asm:277-290, 363), the top half of its sprite, against the player's object box, and it is tail-attack
 * immune ({@code OA3_TAILATKIMMUNE}, prg002.asm:273).
 */
class LevelCompletionCardCollisionTest {
    private static final Coordinates CELL = of(4, 3);
    private static final double CARD_X = CELL.x() * (double) TILE_SPRITE_SIZE;
    private static final double CARD_Y = CELL.y() * (double) TILE_SPRITE_SIZE;
    private static final AxisAlignedBoundingBox FAR_AWAY = new AxisAlignedBoundingBox(500, 500, 510, 525);

    private final LevelCompletion levelCompletion = mock(LevelCompletion.class);
    private final LevelScenePlayer player = mock(LevelScenePlayer.class);
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private LevelCompletionCard card;

    @BeforeEach
    void prepare() {
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelCompletion()).thenReturn(levelCompletion);
        LevelCompletionCardScene.prepare(gameEngine, mock(LevelScene.class));
        broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        card = LevelCompletionCardScene.place(gameEngine, CELL);
        broadPhase.insert(card);
    }

    @Test
    @DisplayName("A player box reaching into the top half collects the card with the face showing")
    void theTopHalfCollects() {
        // Prepare - the box's bottom edge two pixels into the card's top half
        when(player.getObjectCollisionBounds())
            .thenReturn(new AxisAlignedBoundingBox(CARD_X + 4, CARD_Y - 20, CARD_X + 12, CARD_Y + 3));

        // Execute
        broadPhase.resolveActiveObjectCollisions(List.of(player));

        // Verify
        verify(levelCompletion).collect(MUSHROOM, player);
        assertThat(card.isCollected()).isTrue();
    }

    @Test
    @DisplayName("A player box only in the bottom half does not collect it")
    void theBottomHalfDoesNot() {
        // Prepare - y 11 to 16 of the sprite; the bound box ends at y 10
        when(player.getObjectCollisionBounds())
            .thenReturn(new AxisAlignedBoundingBox(CARD_X, CARD_Y + 11, CARD_X + 16, CARD_Y + 16));

        // Execute
        broadPhase.resolveActiveObjectCollisions(List.of(player));

        // Verify
        verify(levelCompletion, never()).collect(any(), any());
        assertThat(card.isCollected()).isFalse();
    }

    @Test
    @DisplayName("The bound box is one pixel in from the sprite's left edge and runs 13 pixels wide")
    void theBoxIsInsetHorizontally() {
        // Prepare & Execute
        final AxisAlignedBoundingBox bounds = card.getBounds();

        // Verify - widened by a pixel on each side for the half-open test, as the power-ups' box is
        assertThat(bounds.left()).isEqualTo(CARD_X);
        assertThat(bounds.right()).isEqualTo(CARD_X + 15);
        assertThat(bounds.top()).isEqualTo(CARD_Y + 1);
        assertThat(bounds.bottom()).isEqualTo(CARD_Y + 11);
    }

    @Test
    @DisplayName("The tail striking the card does not collect it")
    void theTailDoesNotCollect() {
        // Prepare - the body is far away, the tail covers the whole card
        when(player.getObjectCollisionBounds()).thenReturn(FAR_AWAY);
        when(player.isTailAttackStriking()).thenReturn(true);
        when(player.getTailAttackBounds())
            .thenReturn(new AxisAlignedBoundingBox(CARD_X, CARD_Y, CARD_X + 16, CARD_Y + 16));

        // Execute
        broadPhase.resolveActiveObjectCollisions(List.of(player));

        // Verify
        verify(levelCompletion, never()).collect(any(), any());
        assertThat(card.isCollected()).isFalse();
    }

    @Test
    @DisplayName("Staying on the card collects it once")
    void aCardIsCollectedOnce() {
        // Prepare
        when(player.getObjectCollisionBounds())
            .thenReturn(new AxisAlignedBoundingBox(CARD_X, CARD_Y, CARD_X + 16, CARD_Y + 16));

        // Execute
        broadPhase.resolveActiveObjectCollisions(List.of(player));
        broadPhase.resolveActiveObjectCollisions(List.of(player));

        // Verify
        verify(levelCompletion, times(1)).collect(any(), any());
    }

    @Test
    @DisplayName("A kicked or carried shell passes through the card")
    void shellsPassThrough() {
        // Execute & Verify
        assertThat(card.isKilledByRammedObject()).isFalse();
    }
}
