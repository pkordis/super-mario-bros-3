package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_Y_VELOCITY;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_ENEMY_DETAIL;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.KILLED;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaState.SHELLED;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopa.FLIGHT_HOLD_TICKS;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.FLOOR_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.REST_ROW;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.REST_ROW_PIXEL_Y;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.ROWS;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.START_COLUMN;
import static house.x1337.app.smb3.game.object.level.enemy.KoopaParaTroopaTestWorld.descendingPlayer;
import static house.x1337.app.smb3.game.object.level.enemy.placement.EnemyDepthAllocator.baseDepthOfSlot;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The red Koopa Paratroopa. It never moves in X and ignores terrain: each tick it adds its Y velocity, then, only
 * while the flight timer is 0, every 4th flight tick nudges the Y velocity by the acceleration for its direction
 * and, at the speed limit, reverses the direction and holds for a flight timer of 48. The timer counts down before
 * the flight runs. Defeated, it hands its slot to a red troopa.
 */
class KoopaParaTroopaFlightTest {
    private static final double TOLERANCE = 1e-9;
    private static final float DEPTH_TOLERANCE = 1e-6f;
    private static final int FLIGHT_ROW = 1;
    private static final int PERIOD_TICKS = 350;
    private static final int LOWEST_DROP_PIXELS = 111;
    private static final int STOMP_HIT_COOLDOWN_TICKS = 8;
    private static final int TAIL_LAUNCH_Y_VELOCITY = -80;
    private static final int KNOCK_X_VELOCITY = 8;
    private static final int SHELL_KNOCK_RIGHT = 16;
    private static final int WALK_SPEED = 8;
    private static final double WALK_STEP_PIXELS = WALK_SPEED / (double) TILE_SPRITE_SIZE;

    /**
     * {tick, Y velocity after the tick, pixels below the spawn height after the tick}, worked out by hand from
     * its counters. The hold lasts 47 timer ticks plus the 4 that bring the flight tick count back to a multiple
     * of 4, so a half period is 47 + 32 * 4 = 175 ticks and the low point is 30 + 51 + 30 = 111 px.
     */
    private static final int[][] TRACK = {
        {4, 1, 0},
        {64, 16, 30},
        {114, 16, 80},
        {115, 15, 81},
        {175, 0, 111},
        {239, -16, 81},
        {289, -16, 31},
        {290, -15, 30},
        {350, 0, 0},
        {414, 16, 30}
    };

    private KoopaParaTroopaTestWorld world;

    @BeforeEach
    void prepare() {
        world = new KoopaParaTroopaTestWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    // -------------------------------------------------------------------------
    // The track
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It flies down first and follows its counter-driven track, tick for tick")
    void followsTheTrack() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        final double spawnY = paratroopa.getPosition().y();
        int tick = 0;

        for (final int[] checkpoint : TRACK) {
            // Execute
            world.tick(paratroopa, checkpoint[0] - tick);
            tick = checkpoint[0];

            // Verify
            assertThat(paratroopa.getVelocity().y()).as("Y velocity after tick %d", tick).isEqualTo(checkpoint[1]);
            assertThat(paratroopa.getPosition().y() - spawnY)
                .as("Drop after tick %d", tick)
                .isCloseTo(checkpoint[2], within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("Reaching the limit reverses the direction and holds the speed for 48 ticks")
    void reachingTheLimitStartsTheHold() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);

        // Execute
        world.tick(paratroopa, 64);

        // Verify
        assertThat(paratroopa.getVelocity().y()).as("The downward speed limit").isEqualTo(16);
        assertThat(paratroopa.getFlightDirection() & 1).as("The direction has advanced").isEqualTo(1);
        assertThat(paratroopa.getFlightTimer()).isEqualTo(FLIGHT_HOLD_TICKS);
    }

    @Test
    @DisplayName("It cycles between its spawn height and 111 px below it, every 350 ticks")
    void cyclesBetweenTheSpawnHeightAndOneHundredElevenBelow() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        final double spawnY = paratroopa.getPosition().y();
        final List<Double> drops = new ArrayList<>();
        final List<Integer> velocities = new ArrayList<>();

        // Execute
        for (int tick = 0; tick < 2 * PERIOD_TICKS; tick++) {
            paratroopa.motionUpdate();
            drops.add(paratroopa.getPosition().y() - spawnY);
            velocities.add(paratroopa.getVelocity().y());
        }

        // Verify
        assertThat(drops.stream().mapToDouble(Double::doubleValue).min().orElseThrow()).isZero();
        assertThat(drops.stream().mapToDouble(Double::doubleValue).max().orElseThrow())
            .isCloseTo(LOWEST_DROP_PIXELS, within(TOLERANCE));
        assertThat(drops.subList(PERIOD_TICKS, 2 * PERIOD_TICKS)).isEqualTo(drops.subList(0, PERIOD_TICKS));
        assertThat(velocities.subList(PERIOD_TICKS, 2 * PERIOD_TICKS))
            .isEqualTo(velocities.subList(0, PERIOD_TICKS));
    }

    @Test
    @DisplayName("It flies through blocks: no terrain stops it or turns it")
    void fliesThroughBlocks() {
        // Prepare - two rows of blocks across its track and a wall on each side of it
        world.solidRow(FLIGHT_ROW + 3);
        world.solidRow(FLIGHT_ROW + 5);
        world.solidColumn(START_COLUMN - 1);
        world.solidColumn(START_COLUMN + 1);
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        paratroopa.setFacingRight(true);
        final double spawnX = paratroopa.getPosition().x();
        final double spawnY = paratroopa.getPosition().y();

        // Execute
        world.tick(paratroopa, 175);

        // Verify
        assertThat(paratroopa.getPosition().y() - spawnY).isCloseTo(LOWEST_DROP_PIXELS, within(TOLERANCE));
        assertThat(paratroopa.getPosition().x()).as("No X movement at all").isCloseTo(spawnX, within(TOLERANCE));
        assertThat(paratroopa.getVelocity().x()).isZero();
        assertThat(paratroopa.isFacingRight()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Facing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("It faces the nearest player when it appears and keeps that facing afterwards")
    void facesThePlayerOnceAndKeepsIt() {
        // Prepare - the player on the right at spawn time
        world.playerAtPixelX((START_COLUMN + 6) * (double) TILE_SPRITE_SIZE);
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        paratroopa.spawnIntoScene();
        assertThat(paratroopa.isFacingRight()).as("It faces the player as it appears").isTrue();

        // Execute - the player moves to its left
        world.playerAtPixelX(0);
        world.tick(paratroopa, PERIOD_TICKS);

        // Verify
        assertThat(paratroopa.isFacingRight()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The body frame toggles every 8 ticks and the wing beats 4 up, 4 down")
    void theWingBeatsTwiceAsFastAsTheHoppers() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        final List<Integer> frameChanges = new ArrayList<>();
        final List<Integer> beats = new ArrayList<>();
        int frame = paratroopa.getWalkFrameIndex();
        boolean wingUp = paratroopa.isWingUp();

        // Execute
        for (int tick = 0; tick < 100; tick++) {
            paratroopa.motionUpdate();
            if (paratroopa.getWalkFrameIndex() != frame) {
                frame = paratroopa.getWalkFrameIndex();
                frameChanges.add(tick);
            }
            if (paratroopa.isWingUp() != wingUp) {
                wingUp = paratroopa.isWingUp();
                beats.add(tick);
            }
        }

        // Verify - (clock >> 3) & 1 and (clock + 3) & 4, with the animation clock advancing every frame
        assertThat(frameChanges).hasSizeGreaterThan(4);
        for (int change = 1; change < frameChanges.size(); change++) {
            assertThat(frameChanges.get(change) - frameChanges.get(change - 1)).as("Change %d", change)
                .isEqualTo(8);
        }
        assertThat(beats).hasSizeGreaterThan(8);
        for (int beat = 1; beat < beats.size(); beat++) {
            assertThat(beats.get(beat) - beats.get(beat - 1)).as("Beat %d", beat).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("The wing is drawn just in front of its own body")
    void theWingIsInFrontOfItsBody() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        paratroopa.setBaseDepth(baseDepthOfSlot(4));
        paratroopa.spawnIntoScene();

        // Execute
        world.tick(paratroopa, 3);

        // Verify
        final float body = paratroopa.getSpriteGeometry().getLocalTranslation().z;
        final float wing = paratroopa.getWingGeometry().getLocalTranslation().z;
        assertThat(body).isCloseTo(baseDepthOfSlot(4), within(DEPTH_TOLERANCE));
        assertThat(wing).isCloseTo(baseDepthOfSlot(4) + Z_OFFSET_ENEMY_DETAIL, within(DEPTH_TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Other objects
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A walking enemy bounces off it, but it does not turn itself")
    void walkersBounceOffItButItDoesNotTurn() {
        // Prepare - a troopa walking left into the flyer's right side; the flyer faces it
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, REST_ROW);
        paratroopa.setFacingRight(true);
        final KoopaTroopa walker = new KoopaTroopa(world.getGameEngine(), of(START_COLUMN + 1, REST_ROW));
        walker.init();
        walker.setFacingRight(false);

        // Execute
        walker.onSideCollisionWith(paratroopa);
        paratroopa.onSideCollisionWith(walker);

        // Verify - other objects bounce off it, but its flight never turns it away from them
        assertThat(paratroopa.bouncesOffOtherObjects()).isTrue();
        assertThat(walker.isFacingRight()).as("Turned away from the flyer").isTrue();
        assertThat(paratroopa.isFacingRight()).isTrue();
    }

    @Test
    @DisplayName("The green hopper still turns away from a walker it meets side-on")
    void theGreenHopperStillTurns() {
        // Prepare
        final KoopaParaTroopa hopper = world.paratroopaAt(START_COLUMN, REST_ROW);
        hopper.setFacingRight(true);
        final KoopaTroopa walker = new KoopaTroopa(world.getGameEngine(), of(START_COLUMN + 1, REST_ROW));
        walker.init();

        // Execute
        hopper.onSideCollisionWith(walker);

        // Verify
        assertThat(hopper.isFacingRight()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Stomp
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A stomp sheds the wing: a walking red troopa at the same spot, with the ledge guard latched")
    void aStompLeavesAWalkingRedTroopa() {
        // Prepare - caught mid-flight, facing right
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        paratroopa.setFacingRight(true);
        world.tick(paratroopa, 100);
        final double x = paratroopa.getPosition().x();
        final double y = paratroopa.getPosition().y();
        final LevelScenePlayer player = descendingPlayer();

        // Execute
        paratroopa.onCollisionFromAbove(player);
        paratroopa.onCollisionFromAbove(descendingPlayer());

        // Verify - the stomp swaps in a red troopa and zeroes its Y velocity; the flight tick count is kept
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getMode()).isEqualTo(RED);
        assertThat(troopa.getState()).isEqualTo(NORMAL);
        assertThat(troopa.getPosition().x()).isCloseTo(x, within(TOLERANCE));
        assertThat(troopa.getPosition().y()).isCloseTo(y, within(TOLERANCE));
        assertThat(troopa.isFacingRight()).isTrue();
        assertThat(troopa.getVelocity().x()).isEqualTo(WALK_SPEED);
        assertThat(troopa.getVelocity().y()).isZero();
        assertThat(troopa.isLedgeTurned()).as("A nonzero flight tick count latches the guard").isTrue();
        assertThat(troopa.getHitCooldownTicks()).isEqualTo(STOMP_HIT_COOLDOWN_TICKS);
        assertThat(paratroopa.getPendingScoreReward()).isEqualTo(SCORE_100);
        assertThat(troopa.getPendingScoreReward()).isNull();
        assertThat(player.getPosition().getDY()).isCloseTo(PLAYER_STOMP_BOUNCE_Y_VELOCITY, within(TOLERANCE));
        verify(world.getActiveObjectGrid(), times(1).description("One hand-over only"))
            .spawnEnemy(any(EnemyLevelObject.class));
        assertThat(paratroopa.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Stomped above a pit, the red troopa falls on its way without turning back")
    void aboveAPitTheTroopaFallsWithoutTurning() {
        // Prepare
        world.openFloorFrom(0);
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        paratroopa.setFacingRight(true);
        world.tick(paratroopa, 100);
        paratroopa.onCollisionFromAbove(descendingPlayer());
        final KoopaTroopa troopa = world.capturedReplacement();
        final double startX = troopa.getPosition().x();
        final double startY = troopa.getPosition().y();
        double previousY = startY;

        for (int tick = 1; tick <= 30; tick++) {
            // Execute
            troopa.motionUpdate();

            // Verify - no about-face, no two steps back and no snap to a tile row
            assertThat(troopa.isFacingRight()).as("Facing after tick %d", tick).isTrue();
            assertThat(troopa.getPosition().x())
                .as("X after tick %d", tick)
                .isCloseTo(startX + tick * WALK_STEP_PIXELS, within(TOLERANCE));
            assertThat(troopa.getPosition().y()).as("Y after tick %d", tick).isGreaterThanOrEqualTo(previousY);
            previousY = troopa.getPosition().y();
        }
        assertThat(troopa.isGrounded()).isFalse();
        assertThat(previousY).as("Falling under normal gravity").isGreaterThan(startY);
    }

    @Test
    @DisplayName("Once it lands, the red troopa clears the latch and turns back at the next ledge")
    void afterLandingItGuardsLedges() {
        // Prepare - a short floor under the flyer, with open air on both sides
        world.openFloorFrom(0);
        for (int column = START_COLUMN - 2; column <= START_COLUMN + 3; column++) {
            world.getSolids()[FLOOR_ROW][column] = true;
        }
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        paratroopa.setFacingRight(true);
        world.tick(paratroopa, 64);
        paratroopa.onCollisionFromAbove(descendingPlayer());
        final KoopaTroopa troopa = world.capturedReplacement();

        // Execute - the fall
        int ticks = 0;
        while (!troopa.isGrounded() && ticks < 100) {
            assertThat(troopa.isLedgeTurned()).as("Latched until it lands").isTrue();
            troopa.motionUpdate();
            ticks++;
        }

        // Verify
        assertThat(troopa.isGrounded()).isTrue();
        assertThat(troopa.isLedgeTurned()).as("Landing clears the latch").isFalse();
        assertThat(troopa.isFacingRight()).isTrue();

        // Execute - on to the right-hand edge of the floor
        boolean turned = false;
        for (int tick = 0; tick < 200 && !turned; tick++) {
            troopa.motionUpdate();
            turned = !troopa.isFacingRight();
        }

        // Verify
        assertThat(turned).isTrue();
        troopa.motionUpdate();
        assertThat(troopa.getPosition().y()).as("Still on the floor").isCloseTo(REST_ROW_PIXEL_Y, within(TOLERANCE));
    }

    @Test
    @DisplayName("Stomped before its first tick, its flight tick count is still 0, so the ledge guard is not latched")
    void aFreshFlyerHandsOverNoLatch() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);

        // Execute
        paratroopa.onCollisionFromAbove(descendingPlayer());

        // Verify
        assertThat(world.capturedReplacement().isLedgeTurned()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Tail, shells and blocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A tail strike leaves an upside-down red shell and no score")
    void aTailStrikeShellsIt() {
        // Prepare - the player on the left, so the shell is thrown to the right
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        world.tick(paratroopa, 20);
        final LevelScenePlayer player = world.playerAtPixelX((START_COLUMN - 1) * (double) TILE_SPRITE_SIZE);

        // Execute
        paratroopa.onTailAttack(player);

        // Verify
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getMode()).isEqualTo(RED);
        assertThat(troopa.getState()).isEqualTo(SHELLED);
        assertThat(troopa.isUpsideDown()).isTrue();
        assertThat(troopa.getVelocity().y()).isEqualTo(TAIL_LAUNCH_Y_VELOCITY);
        assertThat(troopa.getVelocity().x()).as("Away from the player").isEqualTo(KNOCK_X_VELOCITY);
        assertThat(troopa.getPendingScoreReward()).isNull();
        assertThat(paratroopa.getPendingScoreReward()).isNull();
        assertThat(paratroopa.isExpired()).isTrue();
    }

    @Test
    @DisplayName("A kicked shell kills it through the red troopa that takes its slot")
    void aKickedShellKillsIt() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);

        // Execute
        paratroopa.onStruckByKickedObject(SHELL_KNOCK_RIGHT);

        // Verify
        final KoopaTroopa troopa = world.capturedReplacement();
        assertThat(troopa.getMode()).isEqualTo(RED);
        assertThat(troopa.getState()).isEqualTo(KILLED);
        assertThat(paratroopa.isExpired()).isTrue();
    }

    @Test
    @DisplayName("A block bumped from below does nothing to it, wherever it is on its track")
    void aBlockBumpDoesNothing() {
        // Prepare - every cell of its two columns carries a live bounce
        for (int row = 0; row < ROWS; row++) {
            for (int column = START_COLUMN; column <= START_COLUMN + 1; column++) {
                world.getBumpedBlockCells().add(of(column, row));
            }
        }
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);

        // Execute
        world.tick(paratroopa, PERIOD_TICKS);

        // Verify - its flight has no response to a block bumped underneath
        verify(world.getActiveObjectGrid(), never()).spawnEnemy(any());
        assertThat(paratroopa.isExpired()).isFalse();
    }

    @Test
    @DisplayName("It hurts the player on a body hit and from below")
    void itHurtsThePlayer() {
        // Prepare
        final KoopaParaTroopa paratroopa = world.redParatroopaAt(START_COLUMN, FLIGHT_ROW);
        final LevelScenePlayer player = world.playerAtPixelX(START_COLUMN * (double) TILE_SPRITE_SIZE);

        // Execute
        paratroopa.onPlayerOverlap(player);
        paratroopa.onCollisionFromBelow(player);

        // Verify
        verify(player, times(2)).getHurtBy(paratroopa);
    }
}
