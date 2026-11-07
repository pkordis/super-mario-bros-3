package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.LevelObjectOffset;
import house.x1337.app.smb3.model.game.collision.DirectionalProbes;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.game.level.pipe.PipeEntryRules.entryDirection;
import static house.x1337.app.smb3.game.level.pipe.PipeEntryRules.isAligned;
import static house.x1337.app.smb3.game.level.pipe.PipeEntryRules.isEnterable;
import static house.x1337.app.smb3.game.level.pipe.PipeEntryRules.isEntryCell;
import static house.x1337.app.smb3.game.level.pipe.PipeEntryRules.sidePipeDirection;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_UP;
import static house.x1337.app.smb3.model.game.LevelObjectOffset.fromPlayerOffset;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.LARGE_PROBES;
import static house.x1337.app.smb3.model.game.collision.CollisionOffsets.SMALL_PROBES;
import static java.lang.Math.floor;

public interface LevelScenePlayerPipeEntryCapable extends LevelScenePlayerRuntimeStateAware, GameEngineAware {
    PlayerInputHandler getInputHandler();
    void beginPipeEntry(PipePortal portal, PipeEntryDirection direction);

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
        if (runtimeState.isEnteringPipe() || runtimeState.getPlayerTailAttackCountdown() > 0) {
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
        final LevelObjectOffset inFront = fromPlayerOffset(
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
        final LevelObjectOffset leftFoot = fromPlayerOffset(
            this,
            pipeEntryProbes().resolve(false, isOnLeftHalf(playerX)).vertical().first()
        );
        return findPortal(portals, tiles, playerX, leftFoot, DOWN)
            .filter(portal -> isAligned(playerX, false, isSmall(), leftFoot.x() == portal.getColumn()));
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
        final LevelObjectOffset head = fromPlayerOffset(
            this,
            pipeEntryProbes().resolve(true, isOnLeftHalf(playerX)).vertical().second()
        );
        return findPortal(portals, tiles, playerX, head, UP)
            .filter(portal -> isAligned(playerX, true, isSmall(), head.x() == portal.getColumn()));
    }

    /** The enterable portal whose pipe the probed cell reaches, if that pipe is entered holding {@code direction}. */
    private static Optional<PipePortal> findPortal(
        final List<PipePortal> portals,
        final Tile[][] tiles,
        final int playerX,
        final LevelObjectOffset cell,
        final PipeEntryDirection direction
    ) {
        return portals.stream()
            .filter(portal -> isEnterable(portal) && isEntryCell(portal, cell.y(), cell.x()))
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
}
