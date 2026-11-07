package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.game.engine.LevelRunStart.carryOver;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.STANDARD;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Takes the player from the pipe it went into to the one its portal is linked to.
 *
 * <ol>
 *   <li>The screen fades out.</li>
 *   <li>On the engine frame after it is black, the destination scene replaces the current one, unless the link stays
 *   inside the scene.</li>
 *   <li>The player starts coming out of the linked pipe, with the camera on it.</li>
 *   <li>The screen fades in. The exit plays out once it has.</li>
 * </ol>
 */
@Slf4j
@Prototype
@RequiredArgsConstructor
public final class PipePortalTransit implements PipePortalLinkRulesAware {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger generation = new AtomicInteger();

    private final GameEngine gameEngine;
    private final PipePortalService pipePortalService;
    private final LevelSceneService levelSceneService;
    private final FadingEngine fadingEngine;
    private final LevelSceneHost host;

    public void onPipePortalTransitRequested(final PipePortalTransitRequested event) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        final Optional<PipePortal> destination = findDestinationOrNone(event.getPipePortalId());
        if (destination.isEmpty()) {
            running.set(false);
            host.requestLevelSceneRestart();
            return;
        }
        final int trip = generation.get();
        fadingEngine.fadeOut(STANDARD, () -> gameEngine.enqueue(() -> arrive(destination.get(), trip)));
    }

    public void cancel() {
        if (running.getAndSet(false)) {
            generation.incrementAndGet();
            fadingEngine.reset();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private Optional<PipePortal> findDestinationOrNone(final String enteredId) {
        try {
            return findDestination(enteredId);
        } catch (final RuntimeException e) {
            log.error("Could not look up where pipe portal {} leads; restarting the level", enteredId, e);
            return Optional.empty();
        }
    }

    private Optional<PipePortal> findDestination(final String enteredId) {
        if (enteredId == null) {
            return Optional.empty();
        }
        final Optional<PipePortal> entered = pipePortalService.findById(enteredId);
        final Optional<PipePortal> linked = entered
            .map(PipePortal::getLinksToId)
            .flatMap(pipePortalService::findById);
        return destinationOf(entered.orElse(null), linked.orElse(null));
    }

    private void arrive(final PipePortal destination, final int trip) {
        if (trip != generation.get()) {
            return;
        }
        try {
            if (!isInCurrentScene(destination) && !swapTo(destination)) {
                return;
            }
            currentPlayer().ifPresent(player -> {
                player.beginPipeExit(destination);
                player.centreVerticalScrollOn(destination);
            });
            fadingEngine.fadeIn(STANDARD, () -> running.set(false));
        } catch (final RuntimeException e) {
            log.error("Pipe trip to portal {} failed; restarting the level", destination.getId(), e);
            giveUp();
        }
    }

    private boolean isInCurrentScene(final PipePortal destination) {
        final LevelScene current = gameEngine.getLevelScene();
        return current != null && Objects.equals(destination.getLevelSceneId(), current.getId());
    }

    private boolean swapTo(final PipePortal destination) {
        final long started = System.nanoTime();
        final Optional<LevelScene> scene = Optional.ofNullable(destination.getLevelSceneId())
            .flatMap(levelSceneService::findById);
        final long loaded = System.nanoTime();
        if (scene.isEmpty()) {
            log.warn(
                "Pipe portal {} links into scene {}, which is not saved; restarting the level",
                destination.getId(),
                destination.getLevelSceneId()
            );
            giveUp();
            return false;
        }
        final PlayerMode mode = currentPlayer().map(LevelScenePlayer::getMode).orElse(SHRUNK);
        host.swapLevelScene(scene.get(), carryOver(mode));
        log.info(
            "Pipe trip into scene {}: loaded in {} ms, built in {} ms",
            destination.getLevelSceneId(),
            NANOSECONDS.toMillis(loaded - started),
            NANOSECONDS.toMillis(System.nanoTime() - loaded)
        );
        return true;
    }

    private void giveUp() {
        running.set(false);
        fadingEngine.fadeIn(STANDARD, null);
        host.requestLevelSceneRestart();
    }

    private Optional<LevelScenePlayer> currentPlayer() {
        return gameEngine.getAllPlayers()
            .stream()
            .filter(LevelScenePlayer.class::isInstance)
            .map(LevelScenePlayer.class::cast)
            .findFirst();
    }
}
