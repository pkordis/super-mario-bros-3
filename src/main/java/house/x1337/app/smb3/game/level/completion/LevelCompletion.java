package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.object.GameObjectAnimator;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.ActiveLevelObjectMotionManager;
import house.x1337.app.smb3.game.object.level.block.motion.CoinRewardMotionManager;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCardMotionManager;
import house.x1337.app.smb3.game.object.level.card.LevelCompletionCardSpawner;
import house.x1337.app.smb3.game.object.level.effect.PoofMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballMotionManager;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.event.LevelCardCollected;
import house.x1337.app.smb3.model.event.LevelCompleted;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Getter;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.game.player.PlayerData.CARD_SLOTS;
import static java.lang.Math.floor;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.NONE;

/**
 * The level's completion, from the card touch to the level's end, as the end-level card object runs it in the ROM
 * ({@code ObjNorm_EndLevelCard}, dasm prg002.asm:5367-5385) when the cards are not a triple match.
 *
 * <ol>
 *     <li>The touch ({@code ObjHit_EndLevelCard}, prg002.asm:6146-6292): see {@link #collect}.</li>
 *     <li>State 4, the card's flight ({@code EndLevelCard_WaitFlyAway}, prg002.asm:5630-5649): until the card is off
 *         the top of the screen ({@link CardFlight}). Then state 5 starts with a {@link #COURSE_CLEAR_TICKS} timer.
 *         </li>
 *     <li>State 5 ({@code EndLevelCard_MsgAndCard}, prg002.asm:5676-5755): one {@link TimeBonus} step per tick once
 *         the course-clear music has ended. It ends when the timer is 0, the clock is all converted and the player
 *         is at the exit. Then state 7 starts with an {@link #EXIT_TICKS} timer.</li>
 *     <li>State 7 ({@code EndLevelCard_Exit}, prg002.asm:5808-5829): when the timer runs out, three held cards are
 *         cleared and the level ends ({@link LevelCompleted}).</li>
 * </ol>
 *
 * <p>Like the ROM's {@code Objects_Timer}, the state timer goes down at the start of every tick that is not halted
 * (prg000.asm:1849-1855), before the state runs. In states 4 and 5 the player is halted once it reaches the exit,
 * sprite X 32 on the screen ({@code EndLevelCard_PlayerRunOff}, prg002.asm:5651-5671): it has walked off the right
 * of the screen and the 8-bit sprite X has wrapped round.
 */
@Getter
@Prototype
public final class LevelCompletion implements GameEngineAware {
    /** State 5's timer, set as the card leaves the screen (dasm prg002.asm:5640-5642). */
    static final int COURSE_CLEAR_TICKS = 0x60;

    /** State 7's timer (dasm prg002.asm:5797-5799). */
    static final int EXIT_TICKS = 0x80;

    /** {@code Player_SpriteX / 2 == $10} (dasm prg002.asm:5663-5666): screen X 32 or 33. */
    static final int EXIT_SPRITE_X = 0x20;

    private static final int SPRITE_X_MASK = 0xFF;

    /** The NES screen's width, and the lines below its top that count as on screen (dasm prg000.asm:5166-5168). */
    static final int SCREEN_WIDTH_PIXELS = 256;
    static final int SCREEN_LINES = 240;

    private final GameEngine gameEngine;
    private final LevelCompletionCardSpawner cardSpawner;
    private final CourseClearMusicStandIn musicStandIn;

    private LevelCompletionCardType collectedCard;
    private LevelScenePlayer runningOffPlayer;
    private Phase phase = Phase.NOT_STARTED;

    @Getter(NONE)
    private CardFlight cardFlight;
    private int phaseTimer;

    public LevelCompletion(final GameEngine gameEngine) {
        this(gameEngine, new CourseClearMusicStandIn());
    }

    LevelCompletion(
        final GameEngine gameEngine,
        final CourseClearMusicStandIn musicStandIn
    ) {
        this.gameEngine = gameEngine;
        this.cardSpawner = getBean(LevelCompletionCardSpawner.class, gameEngine);
        this.musicStandIn = musicStandIn;
    }

    /** @return whether the card has been touched in this run */
    public boolean isUnderway() {
        return phase != Phase.NOT_STARTED;
    }

    /** @return whether the level has ended */
    public boolean isCompleted() {
        return phase == Phase.COMPLETED;
    }

    public void reset() {
        phase = Phase.NOT_STARTED;
        phaseTimer = 0;
        cardFlight = null;
        collectedCard = null;
        runningOffPlayer = null;
        musicStandIn.stop();
        cardSpawner.reset();
    }

    public void spawnCardComingIntoView() {
        cardSpawner.spawnComingIntoView();
    }

    public void collect(
        final LevelCompletionCardType card,
        final LevelScenePlayer player
    ) {
        if (isUnderway()) {
            return;
        }
        final Coordinates cell = requireNonNull(cardSpawner.getCell(), "A card is touched, but the scene has none");
        phase = Phase.CARD_FLYING_OFF;
        cardFlight = new CardFlight(cell.y() * TILE_SPRITE_SIZE);
        collectedCard = card;
        runningOffPlayer = player;
        cardSpawner.retire();
        musicStandIn.start();

        final PlayerData playerData = player.getPlayerData();
        playerData.addCard(card);
        playerData.getPlayerTimer().pause();
        getMotionManagers(GameObjectAnimator.class).forEach(GameObjectAnimator::freeze);
        getCollisionGrid().getPowerSwitchTimeWindow().reset();
        player.getRuntimeState().setKillTally(0);
        player.getPosition().setDY(0);
        player.startRunningOff();
        clearTheObjects(player);

        gameEngine.publish(new LevelCardCollected(card));
    }

    /** Runs the completion for one tick that is not halted, after the players have moved. */
    public void tick() {
        if (phase == Phase.NOT_STARTED || phase == Phase.COMPLETED) {
            return;
        }
        if (runningOffPlayer.getRuntimeState().isDying()) {
            return;
        }
        musicStandIn.tick();
        if (phaseTimer > 0) {
            phaseTimer--;
        }
        switch (phase) {
            case CARD_FLYING_OFF -> tickCardFlyingOff();
            case COURSE_CLEAR -> tickCourseClear();
            case EXITING -> tickExiting();
            default -> throw new IllegalStateException("Unexpected phase " + phase);
        }
    }

    private void tickCardFlyingOff() {
        // Objects_SprVVis is worked out before the card moves on this frame
        final boolean offScreen = cardFlight.isAboveTheTopOf(screenTop());
        cardFlight.advance();
        if (offScreen) {
            phase = Phase.COURSE_CLEAR;
            phaseTimer = COURSE_CLEAR_TICKS;
        }
        haltThePlayerAtTheExit();
    }

    private void tickCourseClear() {
        haltThePlayerAtTheExit();
        // DoTimeBonus reports "not done" while the music plays, and on the frame of its last step
        final boolean converting = musicStandIn.isPlaying() || TimeBonus.convertStep(runningOffPlayer.getPlayerData());
        if (converting || phaseTimer > 0 || !isPlayerAtTheExit()) {
            return;
        }
        // Three mixed cards go through the 1UP display first (SMB3-48); until then they go straight to the exit
        phase = Phase.EXITING;
        phaseTimer = EXIT_TICKS;
    }

    private void tickExiting() {
        if (phaseTimer > 0) {
            return;
        }
        final PlayerData playerData = runningOffPlayer.getPlayerData();
        if (playerData.getCards().get(CARD_SLOTS - 1) != null) {
            playerData.clearCards();
        }
        phase = Phase.COMPLETED;
        gameEngine.publish(new LevelCompleted());
    }

    /**
     * The touch's sweep of the screen ({@code ObjHit_EndLevelCard}, dasm prg002.asm:6158-6226).
     *
     * <p>Every special-object slot is cleared: here the piranha fireballs and the switch-block puffs. Then each object
     * slot except the card's own: an object on screen pops out a coin and goes up in a poof; one off screen is just
     * deleted. Placed enemies the scroll has not reached yet are in no slot and are left alone, and a reward already
     * collected this tick is left to its manager. Either way the object is gone for this run, and a held one is out of
     * the player's hands.
     */
    private void clearTheObjects(final LevelScenePlayer player) {
        getMotionManagers(PiranhaFireballMotionManager.class).forEach(PiranhaFireballMotionManager::removeAll);
        getMotionManagers(PoofMotionManager.class).forEach(PoofMotionManager::removeSwitchPuffs);

        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
        final List<? extends ActiveLevelObjectMotionManager> managers =
            getMotionManagers(ActiveLevelObjectMotionManager.class);
        for (final ActiveLevelObjectMotionManager<?> manager : managers) {
            if (manager instanceof LevelCompletionCardMotionManager) {
                continue;
            }
            final Iterator<? extends ActiveLevelObject> iterator = manager.getActiveInstances().iterator();
            while (iterator.hasNext()) {
                final ActiveLevelObject object = iterator.next();
                if (isInNoSlot(object)) {
                    continue;
                }
                if (isOnScreen(object.getPosition(), screen)) {
                    popOutACoin(object, player);
                }
                if (player.getRuntimeState().getCarriedObject() == object) {
                    player.getRuntimeState().setCarriedObject(null);
                }
                object.expire();
                object.detach();
                iterator.remove();
                if (object instanceof final EnemyLevelObject enemy) {
                    getActiveObjectGrid().retirePlacementOf(enemy);
                }
            }
        }
    }

    private static boolean isInNoSlot(final ActiveLevelObject object) {
        return (object instanceof final EnemyLevelObject enemy && !enemy.isSpawnedIntoScene())
            || (object instanceof final RewardLevelObject reward && reward.isCollected());
    }

    /**
     * {@code Object_AnySprOffscreen} after {@code Object_DetermineHorzVisY} and {@code Object_DetermineVertVisY} with
     * {@code Y = 0} (dasm prg002.asm:6174-6180): whatever the object's size, only a 16 x 16 box at its X and Y is
     * tested. Its X must be on the 256 px screen and its bottom ({@code Y + 16}) within the 240 lines below the top.
     *
     * @param position the object's top-left
     * @param screen   the view
     * @return whether the object counts as on screen
     */
    static boolean isOnScreen(
        final PixelPosition position,
        final AxisAlignedBoundingBox screen
    ) {
        final double spriteX = floor(position.x()) - floor(screen.left());
        final double spriteBottom = floor(position.y()) + TILE_SPRITE_SIZE - floor(screen.top());
        return spriteX >= 0 && spriteX < SCREEN_WIDTH_PIXELS && spriteBottom >= 0 && spriteBottom < SCREEN_LINES;
    }

    private void popOutACoin(
        final ActiveLevelObject object,
        final LevelScenePlayer player
    ) {
        final PixelPosition position = object.getPosition();
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        final WorldOffset poofBottomLeft = WorldOffset.of(
            (float) (position.x() / TILE_SPRITE_SIZE),
            rows - (float) ((position.y() + TILE_SPRITE_SIZE) / TILE_SPRITE_SIZE),
            Z_DEPTH_POOF
        );
        getMotionManagers(PoofMotionManager.class).forEach(poofs -> poofs.spawnPoofDeath(gameEngine, poofBottomLeft));
        getMotionManagers(CoinRewardMotionManager.class).forEach(coins -> coins.spawnPoppedOutCoin(player, position));
    }

    private void haltThePlayerAtTheExit() {
        if (isPlayerAtTheExit()) {
            runningOffPlayer.haltRunOff();
        }
    }

    private boolean isPlayerAtTheExit() {
        final double screenLeft = gameEngine.getCameraState().getActiveObjectRegion(0).left();
        final int spriteX = (int) floor(runningOffPlayer.getPosition().getX() - screenLeft) & SPRITE_X_MASK;
        return spriteX >> 1 == EXIT_SPRITE_X >> 1;
    }

    private double screenTop() {
        return gameEngine.getCameraState().getActiveObjectRegion(0).top();
    }

    /** Where the completion is; the ROM's {@code Objects_DetStat} for the card. */
    public enum Phase {
        NOT_STARTED,
        CARD_FLYING_OFF,
        COURSE_CLEAR,
        EXITING,
        COMPLETED
    }
}
