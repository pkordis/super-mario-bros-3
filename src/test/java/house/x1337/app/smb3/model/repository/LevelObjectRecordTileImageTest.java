package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.COIN_FLIPPING;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** A single-tiled level object looks like the tile painted in its cell, so every tile linked to it keeps its look. */
class LevelObjectRecordTileImageTest {
    private final List<ImageResource> images = new ArrayList<>();
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class, this::answer);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Two tiles linked to one level object are drawn each with its own pixels")
    void eachLinkedTileKeepsItsPixels() {
        // Prepare
        final LevelObjectRecord coin = LevelObjectRecord.builder().id(1500).type(COIN_FLIPPING.name()).build();
        final int[] yellow = {1, 1, 1};
        final int[] blue = {2, 2, 2};

        // Execute
        coin.toLevelObject(mock(GameEngine.class), of(0, 0), Tile.builder().id(2000).argbData(yellow).build());
        coin.toLevelObject(mock(GameEngine.class), of(1, 0), Tile.builder().id(2001).argbData(blue).build());

        // Verify
        assertThat(images).extracting(ImageResource::getRgbData).containsExactly(yellow, blue);
    }

    private Object answer(final InvocationOnMock invocation) {
        final List<Object> arguments = new ArrayList<>();
        for (final Object argument : invocation.getArguments()) {
            if (argument instanceof final Object[] array) {
                arguments.addAll(Arrays.asList(array));
            } else {
                arguments.add(argument);
            }
        }
        if (arguments.getFirst() == ImageResource.class) {
            return new ImageResource();
        }
        for (final Object argument : arguments) {
            if (argument instanceof final ImageResource image) {
                images.add(image);
            }
        }
        return mock(LevelObject.class);
    }
}
