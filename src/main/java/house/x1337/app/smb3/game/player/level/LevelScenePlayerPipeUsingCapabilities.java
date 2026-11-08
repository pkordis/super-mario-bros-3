package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerVisibility;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.level.pipe.PipeEntryRulesAware;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.collision.DirectionalProbes;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.BACKGROUND;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_UP;
import static house.x1337.app.smb3.model.game.Coordinates.fromPlayerOffset;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.PIPE_EXIT_TICKS_UP;
import static java.lang.Math.floor;

public interface LevelScenePlayerPipeUsingCapabilities
    extends
        LevelScenePlayerOrientationAware,
        LevelScenePlayerRenderer,
        LevelScenePlayerRuntimeStateAware,
        PipeEntryRulesAware {
    double PIPE_MOVE_PER_TICK = 8.0 / TILE_SPRITE_SIZE;
    double PIPE_EXIT_DOWN_PER_TICK = 56.0 / TILE_SPRITE_SIZE;
    double PIPE_EXIT_FALL_DY = PIPE_EXIT_DOWN_PER_TICK;
    double PIPE_EXIT_UP_DISTANCE = (PIPE_EXIT_TICKS_UP + 1) * PIPE_MOVE_PER_TICK;
    double PLAYER_BOX_HEIGHT = 2.0 * TILE_SPRITE_SIZE;

    PlayerInputHandler getInputHandler();
    LevelSceneVerticalScroll getVerticalScroll();
    boolean isPipeTransitPublished();
    void setPipeTransitPublished(boolean pipeTransitPublished);
    void setVisibility(PlayerVisibility visibility);

    /**
     * Starts the pipe-entering state when the player is lined up with the pipe under an {@code IN} or
     * {@code IN_AND_OUT} portal and holds its direction.
     *
     * <ul>
     *   <li>Never while tail attacking or already in a pipe (prg008.asm:6135-6138, 6285-6286).</li>
     *   <li><b>Side pipe</b> (grounded): the tile in front of the feet ({@code Level_Tile_InFL}) is a vertical
     *   portal's anchor and the {@code Pipe_PadDirForEnter} direction is held. No alignment window.</li>
     *   <li><b>Upright pipe</b> (grounded, checked when the side pipe test fails): the left foot's tile
     *   ({@code Level_Tile_GndL}) is a cell of a {@code DOWN} portal, DOWN is held and the player is aligned.</li>
     *   <li><b>Upside-down pipe</b> (airborne): the head struck the ceiling this tick, the player is not ducking,
     *   the head tile ({@code Level_Tile_GndR} while rising) is a cell of an {@code UP} portal, UP is held and the
     *   player is aligned.</li>
     * </ul>
     * Directions are read held ({@code Pad_Holding}), never as a fresh press.
     *
     * @return {@code true} when the player started going into a pipe
     */
    default boolean enterPipeIfPossible() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (runtimeState.isInPipe() || runtimeState.getPlayerTailAttackCountdown() > 0) {
            return false;
        }
        final LevelScene levelScene = getLevelScene();
        final List<PipePortal> portals = levelScene.getPipePortals();
        if (portals == null || portals.isEmpty()) {
            return false;
        }
        final Tile[][] tiles = levelScene.getStaticEnvironmentLayer() == null
            ? null
            : levelScene.getStaticEnvironmentLayer().getTiles();
        final int playerX = (int) floor(getPosition().getX());

        if (runtimeState.isInAir()) {
            return enter(findUpsideDownPipe(portals, tiles, playerX).orElse(null), UP);
        }
        final PipeEntryDirection sideDirection = sidePipeDirection(playerX);
        return enter(findSidePipe(portals, tiles, playerX, sideDirection).orElse(null), sideDirection)
            || enter(findUprightPipe(portals, tiles, playerX).orElse(null), DOWN);
    }

    private Optional<PipePortal> findSidePipe(
        final List<PipePortal> portals,
        final Tile[][] tiles,
        final int playerX,
        final PipeEntryDirection direction
    ) {
        if (isNotHolding(direction)) {
            return Optional.empty();
        }
        final Coordinates inFront = fromPlayerOffset(
            this,
            pipeEntryProbes().resolve(false, isOnLeftHalf(playerX)).horizontal().first()
        );
        return findPortal(portals, tiles, playerX, inFront, direction);
    }

    private Optional<PipePortal> findUprightPipe(
        final List<PipePortal> portals,
        final Tile[][] tiles,
        final int playerX
    ) {
        if (isNotHolding(DOWN)) {
            return Optional.empty();
        }
        final Coordinates leftFoot = fromPlayerOffset(
            this,
            pipeEntryProbes().resolve(false, isOnLeftHalf(playerX)).vertical().first()
        );
        return findPortal(portals, tiles, playerX, leftFoot, DOWN)
            .filter(portal -> isAligned(playerX, false, isSmall(), leftFoot.x() == portal.getCoordinates().x()));
    }

    private Optional<PipePortal> findUpsideDownPipe(
        final List<PipePortal> portals,
        final Tile[][] tiles,
        final int playerX
    ) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        if (!runtimeState.isHitCeiling() || runtimeState.isDucking() || isNotHolding(UP)) {
            return Optional.empty();
        }
        final Coordinates head = fromPlayerOffset(
            this,
            pipeEntryProbes().resolve(true, isOnLeftHalf(playerX)).vertical().second()
        );
        return findPortal(portals, tiles, playerX, head, UP)
            .filter(portal -> isAligned(playerX, true, isSmall(), head.x() == portal.getCoordinates().x()));
    }

    /** The enterable portal whose pipe the probed cell reaches, if that pipe is entered holding {@code direction}. */
    private Optional<PipePortal> findPortal(
        final List<PipePortal> portals,
        final Tile[][] tiles,
        final int playerX,
        final Coordinates cell,
        final PipeEntryDirection direction
    ) {
        return portals.stream()
            .filter(portal -> isEnterable(portal) && isEntryCell(portal, cell))
            .filter(portal -> entryDirection(tiles, portal, playerX).filter(direction::equals).isPresent())
            .findFirst();
    }

    private boolean enter(
        final PipePortal portal,
        final PipeEntryDirection direction
    ) {
        if (portal != null) {
            beginPipeEntry(portal, direction);
            return true;
        }
        return false;
    }

    /** The probe set the collision grid used this tick: small when small or ducking, as in its resolveProbe. */
    private DirectionalProbes pipeEntryProbes() {
        return (!isLarge() || getRuntimeState().isDucking()) ? SMALL_PROBES : LARGE_PROBES;
    }

    private static boolean isOnLeftHalf(final int playerX) {
        return (playerX & 15) < 8;
    }

    private boolean isNotHolding(final PipeEntryDirection direction) {
        final String key = switch (direction) {
            case DOWN -> HANDLER_DOWN;
            case UP -> HANDLER_UP;
            case LEFT -> HANDLER_LEFT;
            case RIGHT -> HANDLER_RIGHT;
        };
        return !getInputHandler().isActive(key);
    }

    default void beginPipeEntry(final PipePortal portal, final PipeEntryDirection direction) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        runtimeState.startEnteringPipe(portal, direction);
        position.setDX(0);
        position.setDY(0);
        runtimeState.standUp();
        faceTheWayThrough(direction);
        setVisibility(BACKGROUND);
        updateForegroundLayerBuckets();
        getGameEngine().getCameraState().hold();
        advanceAnimation();
        updateVisualPosition();
    }

    default void tickPipeEntering() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        position.snapshotPrevious();
        runtimeState.decrementPipeCountdown();

        if (runtimeState.getPipeCountdown() == 0) {
            applyHurtFlashVisibility();
            if (!isPipeTransitPublished()) {
                setPipeTransitPublished(true);
                final PipePortal entered = runtimeState.getEnteredPipePortal();
                getGameEngine().publish(
                    PipePortalTransitRequested.builder()
                        .pipePortalId(entered == null ? null : entered.getId())
                        .inputHandlerId(getInputHandler().getId())
                        .build()
                );
            }
            return;
        }

        switch (runtimeState.getPipeEntryDirection()) {
            case DOWN -> position.addToY(PIPE_MOVE_PER_TICK);
            case UP -> position.addToY(-PIPE_MOVE_PER_TICK);
            case RIGHT -> walkIntoSidePipe(PIPE_MOVE_PER_TICK);
            case LEFT -> walkIntoSidePipe(-PIPE_MOVE_PER_TICK);
        }

        advanceAnimation();
        // Player_Draw is where the ROM decrements Player_FlashInv, and it is not called once a side pipe hides the
        // player
        if (!runtimeState.isHiddenInPipe()) {
            runtimeState.decrementHurtInvincibility();
        }
        updateVisualPosition();
    }

    default void beginPipeExit(final PipePortal portal) {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        final Tile[][] tiles = getLevelScene().getStaticEnvironmentLayer() == null
            ? null
            : getLevelScene().getStaticEnvironmentLayer().getTiles();
        final PipeEntryDirection direction = exitDirection(tiles, portal).orElse(null);
        setPipeTransitPublished(false);
        position.setDX(0);
        position.setDY(0);
        runtimeState.standUp();
        runtimeState.stop();
        getGameEngine().getCameraState().setTarget(getNode());

        if (direction == null) {
            runtimeState.stopEnteringPipe();
            standOn(portal);
            return;
        }

        runtimeState.startExitingPipe(portal, direction);
        placeInside(portal, direction);
        faceTheWayThrough(direction);
        position.snapshotPrevious();
        setVisibility(BACKGROUND);
        updateForegroundLayerBuckets();
        advanceAnimation();
        updateVisualPosition();
    }

    default void tickPipeExiting() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        position.snapshotPrevious();

        // The exit starts once the screen has faded back in: Palette_FadeIn runs to the end before Level_MainLoop
        // (dasm prg030 PRG030_8B9A)
        if (getGameEngine().getFadingEngine().isHaltingGameplay()) {
            return;
        }

        switch (runtimeState.getPipeExitDirection()) {
            case UP -> position.addToY(-PIPE_MOVE_PER_TICK);
            case DOWN -> position.addToY(PIPE_EXIT_DOWN_PER_TICK);
            case RIGHT -> position.addToX(PIPE_MOVE_PER_TICK);
            case LEFT -> position.addToX(-PIPE_MOVE_PER_TICK);
        }

        if (runtimeState.getPipeCountdown() == 0) {
            finishPipeExit();
        } else {
            runtimeState.decrementPipeCountdown();
        }

        advanceAnimation();
        runtimeState.decrementHurtInvincibility();
        updateVisualPosition();
    }

    /**
     * Snaps the vertical scroll to the middle of the portal's pipe, for the camera after a level junction. The
     * horizontal axis needs nothing: the camera follows the player node, which {@link #beginPipeExit} has put inside
     * the pipe. Called after {@link #beginPipeExit}, so the scroll can keep where the player stands once out inside
     * its dead-zone band ({@link LevelSceneVerticalScroll#snapTo}).
     *
     * @param portal the portal the player is coming out of
     */
    default void centreVerticalScrollOn(final PipePortal portal) {
        final int rows = getLevelScene().getDimensions().rows();
        final float pipeCentreY = rows - (portal.getTopRow() + portal.getRows() / 2f);
        final float outTopY = (float) (rows - pipeExitEndY() / TILE_SPRITE_SIZE);
        getVerticalScroll().snapTo(pipeCentreY, outTopY);
    }

    /** The Y the player's top reaches when the exit under way ends: one move per countdown value, down to 0. */
    private double pipeExitEndY() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final double y = getPosition().getY();
        if (!runtimeState.isExitingPipe()) {
            return y;
        }
        final int moves = runtimeState.getPipeCountdown() + 1;
        return switch (runtimeState.getPipeExitDirection()) {
            case UP -> y - moves * PIPE_MOVE_PER_TICK;
            case DOWN -> y + moves * PIPE_EXIT_DOWN_PER_TICK;
            case LEFT, RIGHT -> y;
        };
    }

    private void walkIntoSidePipe(final double step) {
        if (getRuntimeState().isWalkingIntoSidePipe()) {
            getPosition().addToX(step);
        }
    }

    /** A side pipe turns the player the way it walks; up and down keep the facing. */
    private void faceTheWayThrough(final PipeEntryDirection direction) {
        switch (direction) {
            case LEFT -> getOrientation().setHorizontal(PlayerOrientationHorizontal.LEFT);
            case RIGHT -> getOrientation().setHorizontal(PlayerOrientationHorizontal.RIGHT);
            default -> {
                // Up and down keep the facing
            }
        }
    }

    private void finishPipeExit() {
        final PlayerRuntimeState runtimeState = getRuntimeState();
        final PlayerPosition position = getPosition();
        final PipeEntryDirection direction = runtimeState.getPipeExitDirection();
        runtimeState.stopExitingPipe();
        position.setDX(0);
        if (direction == DOWN) {
            runtimeState.standUp();
            runtimeState.fall();
            position.setDY(PIPE_EXIT_FALL_DY);
        } else {
            runtimeState.stop();
            position.setDY(0);
        }
        setVisibility(FOREGROUND);
        updateForegroundLayerBuckets();
    }

    private void placeInside(final PipePortal portal, final PipeEntryDirection direction) {
        final PlayerPosition position = getPosition();
        final double centredX = portal.getCoordinates().x() * TILE_SPRITE_SIZE + TILE_SPRITE_SIZE / 2.0;
        final double lipTop = portal.getTopRow() * TILE_SPRITE_SIZE;
        final double lipBottom = (portal.getCoordinates().y() + 1) * TILE_SPRITE_SIZE;
        switch (direction) {
            case UP -> {
                position.setX(centredX);
                position.setY(lipTop - PLAYER_BOX_HEIGHT + PIPE_EXIT_UP_DISTANCE);
            }
            case DOWN -> {
                position.setX(centredX);
                position.setY(lipBottom - PLAYER_BOX_HEIGHT);
            }
            case LEFT, RIGHT -> {
                position.setX(portal.getCoordinates().x() * TILE_SPRITE_SIZE);
                position.setY(lipBottom - PLAYER_BOX_HEIGHT);
            }
        }
    }

    private void standOn(final PipePortal portal) {
        final PlayerPosition position = getPosition();
        position.setX(portal.getCoordinates().x() * TILE_SPRITE_SIZE);
        position.setY(portal.getTopRow() * TILE_SPRITE_SIZE - PLAYER_BOX_HEIGHT);
        position.snapshotPrevious();
        setVisibility(FOREGROUND);
        updateForegroundLayerBuckets();
        advanceAnimation();
        updateVisualPosition();
    }
}
