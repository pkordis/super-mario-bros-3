package house.x1337.app.smb3.game.object.level.projectile;

import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.enumeration.PlayerVisibility.BACKGROUND;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code SObj_PlayerCollide} (dasm prg007.asm:4772-4838) for the piranha fireball. With {@code i = 0} for a
 * small or ducking player and {@code 1} otherwise: {@code 0 <= Y + 8 - Player_Y - {18, 10}[i] < {$10, $16}[i]}
 * and {@code 0 <= X + 6 - Player_X < 16}, tested only on ticks where {@code slot + Counter_1} is odd.
 */
class PiranhaFireballHitTest {
    private static final double PLAYER_X = 100;
    private static final double PLAYER_Y = 80;

    private PiranhaFireballTestWorld world;

    @BeforeEach
    void prepare() {
        world = new PiranhaFireballTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // The box
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A large standing player is hit with the fireball's X in [X - 6, X + 10) and Y in [Y + 2, Y + 24)")
    void theLargePlayersBox() {
        // Prepare
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);

        // Execute & Verify - SObjYOff_PlayerSize[1] = 10, SObj_VLimit[1] = $16
        assertHit(player, PLAYER_X - 6, PLAYER_Y + 2, true);
        assertHit(player, PLAYER_X - 7, PLAYER_Y + 2, false);
        assertHit(player, PLAYER_X + 9, PLAYER_Y + 2, true);
        assertHit(player, PLAYER_X + 10, PLAYER_Y + 2, false);
        assertHit(player, PLAYER_X, PLAYER_Y + 1, false);
        assertHit(player, PLAYER_X, PLAYER_Y + 23, true);
        assertHit(player, PLAYER_X, PLAYER_Y + 24, false);
    }

    @Test
    @DisplayName("A small player is hit with the fireball's Y in [Y + 10, Y + 26)")
    void theSmallPlayersBox() {
        // Prepare
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);
        when(player.isSmall()).thenReturn(true);

        // Execute & Verify - SObjYOff_PlayerSize[0] = 18, SObj_VLimit[0] = $10
        assertHit(player, PLAYER_X, PLAYER_Y + 9, false);
        assertHit(player, PLAYER_X, PLAYER_Y + 10, true);
        assertHit(player, PLAYER_X, PLAYER_Y + 25, true);
        assertHit(player, PLAYER_X, PLAYER_Y + 26, false);
        assertHit(player, PLAYER_X - 6, PLAYER_Y + 10, true);
        assertHit(player, PLAYER_X + 10, PLAYER_Y + 10, false);
    }

    @Test
    @DisplayName("A ducking large player gets the small player's box")
    void aDuckingPlayerGetsTheSmallBox() {
        // Prepare
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);
        player.getRuntimeState().setDucking(true);

        // Execute & Verify
        assertHit(player, PLAYER_X, PLAYER_Y + 2, false);
        assertHit(player, PLAYER_X, PLAYER_Y + 10, true);
    }

    // -------------------------------------------------------------------------
    // When it hurts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It tests only on alternate ticks, and keeps flying after a hit")
    void itTestsOnAlternateTicksAndFliesOn() {
        // Prepare - a fireball hanging on the player
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);
        final PiranhaFireball fireball = world.spawn(PLAYER_X, PLAYER_Y + 10, 0, 0);

        // Execute
        world.tick(10);

        // Verify
        verify(player, times(5)).getHurtBy(fireball);
        assertThat(world.getManager().getActiveFireballs()).containsExactly(fireball);
        assertThat(fireball.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Two fireballs in neighbouring slots test on opposite ticks")
    void neighbouringSlotsTestOnOppositeTicks() {
        // Prepare
        final PiranhaFireball first = world.spawn(10, 10, 0, 0);
        final PiranhaFireball second = world.spawn(20, 10, 0, 0);

        // Execute & Verify
        for (int tick = 0; tick < 4; tick++) {
            world.tick(1);
            assertThat(world.getManager().isHitTestTick(first))
                .as("Tick %d", tick)
                .isNotEqualTo(world.getManager().isHitTestTick(second));
        }
        assertThat(List.of(first.getSlot(), second.getSlot())).containsExactly(3, 2);
    }

    @Test
    @DisplayName("A player still flashing from a hit is not hurt again")
    void aFlashingPlayerIsNotHurt() {
        // Prepare - the player's real getHurtBy, so its own gate is what stops the hit
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);
        doCallRealMethod().when(player).getHurtBy(any());
        doCallRealMethod().when(player).isHurtable();
        player.getRuntimeState().setHurtInvincibilityCounter(60);
        world.spawn(PLAYER_X, PLAYER_Y + 10, 0, 0);

        // Execute
        world.tick(4);

        // Verify - the fireball did reach the player; the player's own gate refused it
        verify(player, atLeastOnce()).getHurtBy(any());
        verify(player, never()).die();
        verify(player, never()).turnToShrunk();
    }

    @Test
    @DisplayName("A player hidden behind the scenery is not hit")
    void aBackgroundPlayerIsNotHit() {
        // Prepare
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);
        when(player.getVisibility()).thenReturn(BACKGROUND);
        world.spawn(PLAYER_X, PLAYER_Y + 10, 0, 0);

        // Execute
        world.tick(4);

        // Verify
        verify(player, never()).getHurtBy(any());
    }

    @Test
    @DisplayName("During a halt it neither moves, animates nor hurts, and it stays drawn")
    void aHaltFreezesIt() {
        // Prepare
        final LevelScenePlayer player = world.playerAt(PLAYER_X, PLAYER_Y);
        final PiranhaFireball fireball = world.spawn(PLAYER_X, PLAYER_Y + 10, 0x0B, 0);
        final double x = fireball.getPosition().x();

        // Execute
        for (int tick = 0; tick < 10; tick++) {
            world.getManager().updateWhileHalted();
        }

        // Verify
        assertThat(fireball.getPosition().x()).isEqualTo(x);
        assertThat(fireball.getAnimationCounter()).isZero();
        verify(player, never()).getHurtBy(any());
        assertThat(world.getRootNode().hasChild(fireball.getSpriteGeometry())).isTrue();
    }

    @Test
    @DisplayName("Nothing but its own manager sees it: it never touches the broad-phase")
    void itStaysOutOfTheBroadPhase() {
        // Prepare
        world.playerAt(PLAYER_X, PLAYER_Y);
        world.spawn(PLAYER_X, PLAYER_Y + 10, 0x0B, 0);

        // Execute
        world.tick(10);

        // Verify - so the tail, shells and enemies cannot find it
        verify(world.getGameEngine(), never()).getActiveObjectGrid();
    }

    private void assertHit(
        final LevelScenePlayer player,
        final double fireballX,
        final double fireballY,
        final boolean expected
    ) {
        final PiranhaFireball fireball = new PiranhaFireball(
            world.getGameEngine()
        );
        fireball.spawnIntoScene(
            0,
            new PixelPosition(fireballX, fireballY),
            STILL
        );
        assertThat(fireball.overlaps(player))
            .as("Fireball at (%s, %s)", fireballX - PLAYER_X, fireballY - PLAYER_Y)
            .isEqualTo(expected);
    }
}
