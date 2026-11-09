package house.x1337.app.smb3.converter;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.ImageResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.converter.AnimationImageResourceConverter.paths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class AnimationImageResourceConverterTest {
    @Test
    @DisplayName("A path with neither a <list> nor a {range} is one frame")
    void aPlainPathIsOneFrame() {
        // Execute & Verify
        assertThat(paths("classpath:/sprites/a/b.png")).containsExactly("classpath:/sprites/a/b.png");
    }

    @Test
    @DisplayName("A {range} gives the frames from its first number to its last, both included, as before")
    void aRangeGivesItsFrames() {
        // Execute & Verify
        assertThat(paths("test/frame_{0,3}.png"))
            .containsExactly("test/frame_0.png", "test/frame_1.png", "test/frame_2.png", "test/frame_3.png");
        assertThat(paths("test/frame_{2, 3}.png")).containsExactly("test/frame_2.png", "test/frame_3.png");
    }

    @Test
    @DisplayName("A <list> of folders with a {range} gives each folder's frames, the folders in their order")
    void aListAndARange() {
        // Execute & Verify
        assertThat(paths("test/<foo,bar,lol>/frame_{0,1}.png")).containsExactly(
            "test/foo/frame_0.png",
            "test/foo/frame_1.png",
            "test/bar/frame_0.png",
            "test/bar/frame_1.png",
            "test/lol/frame_0.png",
            "test/lol/frame_1.png"
        );
    }

    @Test
    @DisplayName("A <list> alone gives one frame per alternative, and spaces around them are dropped")
    void aListAlone() {
        // Execute & Verify
        assertThat(paths("test/<foo, bar>/frame.png")).containsExactly("test/foo/frame.png", "test/bar/frame.png");
    }

    @Test
    @DisplayName("Several <lists> combine, the first varying slowest")
    void severalLists() {
        // Execute & Verify
        assertThat(paths("<a,b>/<x,y>.png")).containsExactly("a/x.png", "a/y.png", "b/x.png", "b/y.png");
    }

    @Test
    @DisplayName("An empty alternative is refused")
    void anEmptyAlternativeIsRefused() {
        // Execute & Verify
        assertThatThrownBy(() -> paths("test/<foo,>/frame.png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paths("test/<>/frame.png")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("The card's spin-away loads 9 frames, three per face, from the face folders")
    void theSpinAwayLoadsFromItsFolders() {
        // Execute
        final AnimationImageResource frames;
        try (MockedStatic<StaticBeanFactory> beans = mockStatic(StaticBeanFactory.class)) {
            beans.when(() -> StaticBeanFactory.getBean(ImageResource.class))
                .thenAnswer(invocation -> new ImageResource());
            frames = new AnimationImageResourceConverter()
                .convert("classpath:/sprites/object/card/spin_away/<mushroom,flower,star>/frame_{0,2}.png");
        }

        // Verify
        assertThat(frames.number()).isEqualTo(9);
        assertThat(frames.getDimensionsPixels().width()).as("Every frame was found and read").isEqualTo(16);
        assertThat(frames.getFrameRgbData(5)).as("The Flower's edge-on is the Mushroom's")
            .isEqualTo(frames.getFrameRgbData(2));
        assertThat(frames.getFrameRgbData(3)).as("The Flower's face-on is not the Mushroom's")
            .isNotEqualTo(frames.getFrameRgbData(0));
    }
}
