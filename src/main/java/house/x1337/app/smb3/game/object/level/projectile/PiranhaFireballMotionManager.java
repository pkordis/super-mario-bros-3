package house.x1337.app.smb3.game.object.level.projectile;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.MotionManager;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.BACKGROUND;

/**
 * Owns the live piranha fireballs, in the four special-object slots a plant may use.
 */
@Getter
@Singleton
@RequiredArgsConstructor
public final class PiranhaFireballMotionManager implements MotionManager {
    public static final int SLOT_COUNT = 4;

    private final PiranhaFireball[] slots = new PiranhaFireball[SLOT_COUNT];
    private int frameCounter;

    public boolean spawn(
        final GameEngine gameEngine,
        final PixelPosition topLeft,
        final FixedPointVelocity velocity
    ) {
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            if (slots[slot] == null) {
                final PiranhaFireball fireball = getBean(PiranhaFireball.class, gameEngine);
                fireball.spawnIntoScene(slot, topLeft, velocity);
                slots[slot] = fireball;
                return true;
            }
        }
        return false;
    }

    /** @return the live fireballs, in slot order */
    public List<PiranhaFireball> getActiveFireballs() {
        return Arrays.stream(slots).filter(Objects::nonNull).toList();
    }

    @Override
    public void update() {
        // The tick is in postCollision, after every enemy has had the chance to spit.
    }

    @Override
    public void postCollision() {
        frameCounter++;
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            final PiranhaFireball fireball = slots[slot];
            if (fireball == null || removeIfOffScreen(slot)) {
                continue;
            }
            fireball.advance();
            fireball.positionSprite();
            if (isHitTestTick(fireball)) {
                hurtPlayersTouching(fireball);
            }
        }
    }

    /** Frozen, harmless and still drawn; the removal still runs, as it comes before the halt check. */
    @Override
    public void updateWhileHalted() {
        frameCounter++;
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            if (slots[slot] != null) {
                removeIfOffScreen(slot);
            }
        }
    }

    @Override
    public void reset() {
        removeAll();
    }

    public void removeAll() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            remove(slot);
        }
    }

    public boolean isHitTestTick(final PiranhaFireball fireball) {
        return ((fireball.getSlot() + frameCounter) & 1) == 1;
    }

    private void hurtPlayersTouching(final PiranhaFireball fireball) {
        fireball.getGameEngine().getAllPlayers().stream()
            .filter(LevelScenePlayer.class::isInstance)
            .map(LevelScenePlayer.class::cast)
            .filter(player -> player.getVisibility() != BACKGROUND)
            .filter(fireball::overlaps)
            .filter(player -> !fireball.isDestroyedBy(player))
            .forEach(player -> player.getHurtBy(fireball));
    }

    private boolean removeIfOffScreen(final int slot) {
        final PiranhaFireball fireball = slots[slot];
        final AxisAlignedBoundingBox screen = fireball.getGameEngine().getCameraState().getActiveObjectRegion(0);
        if (!fireball.isOffScreen(screen)) {
            return false;
        }
        remove(slot);
        return true;
    }

    private void remove(final int slot) {
        final PiranhaFireball fireball = slots[slot];
        if (fireball == null) {
            return;
        }
        fireball.setExpired(true);
        fireball.detach();
        slots[slot] = null;
    }
}
