package house.x1337.app.smb3.game.object.level.effect;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.AnimationOnlyMotionManager;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.effect.PoofSequence;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.game.effect.PoofSequence.oneShot;
import static house.x1337.app.smb3.model.game.effect.PoofSequence.poofDeath;

@Getter
@Singleton
@RequiredArgsConstructor
public final class PoofMotionManager implements AnimationOnlyMotionManager<PoofAnimation> {
    private final List<PoofAnimation> activeAnimations = new ArrayList<>();
    private final Class<PoofAnimation> animationType = PoofAnimation.class;

    @Value("classpath:/sprites/effect/poof/frame_{0,3}.png")
    private AnimationImageResource animationFrames;

    /**
     * Starts the switch-block puff ({@code SObj_Poof}) over the level cell at {@code offset}.
     *
     * @param gameEngine the game engine
     * @param offset     the level cell the puff covers
     */
    @Override
    public void spawn(
        final GameEngine gameEngine,
        final Coordinates offset
    ) {
        final WorldOffset worldOffset = WorldOffset.of(
            offset.x(),
            gameEngine.getLevelScene().getDimensions().rows() - 1 - offset.y(),
            Z_DEPTH_POOF
        );
        spawn(gameEngine, worldOffset, oneShot());
    }

    /**
     * Starts an enemy's poof death on the tick it is killed.
     *
     * @param gameEngine  the game engine
     * @param worldOffset the bottom-left corner of the 16x16 cloud, at the enemy's top-left
     */
    public void spawnPoofDeath(
        final GameEngine gameEngine,
        final WorldOffset worldOffset
    ) {
        spawn(gameEngine, worldOffset, poofDeath());
    }

    private void spawn(
        final GameEngine gameEngine,
        final WorldOffset worldOffset,
        final PoofSequence sequence
    ) {
        final PoofAnimation poofAnimation = getBean(PoofAnimation.class);
        poofAnimation.configure(
            gameEngine,
            worldOffset,
            animationFrames,
            sequence
        );
        getActiveAnimations().add(poofAnimation);
    }
}
