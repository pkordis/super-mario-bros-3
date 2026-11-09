package house.x1337.app.smb3.game.engine.fade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.game.engine.fade.FadeProfile.STANDARD;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.TITLE;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.TOAD_AND_KING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fade's darkness and completion tick by tick: the fade's step timer is decremented before it is tested,
 * so the steps land on ticks 4, 8, 12 and 16.
 */
class FadingEngineTest {
    private FadingEngine fadingEngine;

    @BeforeEach
    void prepare() {
        fadingEngine = new FadingEngine();
    }

    @Test
    @DisplayName("A standard fade-out darkens a quarter every four ticks and completes the tick after black")
    void standardFadeOutTable() {
        // Prepare
        final AtomicInteger completedOnTick = new AtomicInteger();
        final AtomicInteger tick = new AtomicInteger();
        fadingEngine.fadeOut(STANDARD, () -> completedOnTick.set(tick.get()));
        final float onStart = fadingEngine.getDarkness();

        // Execute
        final List<Float> darkness = run(tick, 20);

        // Verify
        assertThat(onStart).as("Darkness right after starting").isZero();
        assertThat(darkness).containsExactly(
            0f, 0f, 0f,
            0.25f, 0.25f, 0.25f, 0.25f,
            0.5f, 0.5f, 0.5f, 0.5f,
            0.75f, 0.75f, 0.75f, 0.75f,
            1f, 1f, 1f, 1f, 1f
        );
        assertThat(completedOnTick.get()).as("The tick the callback ran on").isEqualTo(17);
    }

    @Test
    @DisplayName("A standard fade-in starts three quarters dark at once and is clear from tick 12, done on tick 16")
    void standardFadeInTable() {
        // Prepare
        final AtomicInteger completedOnTick = new AtomicInteger();
        final AtomicInteger tick = new AtomicInteger();
        fadingEngine.fadeIn(STANDARD, () -> completedOnTick.set(tick.get()));
        final float onStart = fadingEngine.getDarkness();

        // Execute
        final List<Float> darkness = run(tick, 17);

        // Verify
        assertThat(onStart).as("The palette three brightness rows down is queued at once").isEqualTo(0.75f);
        assertThat(darkness).containsExactly(
            0.75f, 0.75f, 0.75f,
            0.5f, 0.5f, 0.5f, 0.5f,
            0.25f, 0.25f, 0.25f, 0.25f,
            0f, 0f, 0f, 0f,
            0f, 0f
        );
        assertThat(completedOnTick.get()).isEqualTo(16);
        assertThat(fadingEngine.isFading()).isFalse();
    }

    @Test
    @DisplayName("The King's room fade-out is black from tick 16, holds, and completes on tick 45")
    void toadAndKingFadeOutHoldsBlack() {
        // Prepare
        final AtomicInteger completedOnTick = new AtomicInteger();
        final AtomicInteger tick = new AtomicInteger();
        fadingEngine.fadeOut(TOAD_AND_KING, () -> completedOnTick.set(tick.get()));

        // Execute
        final List<Float> darkness = run(tick, 50);

        // Verify
        assertThat(darkness.get(14)).as("Tick 15").isEqualTo(0.75f);
        assertThat(darkness.subList(15, 50)).as("Ticks 16 to 50").containsOnly(1f);
        assertThat(completedOnTick.get()).isEqualTo(45);
    }

    @Test
    @DisplayName("The title fade steps every five ticks")
    void titleFadeStepsEveryFiveTicks() {
        // Prepare
        final AtomicInteger tick = new AtomicInteger();
        fadingEngine.fadeIn(TITLE, null);

        // Execute
        final List<Float> darkness = run(tick, 16);

        // Verify
        assertThat(darkness.subList(0, 4)).containsOnly(0.75f);
        assertThat(darkness.subList(4, 9)).containsOnly(0.5f);
        assertThat(darkness.subList(9, 14)).containsOnly(0.25f);
        assertThat(darkness.subList(14, 16)).containsOnly(0f);
    }

    @Test
    @DisplayName("The callback runs exactly once")
    void callbackRunsOnce() {
        // Prepare
        final AtomicInteger calls = new AtomicInteger();
        fadingEngine.fadeOut(STANDARD, calls::incrementAndGet);

        // Execute
        run(new AtomicInteger(), 60);

        // Verify
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("After a fade-out the screen stays black, halting gameplay, until a fade-in starts")
    void heldBlackUntilFadeIn() {
        // Prepare
        fadingEngine.fadeOut(STANDARD, null);
        run(new AtomicInteger(), 40);

        // Verify
        assertThat(fadingEngine.isFading()).isFalse();
        assertThat(fadingEngine.getDarkness()).isEqualTo(1f);
        assertThat(fadingEngine.isHaltingGameplay()).isTrue();

        // Execute
        fadingEngine.fadeIn(STANDARD, null);

        // Verify
        assertThat(fadingEngine.getDarkness()).isEqualTo(0.75f);
        assertThat(fadingEngine.isHaltingGameplay()).isTrue();

        // Execute
        run(new AtomicInteger(), 16);

        // Verify
        assertThat(fadingEngine.getDarkness()).isZero();
        assertThat(fadingEngine.isHaltingGameplay()).isFalse();
    }

    @Test
    @DisplayName("A callback may start the next fade")
    void callbackCanChainTheNextFade() {
        // Prepare
        final AtomicInteger fadedIn = new AtomicInteger();
        fadingEngine.fadeOut(STANDARD, () -> fadingEngine.fadeIn(STANDARD, fadedIn::incrementAndGet));

        // Execute
        run(new AtomicInteger(), 17);

        // Verify
        assertThat(fadingEngine.isFading()).as("The fade-in is under way").isTrue();
        assertThat(fadingEngine.getDarkness()).isEqualTo(0.75f);

        // Execute
        run(new AtomicInteger(), 16);

        // Verify
        assertThat(fadedIn.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Only a blocking fade halts gameplay")
    void onlyABlockingFadeHalts() {
        // Execute & Verify
        assertThat(fadingEngine.isHaltingGameplay()).as("No fade").isFalse();

        fadingEngine.fadeOut(STANDARD, null);
        assertThat(fadingEngine.isHaltingGameplay()).as("Standard").isTrue();

        fadingEngine.fadeOut(TOAD_AND_KING, null);
        assertThat(fadingEngine.isHaltingGameplay()).as("The King's room, running").isFalse();
        run(new AtomicInteger(), 50);
        assertThat(fadingEngine.getDarkness()).isEqualTo(1f);
        assertThat(fadingEngine.isHaltingGameplay()).as("The King's room, held black").isFalse();
    }

    @Test
    @DisplayName("Starting a fade replaces the running one, whose callback never runs")
    void newFadeReplacesTheRunningOne() {
        // Prepare
        final AtomicInteger first = new AtomicInteger();
        final AtomicInteger second = new AtomicInteger();
        fadingEngine.fadeOut(STANDARD, first::incrementAndGet);
        run(new AtomicInteger(), 10);

        // Execute
        fadingEngine.fadeIn(STANDARD, second::incrementAndGet);
        run(new AtomicInteger(), 30);

        // Verify
        assertThat(first.get()).isZero();
        assertThat(second.get()).isEqualTo(1);
        assertThat(fadingEngine.getDarkness()).isZero();
    }

    @Test
    @DisplayName("Resetting drops the fade and shows full colour")
    void resetClearsEverything() {
        // Prepare
        final AtomicInteger calls = new AtomicInteger();
        fadingEngine.fadeOut(STANDARD, calls::incrementAndGet);
        run(new AtomicInteger(), 10);

        // Execute
        fadingEngine.reset();
        run(new AtomicInteger(), 30);

        // Verify
        assertThat(fadingEngine.getDarkness()).isZero();
        assertThat(fadingEngine.isFading()).isFalse();
        assertThat(fadingEngine.isHaltingGameplay()).isFalse();
        assertThat(calls.get()).isZero();
    }

    /** Ticks {@code count} times, counting in {@code tick}, and records the darkness after each tick. */
    private List<Float> run(final AtomicInteger tick, final int count) {
        final List<Float> darkness = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            tick.incrementAndGet();
            fadingEngine.tick();
            darkness.add(fadingEngine.getDarkness());
        }
        return darkness;
    }
}
