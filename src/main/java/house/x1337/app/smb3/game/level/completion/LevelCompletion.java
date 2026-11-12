package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.enumeration.MusicTrack;
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
import house.x1337.app.smb3.model.event.SoundPlayComplete;
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
import static house.x1337.app.smb3.enumeration.SoundEffect.CASINO_BEEP;
import static house.x1337.app.smb3.game.player.PlayerData.CARD_SLOTS;
import static java.lang.Math.floor;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.NONE;

/**
 * The level's completion, from the card touch to the level's end, as the end-level card runs it when the cards are
 * not a triple match.
 *
 * <ol>
 *     <li>The touch: see {@link #collect}.</li>
 *     <li>State 4, the card's flight: until the card is off the top of the screen ({@link CardFlight}). Then state 5
 *         starts with a {@link #COURSE_CLEAR_TICKS} timer.</li>
 *     <li>State 5: one {@link TimeBonus} step per tick once the course-clear jingle has played to its end (its
 *         {@link SoundPlayComplete}). It ends when the timer is 0, the clock is all converted and the player is at
 *         the exit. Then state 7 starts with an {@link #EXIT_TICKS} timer.</li>
 *     <li>State 7: when the timer runs out, three held cards are cleared and the level ends
 *         ({@link LevelCompleted}).</li>
 * </ol>
 *
 * <p>Like every object timer, the state timer goes down at the start of every tick that is not halted, before the
 * state runs. In states 4 and 5 the player is halted once it reaches the exit, screen X 32: it has walked off the
 * right of the screen and its screen X, kept to 0-255, has wrapped round.
 */
@Getter
@Prototype
public final class LevelCompletion implements GameEngineAware {
    /** State 5's timer, set as the card leaves the screen. */
    static final int COURSE_CLEAR_TICKS = 96;

    /** State 7's timer. */
    static final int EXIT_TICKS = 128;

    /** Screen X 32 or 33: the player's screen X halved equals this value halved. */
    static final int EXIT_SPRITE_X = 32;

    private static final int SPRITE_X_MASK = 255;

    /** The screen's width, and the lines below its top that count as on screen. */
    static final int SCREEN_WIDTH_PIXELS = 256;
    static final int SCREEN_LINES = 240;

    private final GameEngine gameEngine;
    private final LevelCompletionCardSpawner cardSpawner;

    private boolean courseClearPlaying;
    private LevelCompletionCardType collectedCard;
    private LevelScenePlayer runningOffPlayer;
    private Phase phase = Phase.NOT_STARTED;

    @Getter(NONE)
    private CardFlight cardFlight;
    private int phaseTimer;

    public LevelCompletion(final GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.cardSpawner = getBean(LevelCompletionCardSpawner.class, gameEngine);
    }

    /**
     * Listens for the end of the course-clear jingle, which lets the time bonus start. Called once by each engine as
     * it starts, as an engine's {@code destroy()} purges the bus.
     */
    public void subscribe() {
        gameEngine.getGameEventBus().subscribe(SoundPlayComplete.class, this::onSoundPlayComplete);
    }

    /** @param event a track that has played to its end: the course-clear jingle lets the time bonus start */
    void onSoundPlayComplete(final SoundPlayComplete event) {
        if (event.getTrack() == MusicTrack.COURSE_CLEAR) {
            courseClearPlaying = false;
        }
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
        courseClearPlaying = false;
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
        courseClearPlaying = true;

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
        // The card's visibility is worked out before it moves on this frame
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
        // The time bonus reports "not done" while the course-clear jingle plays, and on the frame of its last step
        final boolean musicPlaying = courseClearPlaying;
        final boolean stepped = !musicPlaying && TimeBonus.convertStep(runningOffPlayer.getPlayerData());
        if (stepped) {
            getSoundPlayer().play(CASINO_BEEP);
        }
        if (musicPlaying || stepped || phaseTimer > 0 || !isPlayerAtTheExit()) {
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
     * The touch's sweep of the screen.
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
     * The on-screen test for the touch's sweep: whatever the object's size, only a 16 x 16 box at its X and Y is
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

    /** Where the completion is; the card's state. */
    public enum Phase {
        NOT_STARTED,
        CARD_FLYING_OFF,
        COURSE_CLEAR,
        EXITING,
        COMPLETED
    }
}
