package house.x1337.app.smb3.game.player.level.death;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.model.event.PlayerDeathMusicFinished;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.PLAYER_PIT_DEATH_COUNTDOWN_TICKS;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;

/**
 * A placeholder for the death music, until the port has a sound engine.
 *
 * <p>After a pit death the ROM waits {@code Event_Countdown = $C0} ticks (dasm prg008 @ PRG008_A3FA) for
 * the {@code MUS1_PLAYERDEATH} track to play out, and only then leaves the level ({@code Player_Die_FellOff},
 * prg029). This class counts those {@link house.x1337.app.smb3.GameConstants#PLAYER_PIT_DEATH_COUNTDOWN_TICKS}
 * from {@link PlayerDeathStarted}{@code (FELL_OFF)} and publishes {@link PlayerDeathMusicFinished} on the
 * 192nd simulation tick after the one the death began on. {@link PlayerDeathSequence} turns that into the
 * end of the level.
 *
 * <p><b>Delete this class</b> once the sound engine publishes {@link PlayerDeathMusicFinished} at the end
 * of the real track. Nothing else needs to change.
 *
 * <p>It is a {@link MotionManager} only for the engine's clock and reset. A death halts gameplay, so the
 * engine calls {@link #updateWhileHalted()} once per simulation tick for the whole countdown, and every
 * level build calls {@link #reset()}, which disarms a countdown a restart cut short (the R reset, for one).
 * It subscribes once per engine through {@link #subscribe()}, not in {@code @PostConstruct}: it is a
 * singleton, and an engine's {@code destroy()} purges the bus, so the next engine must subscribe it again.
 */
@Singleton
@RequiredArgsConstructor
public final class DeathMusicStandIn implements MotionManager {
    private final GameEventBus gameEventBus;

    private boolean armed;
    private int remainingTicks;

    /** Listens for deaths on the bus, starting disarmed. Called once by each engine as it starts. */
    public void subscribe() {
        reset();
        gameEventBus.subscribe(PlayerDeathStarted.class, this::onPlayerDeathStarted);
    }

    /** @return whether a countdown is running */
    public boolean isArmed() {
        return armed;
    }

    @Override
    public void update() {
        tick();
    }

    @Override
    public void updateWhileHalted() {
        tick();
    }

    @Override
    public void reset() {
        armed = false;
        remainingTicks = 0;
    }

    private void onPlayerDeathStarted(final PlayerDeathStarted event) {
        if (event.getKind() == FELL_OFF) {
            armed = true;
            remainingTicks = PLAYER_PIT_DEATH_COUNTDOWN_TICKS;
        }
    }

    /**
     * Counts down, then publishes on the tick that finds the count at zero. That is the ROM's order:
     * {@code Player_Die_FellOff} returns while {@code Event_Countdown != 0} and exits on the frame it reads 0.
     */
    private void tick() {
        if (!armed) {
            return;
        }
        if (remainingTicks > 0) {
            remainingTicks--;
            return;
        }
        armed = false;
        gameEventBus.publish(new PlayerDeathMusicFinished());
    }
}
