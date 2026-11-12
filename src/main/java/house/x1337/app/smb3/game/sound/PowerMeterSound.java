package house.x1337.app.smb3.game.sound;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.SoundEffect;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.model.event.GameplayHalted;
import house.x1337.app.smb3.model.event.GameplayResumed;
import house.x1337.app.smb3.model.event.PlayerPowerLost;
import house.x1337.app.smb3.model.event.PlayerPowerReached;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.SoundEffect.POWER_ON;

/**
 * Loops {@link SoundEffect#POWER_ON} while the P-meter is full and gameplay runs. A halt (a death, a pipe, a suit
 * change, a blocking fade) silences it with the meter still full, and it starts again when gameplay resumes.
 *
 * <p>It follows four events: {@link PlayerPowerReached} and {@link PlayerPowerLost} for the meter,
 * {@link GameplayHalted} and {@link GameplayResumed} for the halt. It is a singleton that outlives each engine, so it
 * subscribes through {@link #subscribe()} once per engine, as an engine's {@code destroy()} purges the bus.
 */
@Singleton
@RequiredArgsConstructor
public final class PowerMeterSound {
    private final GameEventBus gameEventBus;
    private final SoundPlayer soundPlayer;

    private boolean full;
    private boolean halted;

    /** Listens for the meter and the halt, from an empty meter and running gameplay. Called once per engine. */
    public void subscribe() {
        full = false;
        halted = false;
        gameEventBus.subscribe(PlayerPowerReached.class, event -> onMeterChanged(true));
        gameEventBus.subscribe(PlayerPowerLost.class, event -> onMeterChanged(false));
        gameEventBus.subscribe(GameplayHalted.class, event -> onHaltChanged(true));
        gameEventBus.subscribe(GameplayResumed.class, event -> onHaltChanged(false));
    }

    private void onMeterChanged(final boolean nowFull) {
        full = nowFull;
        refresh();
    }

    private void onHaltChanged(final boolean nowHalted) {
        halted = nowHalted;
        refresh();
    }

    private void refresh() {
        if (full && !halted) {
            soundPlayer.loop(POWER_ON);
        } else {
            soundPlayer.stopLoop(POWER_ON);
        }
    }
}
