package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.TimerPace;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.PlayerTimerExpired;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.TimerPace.STANDARD;
import static house.x1337.app.smb3.enumeration.TimerPace.REAL_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * The timer steps once every 60 ticks at the real-time pace and once every 41 at the standard one, the first step
 * included, and a pause stops it at either pace.
 */
class PlayerTimerPaceTest {
    private final GameEventBus gameEventBus = new GameEventBus();
    private final List<PlayerTimerExpired> expiries = new ArrayList<>();
    private final PlayerTimer timer = new PlayerTimer();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(GameEventBus.class)).thenReturn(gameEventBus);
        gameEventBus.subscribe(PlayerTimerExpired.class, expiries::add);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A new timer counts at the real-time pace of 60 ticks a step; the standard pace takes 41")
    void aNewTimerCountsInRealTime() {
        // Execute & Verify
        assertThat(timer.getPace()).isEqualTo(REAL_TIME);
        assertThat(REAL_TIME.getTicksPerStep()).isEqualTo(60);
        assertThat(STANDARD.getTicksPerStep()).isEqualTo(41);
    }

    @ParameterizedTest
    @EnumSource(TimerPace.class)
    @DisplayName("From 300 the first step comes after exactly one step's ticks, at either pace")
    void theFirstStepComesAfterOneStepsTicks(final TimerPace pace) {
        // Prepare
        timer.setPace(pace);
        timer.start();

        // Execute & Verify
        tick(pace.getTicksPerStep() - 1);
        assertThat(timer.getTime()).as("One tick early").isEqualTo(300);
        tick(1);
        assertThat(timer.getTime()).as("On the step").isEqualTo(299);
    }

    @Test
    @DisplayName("At the standard pace a timer of 300 runs out after 300 x 41 ticks and announces it once")
    void theStandardPaceRunsOutAfterThreeHundredSteps() {
        // Prepare
        timer.setPace(STANDARD);
        timer.start();

        // Execute & Verify
        tick(300 * 41 - 1);
        assertThat(timer.getTime()).as("One tick early").isEqualTo(1);
        assertThat(expiries).isEmpty();
        tick(1);
        assertThat(timer.getTime()).isZero();
        assertThat(timer.isActive()).isFalse();
        tick(41);
        assertThat(expiries).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(TimerPace.class)
    @DisplayName("A pause stops the timer at either pace")
    void aPauseStopsTheTimer(final TimerPace pace) {
        // Prepare
        timer.setPace(pace);
        timer.start();
        tick(pace.getTicksPerStep());

        // Execute
        timer.pause();
        tick(pace.getTicksPerStep() * 5);

        // Verify
        assertThat(timer.getTime()).isEqualTo(299);
    }

    private void tick(final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            timer.tick();
        }
    }
}
