package house.x1337.app.smb3.game.object.level.card;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.completion.CardFlight;
import house.x1337.app.smb3.game.level.completion.LevelCompletion;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.FanfareCard;
import house.x1337.app.smb3.game.object.level.Animation;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.WorldOffset;
import lombok.Getter;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE_GAME_UNITS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ITEM_REWARD;
import static java.lang.Math.floorDiv;
import static java.lang.Math.floorMod;

/**
 * The collected card spinning away from where it was touched: up off the screen, or, in the fanfare of three matching
 * cards, as the level completion's {@link FanfareCard} says on each tick (flying and drifting left, twinkling, gone).
 */
@Getter
@Prototype
public final class LevelCompletionCardSpinAwayAnimation implements Animation {
    public static final int FIRST_SPIN_FRAME = 3;
    public static final int SPIN_FRAME_STEP = 3;
    public static final int SPIN_FRAMES = 12;
    public static final int SPIN_COUNTER_MASK = 3;

    static final int[] PICTURE_BY_FRAME_GROUP = {0, 1, 2, 1};
    static final int PICTURES_PER_FACE = 3;
    static final int DRIFT_SIXTEENTHS = 16;
    static final int DRIFT_STOP_X = 7;

    private static final int TWINKLES = 2;
    private static final Dimensions CARD_DIMENSIONS = new Dimensions(
        "LevelCompletionCardSpinAway",
        TILE_SIZE_GAME_UNITS,
        TILE_SIZE_GAME_UNITS
    );
    private static final DimensionsPixels CARD_PIXELS = new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE);

    private GameEngine gameEngine;
    private Node rootNode;
    private Geometry spriteGeometry;
    private Material material;
    private Texture[] pictures;
    private Texture[] twinkles;
    private CardFlight flight;
    private int xSixteenths;
    private int frame;
    private boolean expired;

    /** @param frames the spin pictures, three per face in {@link LevelCompletionCardType} order */
    public void configure(
        final GameEngine gameEngine,
        final Coordinates cell,
        final LevelCompletionCardType face,
        final AnimationImageResource frames,
        final AnimationImageResource twinkle
    ) {
        this.gameEngine = gameEngine;
        this.rootNode = gameEngine.getRootNode();
        this.xSixteenths = cell.x() * TILE_SPRITE_SIZE * DRIFT_SIXTEENTHS;
        this.flight = new CardFlight(cell.y() * TILE_SPRITE_SIZE);
        this.frame = FIRST_SPIN_FRAME + face.ordinal();
        this.pictures = loadPictures(frames, face);
        this.twinkles = loadTwinkles(twinkle);
        this.spriteGeometry = fromTexture(gameEngine.getAssetManager(), pictureOf(frame), CARD_DIMENSIONS);
        this.material = spriteGeometry.getMaterial();
        getWorldOffset().applyTo(spriteGeometry);
        rootNode.attachChild(spriteGeometry);
    }

    @Override
    public void tick() {
        if (expired) {
            return;
        }
        final LevelCompletion levelCompletion = gameEngine.getLevelCompletion();
        if (levelCompletion.isFanfare()) {
            tickInTheFanfare(levelCompletion.getFanfareCard());
            return;
        }
        // The card's vertical visibility is worked out before it moves on this frame
        final boolean offScreen = flight.isAboveTheTopOf(gameEngine.getCameraState().getActiveObjectRegion(0).top());
        flight.advance();
        if (offScreen) {
            expired = true;
            return;
        }
        getWorldOffset().applyTo(spriteGeometry);
        spinOnItsFrames();
    }

    @Override
    public WorldOffset getWorldOffset() {
        final int rows = gameEngine.getLevelScene().getDimensions().rows();
        return WorldOffset.of(
            (float) getX() / TILE_SPRITE_SIZE,
            rows - (float) (flight.y() + TILE_SPRITE_SIZE) / TILE_SPRITE_SIZE,
            Z_DEPTH_ITEM_REWARD
        );
    }

    public int getX() {
        return floorDiv(xSixteenths, DRIFT_SIXTEENTHS);
    }

    /** @return the picture shown: 0 face-on, 1 narrow, 2 edge-on */
    public int getPicture() {
        return PICTURE_BY_FRAME_GROUP[(frame - FIRST_SPIN_FRAME) / SPIN_FRAME_STEP];
    }

    @Override
    public void detach() {
        rootNode.detachChild(spriteGeometry);
    }

    private void tickInTheFanfare(final FanfareCard fanfareCard) {
        switch (fanfareCard) {
            case FLYING -> {
                if (floorMod(getX(), TILE_SPRITE_SIZE) != DRIFT_STOP_X) {
                    xSixteenths--;
                }
                flight.advance();
                getWorldOffset().applyTo(spriteGeometry);
                spinOnItsFrames();
            }
            case SMALL_TWINKLE -> material.setTexture("ColorMap", twinkles[0]);
            case LARGE_TWINKLE -> material.setTexture("ColorMap", twinkles[1]);
            case GONE -> expired = true;
        }
    }

    private void spinOnItsFrames() {
        if ((gameEngine.getLevelFrameCounter().getTicks() & SPIN_COUNTER_MASK) == 0) {
            spin();
        }
    }

    private void spin() {
        frame -= SPIN_FRAME_STEP;
        if (frame < FIRST_SPIN_FRAME) {
            frame += SPIN_FRAMES;
        }
        material.setTexture("ColorMap", pictureOf(frame));
    }

    private Texture pictureOf(final int spinFrame) {
        return pictures[PICTURE_BY_FRAME_GROUP[(spinFrame - FIRST_SPIN_FRAME) / SPIN_FRAME_STEP]];
    }

    private Texture[] loadPictures(
        final AnimationImageResource frames,
        final LevelCompletionCardType face
    ) {
        final Texture[] textures = new Texture[PICTURES_PER_FACE];
        for (int picture = 0; picture < PICTURES_PER_FACE; picture++) {
            final int[] pixels = frames.getFrameRgbData(face.ordinal() * PICTURES_PER_FACE + picture);
            textures[picture] = loadTexture(pixels, CARD_PIXELS);
        }
        return textures;
    }

    private Texture[] loadTwinkles(final AnimationImageResource twinkle) {
        final Texture[] textures = new Texture[TWINKLES];
        for (int picture = 0; picture < TWINKLES; picture++) {
            textures[picture] = loadTexture(twinkle.getFrameRgbData(picture), CARD_PIXELS);
        }
        return textures;
    }
}
