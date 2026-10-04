package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_FALL_MAX;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_PAUSE_TICKS;
import static house.x1337.app.smb3.GameConstants.PLAYER_DEATH_RISE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.game.player.level.LevelScenePlayer.applyDeathGravityStep;
import static house.x1337.app.smb3.game.player.level.LevelScenePlayer.hasFallenOffLevel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the death throe's Y motion and off-screen completion (dasm {@code Player_Die} launch of
 * {@code Player_YVel = -64} + the per-tick gravity in prg029 PRG029_D768: {@code INC Player_YVel}
 * twice, applied with the {@code FALLRATE_MAX} cap). The body rises from the launch velocity,
 * decelerates to an apex, then falls — capped at the terminal rate — until it clears the bottom of the
 * level, which is when the engine is told to restart.
 */
class PlayerDeathThroeTest {
    private static final int ROWS = 12;
    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("The throe opens with a 48-tick still pause before any motion")
    void opensWithAStillPause() {
        // Prepare - a dead player holding the launch velocity but still in its pause window
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.startDying();
        runtimeState.setDeathPauseCounter(PLAYER_DEATH_PAUSE_TICKS);

        // Verify - dasm Event_Countdown = $30 = 48, and the body is in the pause phase (drawn, not moving)
        assertThat(PLAYER_DEATH_PAUSE_TICKS).as("Event_Countdown = $30").isEqualTo(48);
        assertThat(runtimeState.isDeathPausing()).as("in the still pause").isTrue();

        // Execute - run the pause out tick by tick; it must stay in the pause for all 48, then release
        for (int tick = 0; tick < PLAYER_DEATH_PAUSE_TICKS; tick++) {
            assertThat(runtimeState.isDeathPausing())
                .as("still paused at tick %d of %d", tick, PLAYER_DEATH_PAUSE_TICKS)
                .isTrue();
            runtimeState.decrementDeathPause();
        }

        // Verify - the pause is spent, so the motion phase may begin, but the player is still dying
        assertThat(runtimeState.isDeathPausing()).as("pause spent after 48 ticks").isFalse();
        assertThat(runtimeState.isDying()).as("still dying — now in the motion phase").isTrue();
    }

    @Test
    @DisplayName("The pause only applies while dying, never to a live player")
    void pauseIsScopedToDying() {
        final PlayerRuntimeState live = new PlayerRuntimeState();
        live.setDeathPauseCounter(PLAYER_DEATH_PAUSE_TICKS);
        assertThat(live.isDeathPausing()).as("a counter without the dying flag is not a pause").isFalse();
    }

    @Test
    @DisplayName("Each tick adds gravity to the velocity and moves on Y by it")
    void gravityStepIntegratesVelocityThenMovesY() {
        // Prepare - freshly launched upward at the ROM's -64/16 = -4.0 px/frame
        final PlayerPosition position = positionWith(4.0 * TILE_SPRITE_SIZE, PLAYER_DEATH_RISE_YVEL);
        final double startX = position.getX();
        final double startY = position.getY();

        // Execute - one tick
        applyDeathGravityStep(position);

        // Verify - velocity eased by +gravity (2/16), Y moved by the new velocity, X untouched
        assertThat(position.getDY())
            .as("gravity eased the rise by 2/16")
            .isCloseTo(PLAYER_DEATH_RISE_YVEL + 2.0 / TILE_SPRITE_SIZE, within(TOLERANCE));
        assertThat(position.getY())
            .as("still rising, so Y decreased by the (still negative) velocity")
            .isCloseTo(startY + position.getDY(), within(TOLERANCE));
        assertThat(position.getY()).as("rose on the first tick").isLessThan(startY);
        assertThat(position.getX()).as("death moves on the Y axis only").isCloseTo(startX, within(TOLERANCE));
    }

    @Test
    @DisplayName("The throe rises to an apex above its start, then falls back past it")
    void risesToApexThenFalls() {
        final PlayerPosition position = positionWith(4.0 * TILE_SPRITE_SIZE, PLAYER_DEATH_RISE_YVEL);
        final double startY = position.getY();

        double highestY = position.getY();
        boolean startedFalling = false;
        for (int tick = 0; tick < 80; tick++) {
            final double before = position.getY();
            applyDeathGravityStep(position);
            highestY = Math.min(highestY, position.getY());
            if (position.getY() > before) {
                startedFalling = true;
            }
        }

        assertThat(highestY).as("rose above its start before falling").isLessThan(startY);
        assertThat(startedFalling).as("began falling after the apex").isTrue();
        assertThat(position.getY()).as("ended well below the start, on its way down").isGreaterThan(startY);
    }

    @Test
    @DisplayName("The fall never exceeds the terminal rate")
    void fallIsCappedAtTerminalRate() {
        final PlayerPosition position = positionWith(4.0 * TILE_SPRITE_SIZE, PLAYER_DEATH_RISE_YVEL);

        double maxFallSpeed = 0;
        for (int tick = 0; tick < 400; tick++) {
            applyDeathGravityStep(position);
            maxFallSpeed = Math.max(maxFallSpeed, position.getDY());
        }

        assertThat(maxFallSpeed)
            .as("fall capped at FALLRATE_MAX = $40/16 = 4.0 px/frame")
            .isLessThanOrEqualTo(PLAYER_DEATH_FALL_MAX + TOLERANCE);
        assertThat(maxFallSpeed)
            .as("and actually reaches the cap given enough fall")
            .isCloseTo(PLAYER_DEATH_FALL_MAX, within(TOLERANCE));
    }

    @Test
    @DisplayName("Off-level completion fires only once the body is a tile below the bottom edge")
    void offLevelPredicateNeedsAFullTileOfMargin() {
        final PlayerPosition atBottomEdge = positionWith(4.0 * TILE_SPRITE_SIZE, 0);
        atBottomEdge.setY((double) ROWS * TILE_SPRITE_SIZE);
        assertThat(hasFallenOffLevel(atBottomEdge, ROWS))
            .as("level with the bottom edge is not yet off-screen")
            .isFalse();

        final PlayerPosition justBelowEdge = positionWith(4.0 * TILE_SPRITE_SIZE, 0);
        justBelowEdge.setY((double) (ROWS + 1) * TILE_SPRITE_SIZE);
        assertThat(hasFallenOffLevel(justBelowEdge, ROWS))
            .as("exactly one tile below is still on the boundary, not past it")
            .isFalse();

        final PlayerPosition clear = positionWith(4.0 * TILE_SPRITE_SIZE, 0);
        clear.setY((double) (ROWS + 1) * TILE_SPRITE_SIZE + 1);
        assertThat(hasFallenOffLevel(clear, ROWS))
            .as("a tile and a pixel below has cleared the view")
            .isTrue();
    }

    @Test
    @DisplayName("Driven to completion, the throe eventually clears the bottom of the level")
    void throeEventuallyClearsTheLevel() {
        final PlayerPosition position = positionWith(4.0 * TILE_SPRITE_SIZE, PLAYER_DEATH_RISE_YVEL);

        boolean cleared = false;
        for (int tick = 0; tick < 400 && !cleared; tick++) {
            applyDeathGravityStep(position);
            cleared = hasFallenOffLevel(position, ROWS);
        }

        assertThat(cleared).as("the body falls off the bottom within the throe").isTrue();
        assertThat(position.getY()).isGreaterThan((double) (ROWS + 1) * TILE_SPRITE_SIZE);
    }

    private static PlayerPosition positionWith(final double x, final double dy) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(4.0 * TILE_SPRITE_SIZE);
        position.setDY(dy);
        position.snapshotPrevious();
        return position;
    }
}
