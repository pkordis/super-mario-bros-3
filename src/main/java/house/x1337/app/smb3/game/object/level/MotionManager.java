package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.util.CastCapable;
import house.x1337.app.smb3.util.loader.ImageResourceLoader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface MotionManager<L extends LevelObject> extends ImageResourceLoader {
    void update();

    /**
     * Reports whether this manager is currently animating a block-bump (the one-shot bounce a solid
     * block plays when hit from below) at the given tile cell.
     *
     * <p>This is the project's analogue of the ROM's transient {@code TILEA_BLOCKBUMP_CLEAR} tile
     * (dasm {@code prg008.asm Level_DoBumpBlocks}): rather than the bounce animation pushing objects
     * that rest on it, the bumped cell simply publishes this transient state and any active object
     * standing on it polls the tile at its own feet — see
     * {@code SuperMushroom} / {@code Object_InteractWithWorld} @ PRG001_A97C. Only the block-bounce
     * managers override this; everything else reports {@code false}.
     *
     * @param cell the tile cell to test
     * @return {@code true} if a bump is live at {@code cell} this tick
     */
    default boolean isBlockBumpActiveAt(final Offset cell) {
        return false;
    }

    /**
     * Called once per simulation tick <b>after</b> the engine's active-object collision pass has
     * dispatched every {@code onCollisionWith}. Managers react here to collisions detected this
     * tick — e.g. spawning a score caption for a reward that was just collected — so the reaction
     * lands on the same frame the collision was detected. Default: no reaction.
     */
    default void postCollision() {
        // Do nothing
    }

    @Singleton
    @RequiredArgsConstructor
    class Registry implements CastCapable {
        private final ListableBeanFactory beanFactory;
        @Getter(lazy = true)
        private final List<? extends MotionManager<?>> all = findAll();

        private List<? extends MotionManager<?>> findAll() {
            final ListableBeanFactory beanFactory = getBean(MotionManager.Registry.class).beanFactory;
            assert beanFactory != null;
            return beanFactory
                .getBeansOfType(MotionManager.class)
                .values()
                .stream()
                .map(this::<MotionManager<?>>checkedCast)
                .toList();
        }
    }
}
