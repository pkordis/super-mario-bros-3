package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.PlayerTimerHurryUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * The timer announces the hurry-up only on the countdown step from 101 to 100.
 */
class PlayerTimerHurryUpTest {
    private final GameEventBus gameEventBus = new GameEventBus();
    private final List<Integer> hurryUpTimes = new ArrayList<>();
    private final PlayerTimer timer = new PlayerTimer();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(GameEventBus.class)).thenReturn(gameEventBus);
        gameEventBus.subscribe(PlayerTimerHurryUp.class, event -> hurryUpTimes.add(timer.getTime()));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Counting down from 102 to 98 announces the hurry-up once, at 100")
    void theStepToOneHundredAnnouncesIt() {
        // Prepare
        timer.setInitialTime(102);
        timer.start();

        // Execute
        tickSeconds(4);

        // Verify
        assertThat(timer.getTime()).isEqualTo(98);
        assertThat(hurryUpTimes).containsExactly(100);
    }

    @Test
    @DisplayName("A run that starts at 100 never announces it")
    void aRunStartingAtOneHundredStaysQuiet() {
        // Prepare
        timer.setInitialTime(100);
        timer.start();

        // Execute
        tickSeconds(3);

        // Verify
        assertThat(hurryUpTimes).isEmpty();
    }

    @Test
    @DisplayName("Time taken off down to 100 does not announce it")
    void timeTakenOffStaysQuiet() {
        // Prepare
        timer.setInitialTime(150);
        timer.start();

        // Execute
        timer.takeOff(50);
        timer.takeOff(10);

        // Verify
        assertThat(timer.getTime()).isEqualTo(90);
        assertThat(hurryUpTimes).isEmpty();
    }

    private void tickSeconds(final int seconds) {
        for (int tick = 0; tick < seconds * SIMULATION_FPS; tick++) {
            timer.tick();
        }
    }
}
