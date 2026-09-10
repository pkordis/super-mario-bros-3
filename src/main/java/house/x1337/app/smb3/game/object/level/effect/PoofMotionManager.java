package house.x1337.app.smb3.game.object.level.effect;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Offset;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Drives the short-lived poof clouds spawned at level cells — the ROM's {@code SOBJ_POOF} special
 * object slots (dasm prg007 {@code SObj_Poof}, spawned for the P-Switch by prg008
 * {@code LATP_PSwitch} @ {@code PRG008_B8C9}).
 *
 * <p>Discovered automatically through {@link MotionManager.Registry}, so {@code GameEngine} ticks it
 * each frame with no extra wiring.
 */
@Singleton
@RequiredArgsConstructor
public final class PoofMotionManager implements MotionManager<LevelObject> {
    private final List<PoofAnimation> activePoofs = new ArrayList<>();

    @Value("classpath:/sprites/effect/poof/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    @Override
    public void update() {
        final Iterator<PoofAnimation> iterator = activePoofs.iterator();
        while (iterator.hasNext()) {
            final PoofAnimation poof = iterator.next();
            poof.tick();
            if (poof.isExpired()) {
                poof.detach();
                iterator.remove();
            }
        }
    }

    public void spawn(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        for (final PoofAnimation existing : activePoofs) {
            if (existing.getOffset().equals(offset)) {
                return;
            }
        }
        activePoofs.add(new PoofAnimation(gameEngine, offset, animationFrames));
    }
}
