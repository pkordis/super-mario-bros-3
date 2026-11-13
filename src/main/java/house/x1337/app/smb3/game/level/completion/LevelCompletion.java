package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.game.camera.LevelSceneVerticalScroll;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.GameEngineAware;
import house.x1337.app.smb3.game.level.completion.LevelCompletionScreen.FireworksPicture;
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
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.SoundEffect.CASINO_BEEP;
import static house.x1337.app.smb3.enumeration.SoundEffect.ONE_UP;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard.LARGE_TWINKLE;
import static house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard.SMALL_TWINKLE;
import static house.x1337.app.smb3.game.player.PlayerData.CARD_SLOTS;
import static java.lang.Math.floor;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.NONE;

/**
 * Runs the level's end, from the touch of the end-level card to {@link LevelCompleted}: the card's flight or the
 * fanfare of three matching cards, the time bonus, the messages, the lives bonus and the exit, one {@link Phase} after
 * another on a state timer.
 *
 * <p>See {@code kb/level-completion.md}, "LevelCompletion at a glance", for the phases and their timings.
 */
@Getter
@Prototype
public final class LevelCompletion implements GameEngineAware {
    static final int COURSE_CLEAR_MESSAGE_TICK = 30;
    static final int COURSE_CLEAR_TICKS = 96;
    static final int EXIT_SPRITE_X = 32;
    static final int EXIT_TICKS = 128;
    static final int FADE_FRAMES = 4;
    static final int FANFARE_LIVES_BONUS_X = 116;
    static final int FANFARE_MESSAGE_TICK = 80;
    static final int FANFARE_MESSAGE_TICKS = 128;
    static final int FANFARE_SCROLL_PIXELS = 3;
    static final int FANFARE_VIEW_TOP = 17;
    static final int LIVES_BONUS_BOUNCE_TICKS = 64;
    static final int LIVES_BONUS_X = 124;
    static final int SCREEN_WIDTH_PIXELS = 256;
    static final int SCREEN_LINES = 240;
    static final int SKY_CARD_Y = 272;
    static final int SPARKLE_FRAMES = 8;
    static final int TWINKLE_TICKS = 16;
    static final int YOU_GOT_A_CARD_TICK = 1;

    private static final int MIXED_CARDS_LIVES = 1;
    private static final int THREE_MUSHROOMS_LIVES = 2;
    private static final int THREE_FLOWERS_LIVES = 3;
    private static final int THREE_STARS_LIVES = 5;
    private static final int SPARKLES = 4;
    private static final int SPRITE_X_MASK = 255;
    private static final int FADE_STEPS = FireworksFade.values().length + 1;
    private static final FanfareCard[] TWINKLE_BY_HALF_TICKS = {
        SMALL_TWINKLE,
        SMALL_TWINKLE,
        SMALL_TWINKLE,
        LARGE_TWINKLE,
        SMALL_TWINKLE,
        LARGE_TWINKLE,
        LARGE_TWINKLE,
        LARGE_TWINKLE
    };

    private final GameEngine gameEngine;
    private final LevelCompletionCardSpawner cardSpawner;
    @Getter(NONE)
    private final StatusBarCards statusBarCards = new StatusBarCards();

    private boolean jinglePlaying;
    private LevelCompletionCardType collectedCard;
    private LevelScenePlayer player;
    private Phase phase = Phase.NOT_STARTED;
    private boolean fanfare;
    private FanfareCard fanfareCard = FanfareCard.GONE;

    @Getter(NONE)
    private CardFlight cardFlight;
    private int phaseTimer;
    private Message message = Message.NONE;
    @Getter(NONE)
    private int fadeSteps;
    @Getter(NONE)
    private LivesBonus livesBonus;

    public LevelCompletion(final GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.cardSpawner = getBean(LevelCompletionCardSpawner.class, gameEngine);
    }

    public void subscribe() {
        gameEngine.getGameEventBus().subscribe(SoundPlayComplete.class, this::onSoundPlayComplete);
    }

    void onSoundPlayComplete(final SoundPlayComplete event) {
        if (event.getTrack() == (fanfare ? MusicTrack.VICTORY_FANFARE : MusicTrack.COURSE_CLEAR)) {
            jinglePlaying = false;
        }
    }

    public boolean isUnderway() {
        return phase != Phase.NOT_STARTED;
    }

    public boolean isCompleted() {
        return phase == Phase.COMPLETED;
    }

    public boolean isCardSlotHidden(final int slot) {
        return isUnderway() && !isCompleted() && statusBarCards.isHidden(slot);
    }

    public LevelCompletionScreen getScreen() {
        if (!isUnderway()) {
            return LevelCompletionScreen.NOTHING;
        }
        // The fade's first step shows nothing yet, so step n of the fireworks is the fade's step n + 1
        final FireworksPicture fireworks = fanfare && fadeSteps > 1
            ? new FireworksPicture(collectedCard, fireworksFadeAt(fadeSteps - 1), sparkle())
            : null;
        return new LevelCompletionScreen(
            message,
            collectedCard,
            fanfare ? player.getVerticalScroll().getViewTop() : 0,
            fireworks,
            livesBonus == null ? null : livesBonus.picture()
        );
    }

    public void reset() {
        phase = Phase.NOT_STARTED;
        phaseTimer = 0;
        cardFlight = null;
        collectedCard = null;
        player = null;
        jinglePlaying = false;
        message = Message.NONE;
        fanfare = false;
        fanfareCard = FanfareCard.GONE;
        fadeSteps = 0;
        livesBonus = null;
        statusBarCards.reset();
        cardSpawner.reset();
    }

    public void spawnCardComingIntoView() {
        cardSpawner.spawnComingIntoView();
    }

    /**
     * The touch of the card: gives it to the player, stops the clock and the player, mutes the sound effects, clears
     * the screen's objects and starts the flight or the fanfare.
     */
    public void collect(
        final LevelCompletionCardType card,
        final LevelScenePlayer player
    ) {
        if (isUnderway()) {
            return;
        }
        final Coordinates cell = requireNonNull(cardSpawner.getCell(), "A card is touched, but the scene has none");
        cardFlight = new CardFlight(cell.y() * TILE_SPRITE_SIZE);
        collectedCard = card;
        this.player = player;
        cardSpawner.retire();
        jinglePlaying = true;

        final PlayerData playerData = player.getPlayerData();
        playerData.addCard(card);
        statusBarCards.hideTheNewest(newestSlotOf(playerData.getCards()));
        fanfare = isThreeOfAKind(playerData.getCards());
        phase = fanfare ? Phase.FANFARE_RISING : Phase.CARD_FLYING_OFF;
        fanfareCard = fanfare ? FanfareCard.FLYING : FanfareCard.GONE;
        playerData.getPlayerTimer().pause();
        if (!fanfare) {
            getMotionManagers(GameObjectAnimator.class).forEach(GameObjectAnimator::freeze);
        }
        getCollisionGrid().getPowerSwitchTimeWindow().reset();
        player.getRuntimeState().setKillTally(0);
        player.getPosition().setDY(0);
        if (fanfare) {
            player.standStill();
        } else {
            player.startRunningOff();
        }
        // Before the sweep, whose popped-out coins must not be heard either
        getSoundPlayer().muteEffectsExcept(CASINO_BEEP);
        clearTheObjects(player);

        gameEngine.publish(new LevelCardCollected(card, fanfare));
    }

    /** Runs one tick that is not halted, after the players have moved. */
    public void tick() {
        if (phase == Phase.NOT_STARTED || phase == Phase.COMPLETED) {
            return;
        }
        if (player.getRuntimeState().isDying()) {
            return;
        }
        if (phaseTimer > 0) {
            phaseTimer--;
        }
        statusBarCards.countDown();
        if (livesBonus != null) {
            livesBonus.countDown();
        }
        switch (phase) {
            case FANFARE_RISING -> tickFanfareRising();
            case FANFARE_APPEARING -> tickFanfareAppearing();
            case FANFARE_MESSAGE -> tickFanfareMessage();
            case CARD_FLYING_OFF -> tickCardFlyingOff();
            case COURSE_CLEAR -> tickCourseClear();
            case PAYING_OUT_LIVES -> tickPayingOutLives();
            case EXITING -> tickExiting();
            default -> throw new IllegalStateException("Unexpected phase " + phase);
        }
    }

    private void tickFanfareRising() {
        if (phaseTimer > 0) {
            final int halfTicks = phaseTimer / 2;
            fanfareCard = TWINKLE_BY_HALF_TICKS[halfTicks];
            if (halfTicks == 0) {
                phase = Phase.FANFARE_APPEARING;
            }
            return;
        }
        fanfareCard = FanfareCard.FLYING;
        cardFlight.advance();
        if (cardFlight.y() >= SKY_CARD_Y) {
            return;
        }
        final LevelSceneVerticalScroll verticalScroll = player.getVerticalScroll();
        verticalScroll.scrollUp(FANFARE_SCROLL_PIXELS);
        if (verticalScroll.getViewTop() < FANFARE_VIEW_TOP) {
            phaseTimer = TWINKLE_TICKS;
        }
    }

    private void tickFanfareAppearing() {
        fanfareCard = FanfareCard.GONE;
        if (frame() % FADE_FRAMES != 0) {
            return;
        }
        fadeSteps++;
        if (fadeSteps == FADE_STEPS) {
            phase = Phase.FANFARE_MESSAGE;
            phaseTimer = FANFARE_MESSAGE_TICKS;
        }
    }

    private void tickFanfareMessage() {
        if (phaseTimer == FANFARE_MESSAGE_TICK) {
            message = Message.FANFARE_YOU_GOT_A_CARD;
            statusBarCards.holdTheNewest();
            return;
        }
        if (phaseTimer < FANFARE_MESSAGE_TICK) {
            statusBarCards.drawTheNewestFlashing(frame());
        }
        if (stepTheTimeBonus() && phaseTimer == 0) {
            startPayingOut(livesForThreeOf(collectedCard));
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
        showTheMessages();
        if (stepTheTimeBonus() && phaseTimer == 0 && isPlayerAtTheExit()) {
            if (player.getPlayerData().getCards().get(CARD_SLOTS - 1) != null) {
                startPayingOut(MIXED_CARDS_LIVES);
                return;
            }
            phase = Phase.EXITING;
            phaseTimer = EXIT_TICKS;
        }
        if (phaseTimer == 0) {
            statusBarCards.drawTheNewestFlashing(frame());
        }
    }

    private void showTheMessages() {
        if (phaseTimer == COURSE_CLEAR_MESSAGE_TICK) {
            message = Message.COURSE_CLEAR;
        } else if (phaseTimer == YOU_GOT_A_CARD_TICK) {
            message = Message.YOU_GOT_A_CARD;
            statusBarCards.holdTheNewest();
        }
    }

    /** @return whether the time bonus is done: the jingle over and nothing left to convert */
    private boolean stepTheTimeBonus() {
        if (jinglePlaying) {
            return false;
        }
        final boolean stepped = TimeBonus.convertStep(player.getPlayerData());
        if (stepped) {
            getSoundPlayer().play(CASINO_BEEP);
        }
        return !stepped;
    }

    private void startPayingOut(final int lives) {
        livesBonus = new LivesBonus(lives, fanfare ? FANFARE_LIVES_BONUS_X : LIVES_BONUS_X);
        phase = Phase.PAYING_OUT_LIVES;
        getSoundPlayer().muteEffectsExcept(CASINO_BEEP, ONE_UP);
    }

    private void tickPayingOutLives() {
        payOutAndCycleTheCards();
        if (livesBonus.isRising()) {
            phaseTimer = LIVES_BONUS_BOUNCE_TICKS;
            livesBonus.rise();
            return;
        }
        if (phaseTimer > 0) {
            livesBonus.bounce();
            return;
        }
        if (livesBonus.isPaidOut()) {
            phase = Phase.EXITING;
            phaseTimer = EXIT_TICKS;
        }
    }

    private void payOutAndCycleTheCards() {
        livesBonus.payOut(player.getPlayerData(), getSoundPlayer());
        statusBarCards.cycle(frame());
    }

    private void tickExiting() {
        if (phaseTimer > 0) {
            if (livesBonus != null) {
                payOutAndCycleTheCards();
            } else {
                statusBarCards.drawTheNewestFlashing(frame());
            }
            return;
        }
        final PlayerData playerData = player.getPlayerData();
        if (playerData.getCards().get(CARD_SLOTS - 1) != null) {
            playerData.clearCards();
        }
        phase = Phase.COMPLETED;
        gameEngine.publish(new LevelCompleted());
    }

    private int livesForThreeOf(final LevelCompletionCardType card) {
        return switch (card) {
            case MUSHROOM -> THREE_MUSHROOMS_LIVES;
            case FLOWER -> THREE_FLOWERS_LIVES;
            case STAR -> THREE_STARS_LIVES;
        };
    }

    private boolean isThreeOfAKind(final List<LevelCompletionCardType> cards) {
        return cards.getLast() != null && cards.stream().distinct().count() == 1;
    }

    private int newestSlotOf(final List<LevelCompletionCardType> cards) {
        int newest = 0;
        for (int slot = 0; slot < cards.size(); slot++) {
            if (cards.get(slot) != null) {
                newest = slot;
            }
        }
        return newest;
    }

    private FireworksFade fireworksFadeAt(final int step) {
        return Arrays.stream(FireworksFade.values())
            .filter(fade -> fade.getStep() == step)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No fireworks fade step " + step));
    }

    private int frame() {
        return gameEngine.getLevelFrameCounter().getTicks();
    }

    private int sparkle() {
        return frame() / SPARKLE_FRAMES % SPARKLES;
    }

    /** The touch's sweep: objects on screen pop out a coin and go up in a poof, the others are just deleted. */
    private void clearTheObjects(final LevelScenePlayer player) {
        getMotionManagers(PiranhaFireballMotionManager.class).forEach(PiranhaFireballMotionManager::removeAll);
        getMotionManagers(PoofMotionManager.class).forEach(PoofMotionManager::removeSwitchPuffs);

        @SuppressWarnings("unchecked")
        final List<ActiveLevelObjectMotionManager<?>> managers = (List<ActiveLevelObjectMotionManager<?>>)
            getMotionManagers(ActiveLevelObjectMotionManager.class);
        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
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

    /** Whatever the object's size, only a 16 x 16 box at its top-left is tested against the screen. */
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
            player.haltRunOff();
        }
    }

    private boolean isPlayerAtTheExit() {
        final double screenLeft = gameEngine.getCameraState().getActiveObjectRegion(0).left();
        final int spriteX = (int) floor(player.getPosition().getX() - screenLeft) & SPRITE_X_MASK;
        return spriteX >> 1 == EXIT_SPRITE_X >> 1;
    }

    private double screenTop() {
        return gameEngine.getCameraState().getActiveObjectRegion(0).top();
    }

    public enum Phase {
        NOT_STARTED,
        FANFARE_RISING,
        FANFARE_APPEARING,
        FANFARE_MESSAGE,
        CARD_FLYING_OFF,
        COURSE_CLEAR,
        PAYING_OUT_LIVES,
        EXITING,
        COMPLETED
    }

    /** What the collected card is during the fanfare, on this tick. */
    public enum FanfareCard {
        FLYING,
        SMALL_TWINKLE,
        LARGE_TWINKLE,
        GONE
    }

    /** A message written on the screen, kept until the level ends. */
    @Getter
    @RequiredArgsConstructor
    public enum Message {
        NONE(""),
        COURSE_CLEAR("COURSE CLEAR !"),
        YOU_GOT_A_CARD("YOU GOT A CARD"),
        FANFARE_YOU_GOT_A_CARD("YOU GOT A CARD");

        private final String text;
    }
}
