package house.x1337.app.smb3.game.player.level.death;

import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PlayerDeathMusicFinished;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerDied;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.GameConstants.PLAYER_PIT_DEATH_COUNTDOWN_TICKS;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.THROE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a pit death ends: {@code PlayerDeathStarted(FELL_OFF)}, then {@code PlayerDeathMusicFinished} once
 * the death music is over, then a single {@code PlayerDied}. The ROM waits {@code Event_Countdown = $C0}
 * (dasm prg008 @ PRG008_A3FA) and exits the level on the frame {@code Player_Die_FellOff} (prg029) reads
 * it as zero. Until there is a sound engine the stand-in counts that wait.
 *
 * <p>Each tick below is one simulation tick of a halted game. The first is the tick the death began on:
 * the engine runs the player before the motion managers, so the stand-in's first count lands on the
 * death's own tick, as the ROM's first decrement does.
 */
class PlayerDeathEventChainTest {
    private GameEventBus bus;
    private DeathMusicStandIn standIn;
    private PlayerDeathSequence sequence;
    private AtomicInteger musicFinished;
    private AtomicInteger playerDied;

    @BeforeEach
    void prepare() {
        bus = new GameEventBus();
        standIn = new DeathMusicStandIn(bus);
        sequence = new PlayerDeathSequence(bus);
        musicFinished = new AtomicInteger();
        playerDied = new AtomicInteger();
    }

    @Test
    @DisplayName("The stand-in reports the end of the music 192 ticks after the tick the death began on")
    void theStandInReportsTheMusicEnd192TicksLater() {
        // Prepare
        subscribeTheEngine();
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute & Verify - the death's own tick, then 191 more
        assertThat(PLAYER_PIT_DEATH_COUNTDOWN_TICKS).as("Event_Countdown = $C0").isEqualTo(192);
        tick(PLAYER_PIT_DEATH_COUNTDOWN_TICKS);
        assertThat(musicFinished.get()).as("Not yet").isZero();
        assertThat(standIn.isArmed()).isTrue();

        tick(1);
        assertThat(musicFinished.get()).as("On the 192nd tick after the death's").isEqualTo(1);
        assertThat(standIn.isArmed()).as("Spent").isFalse();

        tick(300);
        assertThat(musicFinished.get()).as("Once").isEqualTo(1);
    }

    @Test
    @DisplayName("Only when the music has ended is PlayerDied published, exactly once")
    void playerDiedFollowsTheMusicOnce() {
        // Prepare
        subscribeTheEngine();
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute & Verify
        tick(PLAYER_PIT_DEATH_COUNTDOWN_TICKS);
        assertThat(playerDied.get()).as("The level does not end while the music plays").isZero();

        tick(1);
        assertThat(playerDied.get()).as("It ends with the music").isEqualTo(1);
        assertThat(sequence.isFellOffDeathInProgress()).isFalse();

        tick(300);
        assertThat(playerDied.get()).as("And only once").isEqualTo(1);
    }

    @Test
    @DisplayName("Any publisher of the music's end finishes a pit death, with no stand-in at all")
    void anyPublisherOfTheMusicEndFinishesTheDeath() {
        // Prepare - a sound engine in place of the stand-in
        sequence.subscribe();
        recordTheEvents();
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute
        bus.publish(new PlayerDeathMusicFinished());
        bus.publish(new PlayerDeathMusicFinished());

        // Verify
        assertThat(playerDied.get()).as("One death, one PlayerDied").isEqualTo(1);
    }

    @Test
    @DisplayName("The throe neither arms the stand-in nor is ended by the music, since it ends itself")
    void theThroeIsLeftToItself() {
        // Prepare
        subscribeTheEngine();
        bus.publish(new PlayerDeathStarted(THROE));

        // Execute
        tick(400);
        bus.publish(new PlayerDeathMusicFinished());

        // Verify
        assertThat(standIn.isArmed()).as("Not armed").isFalse();
        assertThat(musicFinished.get()).as("Only the published one").isEqualTo(1);
        assertThat(playerDied.get()).as("The throe publishes its own PlayerDied").isZero();
    }

    @Test
    @DisplayName("A level restart part-way through disarms the stand-in, so the fresh level is not ended")
    void aRestartDisarmsTheStandIn() {
        // Prepare
        subscribeTheEngine();
        bus.publish(new PlayerDeathStarted(FELL_OFF));
        tick(50);

        // Execute - the R reset: the request, then the rebuild's MotionManager reset
        bus.publish(LevelSceneResetRequested.builder().build());
        standIn.reset();
        tick(400);

        // Verify
        assertThat(standIn.isArmed()).isFalse();
        assertThat(musicFinished.get()).as("No music end for the abandoned death").isZero();
        assertThat(playerDied.get()).isZero();
    }

    @Test
    @DisplayName("The R reset makes the sequence forget the death, so a late music end changes nothing")
    void theResetForgetsTheDeathInProgress() {
        // Prepare
        sequence.subscribe();
        recordTheEvents();
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute
        bus.publish(LevelSceneResetRequested.builder().build());
        bus.publish(new PlayerDeathMusicFinished());

        // Verify
        assertThat(sequence.isFellOffDeathInProgress()).isFalse();
        assertThat(playerDied.get()).isZero();
    }

    @Test
    @DisplayName("After an engine is destroyed, the next one subscribes both again and the chain still works once")
    void bothWorkInASecondSession() {
        // Prepare - a first session that ends mid-countdown, its listeners purged with its engine
        subscribeTheEngine();
        bus.publish(new PlayerDeathStarted(FELL_OFF));
        tick(20);
        bus.purgeEventListeners();

        // Execute - the second engine's start-up, then a pit death in it
        subscribeTheEngine();
        final boolean armedAtTheStart = standIn.isArmed();
        bus.publish(new PlayerDeathStarted(FELL_OFF));
        tick(PLAYER_PIT_DEATH_COUNTDOWN_TICKS + 1);

        // Verify
        assertThat(armedAtTheStart).as("The first session's countdown is gone").isFalse();
        assertThat(musicFinished.get()).as("Heard, and not twice").isEqualTo(1);
        assertThat(playerDied.get()).as("The level ends once").isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** What {@code GameEngine.simpleInitApp} does, plus listeners that count what reaches the bus. */
    private void subscribeTheEngine() {
        standIn.subscribe();
        sequence.subscribe();
        recordTheEvents();
    }

    private void recordTheEvents() {
        bus.subscribe(PlayerDeathMusicFinished.class, event -> musicFinished.incrementAndGet());
        bus.subscribe(PlayerDied.class, event -> playerDied.incrementAndGet());
    }

    /** Ticks of a halted game: the engine calls {@code updateWhileHalted} on every motion manager. */
    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            standIn.updateWhileHalted();
        }
    }
}
