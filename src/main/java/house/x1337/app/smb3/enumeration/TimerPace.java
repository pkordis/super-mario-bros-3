package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;

/**
 * How fast the level timer counts down: one step every {@link #getTicksPerStep()} simulation ticks, the first step
 * included. {@link #REAL_TIME} steps once a real-world second. {@link #STANDARD} steps every 41 ticks, so a timer of
 * 300 runs out about 32% sooner.
 */
@Getter
@RequiredArgsConstructor
public enum TimerPace {
    REAL_TIME(SIMULATION_FPS),
    STANDARD(41);

    private final int ticksPerStep;
}
