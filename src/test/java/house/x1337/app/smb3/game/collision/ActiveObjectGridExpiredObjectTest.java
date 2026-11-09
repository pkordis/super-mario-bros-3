package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An object retired earlier in the collision pass, as the card touch retires everything on screen, is left alone by
 * the rest of the pass: the card is resolved before the other objects, which by then are poofing.
 */
class ActiveObjectGridExpiredObjectTest {
    private static final AxisAlignedBoundingBox BOX = new AxisAlignedBoundingBox(32, 32, 48, 48);

    @Test
    @DisplayName("An expired object is neither touched nor struck by the tail")
    void anExpiredObjectIsLeftAlone() {
        // Prepare
        final ActiveObjectGrid<ActiveLevelObject> grid = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        final ActiveLevelObject expired = objectAt(true);
        final ActiveLevelObject live = objectAt(false);
        grid.insert(expired);
        grid.insert(live);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getObjectCollisionBounds()).thenReturn(BOX);
        when(player.isTailAttackStriking()).thenReturn(true);
        when(player.getTailAttackBounds()).thenReturn(BOX);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        verify(expired, never()).onCollisionWith(any());
        verify(expired, never()).onTailAttack(any());
        verify(live).onCollisionWith(player);
        verify(live).onTailAttack(player);
    }

    private static ActiveLevelObject objectAt(final boolean expired) {
        final ActiveLevelObject object = mock(ActiveLevelObject.class);
        when(object.getBounds()).thenReturn(BOX);
        when(object.intersects(any())).thenReturn(true);
        when(object.isExpired()).thenReturn(expired);
        return object;
    }
}
