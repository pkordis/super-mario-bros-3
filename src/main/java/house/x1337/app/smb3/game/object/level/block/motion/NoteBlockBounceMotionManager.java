package house.x1337.app.smb3.game.object.level.block.motion;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.GameObjectAnimatorSingleTiled;
import house.x1337.app.smb3.game.object.level.block.Bounce;
import house.x1337.app.smb3.game.object.level.block.animation.NoteBlockBounceAnimation;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;

/**
 * Runs the note blocks' bounces, one per block at a time. A block struck from below or from the side bounces up and
 * knocks what stands on it. A block the player lands on sinks and comes back up with the player riding it, and the
 * player is launched as the bounce ends; a jump pressed during the ride makes the launch stronger. See
 * {@code kb/note-block.md}.
 */
@Singleton
public final class NoteBlockBounceMotionManager implements BlockMotionManager {
    private static final double LAUNCH_Y_VELOCITY = -3.5;
    private static final double BOOSTED_LAUNCH_Y_VELOCITY = -7.0;

    private final List<Bounce> activeBounces = new ArrayList<>();

    @Override
    public void update() {
        // A bounce that ends here stays listed until postCollision, so it still counts as a bump for the whole tick
        // whatever order the managers run in.
        for (final Bounce bounce : activeBounces) {
            final NoteBlockBounceAnimation animation = bounce.animation();
            if (animation.isExpired()) {
                continue;
            }
            animation.tick();
            if (animation.isExpired()) {
                bounce.rider().ifPresent(this::launch);
                animation.detach();
            } else {
                bounce.rider().ifPresent(rider -> ride(rider, animation));
            }
        }
    }

    @Override
    public void postCollision() {
        activeBounces.removeIf(bounce -> bounce.animation().isExpired());
    }

    public void spawnBounceUp(
        final GameEngine gameEngine,
        final Coordinates offset,
        final GameObjectAnimatorSingleTiled<?> animator
    ) {
        if (isBounceActiveAt(offset)) {
            return;
        }
        final NoteBlockBounceAnimation animation = getBean(NoteBlockBounceAnimation.class, gameEngine, offset);
        animation.configure(animator, false);
        activeBounces.add(new Bounce(
            animation,
            Optional.empty()
        ));
    }

    public void spawnBounceDown(
        final GameEngine gameEngine,
        final Coordinates offset,
        final GameObjectAnimatorSingleTiled<?> animator,
        final LevelScenePlayer rider
    ) {
        if (isBounceActiveAt(offset)) {
            return;
        }
        final PlayerRuntimeState runtimeState = rider.getRuntimeState();
        runtimeState.setRidingNoteBlock(true);
        runtimeState.setNoteBlockBoosted(false);
        runtimeState.fall();
        final NoteBlockBounceAnimation animation = getBean(NoteBlockBounceAnimation.class, gameEngine, offset);
        animation.configure(animator, true);
        activeBounces.add(new Bounce(
            animation,
            Optional.of(rider)
        ));
    }


    public boolean isBounceActiveAt(final Coordinates offset) {
        return activeBounces
            .stream()
            .anyMatch(bounce -> bounce.isAt(offset));
    }

    public boolean isSinkingAt(final Coordinates offset) {
        return activeBounces
            .stream()
            .anyMatch(bounce -> bounce.isAt(offset) && bounce.animation().isSinking());
    }

    @Override
    public boolean isBlockBumpActiveAt(final Coordinates cell) {
        return activeBounces
            .stream()
            .anyMatch(bounce -> bounce.isAt(cell) && !bounce.animation().isSinking());
    }

    @Override
    public void reset() {
        activeBounces.forEach(bounce -> {
            if (!bounce.animation().isExpired()) {
                bounce.animation().detach();
            }
            bounce.rider().ifPresent(rider -> stopRiding(rider.getRuntimeState()));
        });
        activeBounces.clear();
    }

    /**
     * Moves the rider with the block. Once the block is back in place the ride is over and a jump press is an
     * ordinary press again, though the launch is still one tick away.
     */
    private void ride(
        final LevelScenePlayer rider,
        final NoteBlockBounceAnimation animation
    ) {
        rider.getPosition().setDY(animation.getRiderYVelocity());
        if (animation.isBackInPlace()) {
            rider.getRuntimeState().setRidingNoteBlock(false);
        }
    }

    private void launch(final LevelScenePlayer rider) {
        final PlayerRuntimeState runtimeState = rider.getRuntimeState();
        rider.getPosition().setDY(runtimeState.isNoteBlockBoosted() ? BOOSTED_LAUNCH_Y_VELOCITY : LAUNCH_Y_VELOCITY);
        runtimeState.setTo(JUMPING);
        runtimeState.setSlowGravityBlocked(true);
        stopRiding(runtimeState);
    }

    private void stopRiding(final PlayerRuntimeState runtimeState) {
        runtimeState.setRidingNoteBlock(false);
        runtimeState.setNoteBlockBoosted(false);
    }

}
