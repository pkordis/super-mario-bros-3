package house.x1337.app.smb3.jme3.core;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Holding the camera keeps it where its target was, however far the target moves afterwards, until it is given a
 * new target. Going into a pipe relies on this so a side pipe does not scroll the screen.
 */
class CameraStateHoldTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(
            StaticBeanFactory.class,
            invocation -> mock(invocation.<Class<?>>getArgument(0))
        );
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A held camera stays where the target was until a new target is set")
    void aHeldCameraStaysPut() {
        // Prepare
        final CameraState cameraState = new CameraState();
        final Node player = new Node("PlayerNode");
        player.setLocalTranslation(5, 3, 0);
        cameraState.setTarget(player);

        // Execute
        cameraState.hold();
        player.setLocalTranslation(6.375f, 3, 0);

        // Verify
        assertThat(cameraState.getTarget().getLocalTranslation().x).isEqualTo(5);

        // Execute & Verify - the rebuilt level's new target is followed again
        final Node respawned = new Node("PlayerNode");
        respawned.setLocalTranslation(1, 2, 0);
        cameraState.setTarget(respawned);
        assertThat(cameraState.getTarget().getLocalTranslation().x).isEqualTo(1);
    }
}
