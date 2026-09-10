package house.x1337.app.smb3.game.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.time.PowerSwitchTimeWindow.INITIAL_COUNT;
import static house.x1337.app.smb3.game.time.PowerSwitchTimeWindow.TICKS_PER_COUNT;
import static house.x1337.app.smb3.game.time.PowerSwitchTimeWindow.TOTAL_TICKS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link PowerSwitchTimeWindow} against the ROM's {@code Level_PSwitchCnt} countdown
 * (dasm {@code prg008.asm PRG008_A203}, ~:391): seeded at $80 and decremented only when
 * {@code Counter_1 AND #$03 == 0}, i.e. once every 4 frames — 512 ticks, ~8.5 s at 60 Hz.
 */
class PowerSwitchTimeWindowTest {

    @Test
    @DisplayName("Inactive until a switch is pressed")
    void inactiveByDefault() {
        // Prepare
        final PowerSwitchTimeWindow pSwitch = new PowerSwitchTimeWindow();

        // Execute
        pSwitch.tick();

        // Verify
        assertThat(pSwitch.isActive()).isFalse();
        assertThat(pSwitch.getTicksRemaining()).isZero();
    }

    @Test
    @DisplayName("The window lasts exactly 512 ticks — $80 counts of 4 frames each")
    void windowLastsFiveHundredAndTwelveTicks() {
        // Prepare
        final PowerSwitchTimeWindow pSwitch = new PowerSwitchTimeWindow();
        pSwitch.activate();

        // Execute & Verify
        assertThat(TOTAL_TICKS).as("Ported duration").isEqualTo(512);
        for (int tick = 1; tick < TOTAL_TICKS; tick++) {
            pSwitch.tick();
            assertThat(pSwitch.isActive()).as("Still active at tick %d", tick).isTrue();
        }
        pSwitch.tick();
        assertThat(pSwitch.isActive()).as("Expired on tick %d", TOTAL_TICKS).isFalse();
    }

    @Test
    @DisplayName("The count drops once every fourth tick, not every tick")
    void countDropsEveryFourthTick() {
        // Prepare
        final PowerSwitchTimeWindow pSwitch = new PowerSwitchTimeWindow();
        pSwitch.activate();

        // Execute & Verify
        assertThat(pSwitch.getCount()).isEqualTo(INITIAL_COUNT);
        for (int tick = 1; tick < TICKS_PER_COUNT; tick++) {
            pSwitch.tick();
            assertThat(pSwitch.getCount()).as("Count held at tick %d", tick).isEqualTo(INITIAL_COUNT);
        }
        pSwitch.tick();
        assertThat(pSwitch.getCount()).as("Count drops on the 4th tick").isEqualTo(INITIAL_COUNT - 1);
    }

    @Test
    @DisplayName("Ticks remaining counts down to zero across the whole window")
    void ticksRemainingCountsDown() {
        // Prepare
        final PowerSwitchTimeWindow pSwitch = new PowerSwitchTimeWindow();
        pSwitch.activate();

        // Execute & Verify
        assertThat(pSwitch.getTicksRemaining()).isEqualTo(TOTAL_TICKS);
        for (int tick = 1; tick <= TOTAL_TICKS; tick++) {
            pSwitch.tick();
            assertThat(pSwitch.getTicksRemaining())
                .as("Ticks remaining after tick %d", tick)
                .isEqualTo(TOTAL_TICKS - tick);
        }
    }

    @Test
    @DisplayName("Pressing a second switch mid-window reloads the full duration")
    void secondPressReloadsTheWindow() {
        // Prepare
        final PowerSwitchTimeWindow pSwitch = new PowerSwitchTimeWindow();
        pSwitch.activate();
        for (int tick = 0; tick < TOTAL_TICKS / 2; tick++) {
            pSwitch.tick();
        }

        // Execute
        pSwitch.activate();

        // Verify
        assertThat(pSwitch.getTicksRemaining()).isEqualTo(TOTAL_TICKS);
        for (int tick = 1; tick < TOTAL_TICKS; tick++) {
            pSwitch.tick();
        }
        assertThat(pSwitch.isActive()).as("Still active one tick before the reloaded window ends").isTrue();
        pSwitch.tick();
        assertThat(pSwitch.isActive()).isFalse();
    }

    @Test
    @DisplayName("Reset closes an open window so it cannot leak into the next level")
    void resetClosesTheWindow() {
        // Prepare
        final PowerSwitchTimeWindow pSwitch = new PowerSwitchTimeWindow();
        pSwitch.activate();
        pSwitch.tick();

        // Execute
        pSwitch.reset();

        // Verify
        assertThat(pSwitch.isActive()).isFalse();
        assertThat(pSwitch.getTicksRemaining()).isZero();
        pSwitch.tick();
        assertThat(pSwitch.isActive()).as("Still closed after a further tick").isFalse();
    }
}
