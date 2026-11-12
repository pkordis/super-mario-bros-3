package house.x1337.app.smb3.game.player.level.death;

import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PlayerDeathFallEnded;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerDied;
import house.x1337.app.smb3.model.event.SoundPlayComplete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.enumeration.MusicTrack.COURSE_CLEAR;
import static house.x1337.app.smb3.enumeration.MusicTrack.PLAYER_DOWN;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.THROE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a death ends: {@code PlayerDeathStarted}, then the end of the death music ({@code SoundPlayComplete} for
 * {@code PLAYER_DOWN}) and, for a death by an enemy, the body's fall off the level ({@code PlayerDeathFallEnded}),
 * then a single {@code PlayerDied}.
 */
class PlayerDeathEventChainTest {
    private GameEventBus bus;
    private PlayerDeathSequence sequence;
    private AtomicInteger playerDied;

    @BeforeEach
    void prepare() {
        bus = new GameEventBus();
        sequence = new PlayerDeathSequence(bus);
        playerDied = new AtomicInteger();
        subscribeTheEngine();
    }

    @Test
    @DisplayName("A pit death ends with the death music, exactly once")
    void aPitDeathEndsWithTheMusic() {
        // Prepare
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute
        final int beforeTheMusicEnds = playerDied.get();
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));

        // Verify
        assertThat(beforeTheMusicEnds).as("Not while the music plays").isZero();
        assertThat(playerDied.get()).as("Once, with the music").isEqualTo(1);
        assertThat(sequence.isDeathInProgress()).isFalse();
    }

    @Test
    @DisplayName("A death by an enemy waits for the music after the body has fallen off the level")
    void aThroeWaitsForTheMusic() {
        // Prepare
        bus.publish(new PlayerDeathStarted(THROE));

        // Execute
        bus.publish(new PlayerDeathFallEnded());
        final int whileTheMusicPlays = playerDied.get();
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));

        // Verify
        assertThat(whileTheMusicPlays).as("The body is gone, the music plays on").isZero();
        assertThat(playerDied.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("A death by an enemy waits for the body when the music ends first")
    void aThroeWaitsForTheBody() {
        // Prepare
        bus.publish(new PlayerDeathStarted(THROE));

        // Execute
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));
        final int whileTheBodyFalls = playerDied.get();
        bus.publish(new PlayerDeathFallEnded());

        // Verify
        assertThat(whileTheBodyFalls).as("The music is over, the body still falls").isZero();
        assertThat(playerDied.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Another track's end does not end a death")
    void anotherTracksEndIsIgnored() {
        // Prepare
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute
        bus.publish(new SoundPlayComplete(COURSE_CLEAR));

        // Verify
        assertThat(playerDied.get()).isZero();
        assertThat(sequence.isDeathInProgress()).isTrue();
    }

    @Test
    @DisplayName("Without a death, the music's end and a fallen body change nothing")
    void nothingWithoutADeath() {
        // Execute
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));
        bus.publish(new PlayerDeathFallEnded());

        // Verify
        assertThat(playerDied.get()).isZero();
    }

    @Test
    @DisplayName("The R reset makes the sequence forget the death, so a late music end changes nothing")
    void theResetForgetsTheDeathInProgress() {
        // Prepare
        bus.publish(new PlayerDeathStarted(FELL_OFF));

        // Execute
        bus.publish(LevelSceneResetRequested.builder().build());
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));

        // Verify
        assertThat(sequence.isDeathInProgress()).isFalse();
        assertThat(playerDied.get()).isZero();
    }

    @Test
    @DisplayName("After an engine is destroyed, the next one subscribes again and a death still ends once")
    void itWorksInASecondSession() {
        // Prepare - a first session that ends mid-death, its listeners purged with its engine
        bus.publish(new PlayerDeathStarted(FELL_OFF));
        bus.purgeEventListeners();

        // Execute - the second engine's start-up, then a pit death in it
        subscribeTheEngine();
        final boolean inProgressAtTheStart = sequence.isDeathInProgress();
        bus.publish(new PlayerDeathStarted(FELL_OFF));
        bus.publish(new SoundPlayComplete(PLAYER_DOWN));

        // Verify
        assertThat(inProgressAtTheStart).as("The first session's death is forgotten").isFalse();
        assertThat(playerDied.get()).as("The level ends once").isEqualTo(1);
    }

    /** What {@code GameEngine.simpleInitApp} does, plus a listener that counts the level's ends. */
    private void subscribeTheEngine() {
        sequence.subscribe();
        bus.subscribe(PlayerDied.class, event -> playerDied.incrementAndGet());
    }
}
