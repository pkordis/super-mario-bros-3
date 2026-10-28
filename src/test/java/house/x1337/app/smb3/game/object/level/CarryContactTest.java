package house.x1337.app.smb3.game.object.level;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.object.level.CarriableLevelObject.CARRY_HIT_KNOCK_X_VELOCITY_FIXED_POINT;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CarriableLevelObject#onCarriedInto}'s own behaviour, exercised through a carriable that is
 * <b>not</b> an enemy — an ice block or a P-switch, neither of which exists yet.
 *
 * <p>Worth its own file because the interesting half of the contract is what happens to something with no
 * death of its own to die. The ROM writes {@code OBJSTATE_KILLED} into both slots and is done
 * (prg000.asm:3106-3120), which only reads as "killed" for an object whose handler understands that
 * state; for anything else it simply means "gone". {@link ActiveLevelObject#expire()} is that meaning,
 * and the default routes to it. {@code KoopaTroopaCarryTest} covers the enemy half, where the state and
 * the upward throw are real.
 */
class CarryContactTest {

    @Test
    @DisplayName("A carriable with no death of its own leaves the world by expiry instead")
    void aNonEnemyCarriableExpires() {
        // Prepare - held at the player's right, with something standing further right again
        final StubCarriable carriable = heldAt(91);
        final ActiveLevelObject victim = objectAt(95);

        // Execute
        carriable.onCarriedInto(victim);

        // Verify
        assertThat(carriable.expired).as("retired from its manager's list, nothing more").isTrue();
        verify(victim).onStruckByKickedObject(CARRY_HIT_KNOCK_X_VELOCITY_FIXED_POINT);
    }

    @Test
    @DisplayName("The victim is still knocked away from the player, whatever is doing the carrying")
    void theVictimIsKnockedAwayFromThePlayer() {
        // Level_ObjCalcXDiffs is asked about the VICTIM against the player (prg000.asm:3130-3132), so the
        // answer does not depend on which side of the player the carried object happens to be tucked.
        // Prepare - held at the player's left this time, with the victim left of the player
        final StubCarriable carriable = heldAt(69);
        final ActiveLevelObject victim = objectAt(60);

        // Execute
        carriable.onCarriedInto(victim);

        // Verify
        verify(victim).onStruckByKickedObject(-CARRY_HIT_KNOCK_X_VELOCITY_FIXED_POINT);
    }

    @Test
    @DisplayName("Nothing happens to an object nobody is holding")
    void anUnheldCarriableIsInert() {
        // Prepare
        final StubCarriable carriable = new StubCarriable(null);

        // Execute
        carriable.onCarriedInto(objectAt(95));

        // Verify
        assertThat(carriable.expired).isFalse();
    }

    /** A carriable held by a player standing at X 80, placed at the given X itself. */
    private static StubCarriable heldAt(final double x) {
        final PlayerPosition playerPosition = new PlayerPosition();
        playerPosition.setX(80);
        final LevelScenePlayer holder = mock(LevelScenePlayer.class);
        when(holder.getPosition()).thenReturn(playerPosition);
        final StubCarriable carriable = new StubCarriable(holder);
        carriable.position = new PixelPosition(x, 0);
        return carriable;
    }

    private static ActiveLevelObject objectAt(final double left) {
        final ActiveLevelObject object = mock(ActiveLevelObject.class);
        when(object.getBounds())
            .thenReturn(AxisAlignedBoundingBox.ofSize(left, 0, new DimensionsPixels(16, 16)));
        return object;
    }

    /**
     * The minimum a carriable can be: no state machine, no expiry flag of its own beyond the one
     * {@code expire()} is overridden to set, and nothing the renderer needs.
     */
    private static final class StubCarriable implements CarriableLevelObject {
        private final LevelScenePlayer carrier;
        private PixelPosition position = new PixelPosition(0, 0);
        private boolean expired;

        private StubCarriable(final LevelScenePlayer carrier) {
            this.carrier = carrier;
        }

        @Override
        public void expire() {
            expired = true;
        }

        @Override
        public LevelScenePlayer getCarrier() {
            return carrier;
        }

        @Override
        public boolean isCarried() {
            return carrier != null;
        }

        @Override
        public boolean isCarriedHazard() {
            return carrier != null;
        }

        @Override
        public PixelPosition getPosition() {
            return position;
        }

        @Override
        public AxisAlignedBoundingBox getBounds() {
            return AxisAlignedBoundingBox.ofSize(position, new DimensionsPixels(16, 16));
        }

        @Override
        public boolean isCarriable() {
            return true;
        }

        @Override
        public void onCarried(final LevelScenePlayer player) {
            throw doNotCall();
        }

        @Override
        public void onKicked(final LevelScenePlayer player, final boolean toTheRight) {
            throw doNotCall();
        }

        @Override
        public void drawCarriedAt(final PixelPosition anchor) {
            throw doNotCall();
        }

        @Override
        public FixedPointVelocity getVelocity() {
            return STILL;
        }

        @Override
        public Dimensions getSpriteDimensions() {
            throw doNotCall();
        }

        @Override
        public Geometry getSpriteGeometry() {
            return null;
        }

        @Override
        public void onCollisionWith(final LevelScenePlayer player) {
            throw doNotCall();
        }

        @Override
        public GameEngine getGameEngine() {
            throw doNotCall();
        }

        @Override
        public LevelObjectType getType() {
            throw doNotCall();
        }

        @Override
        public Offset getOffset() {
            throw doNotCall();
        }

        private RuntimeException doNotCall() {
            return new UnsupportedOperationException("This method should not be called");
        }
    }
}
