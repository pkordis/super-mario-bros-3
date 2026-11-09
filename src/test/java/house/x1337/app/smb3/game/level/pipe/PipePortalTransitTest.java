package house.x1337.app.smb3.game.level.pipe;

import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.fade.FadingEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.Player;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.game.engine.LevelRunStart.carryOver;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.STANDARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A pipe trip: fade out, switch to the linked portal's scene on the engine frame after the screen is black, start the
 * exit with the camera on it, fade in. Without a destination the level restarts as R restarts it.
 */
class PipePortalTransitTest {
    private static final String SCENE_A = "scene-a";
    private static final String SCENE_B = "scene-b";

    private final List<Runnable> engineQueue = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();

    private GameEngine gameEngine;
    private PipePortalService pipePortalService;
    private LevelSceneService levelSceneService;
    private FadingEngine fadingEngine;
    private LevelSceneHost host;
    private LevelScenePlayer currentPlayer;
    private LevelScenePlayer arrivingPlayer;
    private PipePortalTransit transit;

    @BeforeEach
    void prepare() {
        gameEngine = mock(GameEngine.class);
        pipePortalService = mock(PipePortalService.class);
        levelSceneService = mock(LevelSceneService.class);
        fadingEngine = mock(FadingEngine.class);
        host = mock(LevelSceneHost.class);
        currentPlayer = mock(LevelScenePlayer.class);
        arrivingPlayer = mock(LevelScenePlayer.class);
        when(currentPlayer.getMode()).thenReturn(RACCOON);
        players.add(currentPlayer);
        final LevelScene current = mock(LevelScene.class);
        when(current.getId()).thenReturn(SCENE_A);
        when(gameEngine.getLevelScene()).thenReturn(current);
        when(gameEngine.getAllPlayers()).thenReturn(players);
        doAnswer(invocation -> engineQueue.add(invocation.getArgument(0)))
            .when(gameEngine)
            .enqueue(any(Runnable.class));
        doAnswer(invocation -> {
            players.clear();
            players.add(arrivingPlayer);
            return null;
        }).when(host).swapLevelScene(any(), any());
        when(pipePortalService.findById(anyString())).thenReturn(Optional.empty());
        transit = new PipePortalTransit(gameEngine, pipePortalService, levelSceneService, fadingEngine, host);
    }

    @Test
    @DisplayName("An unlinked portal restarts the level, with no fade")
    void anUnlinkedPortalRestarts() {
        // Prepare
        stored(portal("in-1", IN, SCENE_A, null));

        // Execute
        transit.onPipePortalTransitRequested(request("in-1"));

        // Verify
        verify(host).requestLevelSceneRestart();
        verifyNoInteractions(fadingEngine);
        assertThat(transit.isRunning()).isFalse();
    }

    @Test
    @DisplayName("A portal linked to one that cannot be come out of restarts the level, with no fade")
    void aLinkToAnInPortalRestarts() {
        // Prepare
        stored(portal("in-1", IN_AND_OUT, SCENE_A, "in-2"));
        stored(portal("in-2", IN, SCENE_B, "in-1"));

        // Execute
        transit.onPipePortalTransitRequested(request("in-1"));

        // Verify
        verify(host).requestLevelSceneRestart();
        verifyNoInteractions(fadingEngine);
    }

    @Test
    @DisplayName("A portal that is not saved restarts the level, with no fade")
    void aMissingPortalRestarts() {
        // Execute
        transit.onPipePortalTransitRequested(request("never-saved"));

        // Verify
        verify(host).requestLevelSceneRestart();
        verifyNoInteractions(fadingEngine);
    }

    @Test
    @DisplayName("A linked exit in another scene fades out, then on the next frame swaps, exits, centres and fades in")
    void aLinkedExitInAnotherScene() {
        // Prepare
        final PipePortal exit = linkedPair(SCENE_B);
        final LevelScene destination = mock(LevelScene.class);
        when(levelSceneService.findById(SCENE_B)).thenReturn(Optional.of(destination));

        // Execute
        transit.onPipePortalTransitRequested(request("in-1"));

        // Verify - only the fade so far
        final Runnable faded = fadedOut();
        verify(host, never()).swapLevelScene(any(), any());

        // Execute - the screen is black
        faded.run();

        // Verify - queued for the engine's next frame, not run inside the fade's tick
        assertThat(engineQueue).hasSize(1);
        verify(host, never()).swapLevelScene(any(), any());

        // Execute - the engine's next frame
        runEngineQueue();

        // Verify
        final InOrder inOrder = inOrder(fadingEngine, host, arrivingPlayer);
        inOrder.verify(host).swapLevelScene(destination, carryOver(RACCOON));
        inOrder.verify(arrivingPlayer).beginPipeExit(exit);
        inOrder.verify(arrivingPlayer).centreVerticalScrollOn(exit);
        inOrder.verify(fadingEngine).fadeIn(eq(STANDARD), any());
        verify(currentPlayer, never()).beginPipeExit(any());
        verify(host, never()).requestLevelSceneRestart();
        assertThat(transit.isRunning()).as("Until the fade-in ends").isTrue();
    }

    @Test
    @DisplayName("A linked exit in the same scene moves the current player without rebuilding anything")
    void aLinkedExitInTheSameScene() {
        // Prepare
        final PipePortal exit = linkedPair(SCENE_A);

        // Execute
        transit.onPipePortalTransitRequested(request("in-1"));
        fadedOut().run();
        runEngineQueue();

        // Verify
        verify(host, never()).swapLevelScene(any(), any());
        verifyNoInteractions(levelSceneService);
        final InOrder inOrder = inOrder(currentPlayer, fadingEngine);
        inOrder.verify(currentPlayer).beginPipeExit(exit);
        inOrder.verify(currentPlayer).centreVerticalScrollOn(exit);
        inOrder.verify(fadingEngine).fadeIn(eq(STANDARD), any());
    }

    @Test
    @DisplayName("A destination scene that is not saved fades back in and restarts the level")
    void aMissingDestinationSceneRestarts() {
        // Prepare
        linkedPair(SCENE_B);
        when(levelSceneService.findById(SCENE_B)).thenReturn(Optional.empty());

        // Execute
        transit.onPipePortalTransitRequested(request("in-1"));
        fadedOut().run();
        runEngineQueue();

        // Verify
        final InOrder inOrder = inOrder(fadingEngine, host);
        inOrder.verify(fadingEngine).fadeIn(eq(STANDARD), isNull());
        inOrder.verify(host).requestLevelSceneRestart();
        verify(host, never()).swapLevelScene(any(), any());
        verify(currentPlayer, never()).beginPipeExit(any());
        assertThat(transit.isRunning()).isFalse();
    }

    @Test
    @DisplayName("A second request during a trip is ignored, and the next one after the fade-in is honoured")
    void aSecondRequestDuringATripIsIgnored() {
        // Prepare
        linkedPair(SCENE_A);

        // Execute
        transit.onPipePortalTransitRequested(request("in-1"));
        transit.onPipePortalTransitRequested(request("in-1"));

        // Verify
        verify(fadingEngine, times(1)).fadeOut(eq(STANDARD), any());

        // Execute - the trip plays out to the end of the fade-in
        fadedOut().run();
        runEngineQueue();
        final ArgumentCaptor<Runnable> fadedIn = ArgumentCaptor.forClass(Runnable.class);
        verify(fadingEngine).fadeIn(eq(STANDARD), fadedIn.capture());
        fadedIn.getValue().run();
        transit.onPipePortalTransitRequested(request("in-1"));

        // Verify
        verify(fadingEngine, times(2)).fadeOut(eq(STANDARD), any());
    }

    @Test
    @DisplayName("A trip cancelled by a restart shows the screen again and never arrives")
    void aCancelledTripNeverArrives() {
        // Prepare
        linkedPair(SCENE_A);
        transit.onPipePortalTransitRequested(request("in-1"));
        final Runnable faded = fadedOut();

        // Execute
        transit.cancel();
        faded.run();
        runEngineQueue();

        // Verify
        verify(fadingEngine).reset();
        verify(currentPlayer, never()).beginPipeExit(any());
        verify(fadingEngine, never()).fadeIn(any(), any());
        assertThat(transit.isRunning()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** Stores IN portal {@code in-1} in scene A, linked both ways to OUT portal {@code out-1} in the given scene. */
    private PipePortal linkedPair(final String exitSceneId) {
        stored(portal("in-1", IN, SCENE_A, "out-1"));
        return stored(portal("out-1", OUT, exitSceneId, "in-1"));
    }

    private PipePortal stored(final PipePortal portal) {
        when(pipePortalService.findById(portal.getId())).thenReturn(Optional.of(portal));
        return portal;
    }

    private Runnable fadedOut() {
        final ArgumentCaptor<Runnable> onBlack = ArgumentCaptor.forClass(Runnable.class);
        verify(fadingEngine).fadeOut(eq(STANDARD), onBlack.capture());
        return onBlack.getValue();
    }

    private static PipePortal portal(
        final String id,
        final PipePortalType type,
        final String levelSceneId,
        final String linksToId
    ) {
        return PipePortal.builder().id(id).type(type).levelSceneId(levelSceneId).linksToId(linksToId).build();
    }

    private static PipePortalTransitRequested request(final String pipePortalId) {
        return PipePortalTransitRequested.builder().pipePortalId(pipePortalId).inputHandlerId("handler-1").build();
    }

    /** Runs what the engine would run at the start of its next frame. */
    private void runEngineQueue() {
        final List<Runnable> tasks = new ArrayList<>(engineQueue);
        engineQueue.clear();
        tasks.forEach(Runnable::run);
    }
}
