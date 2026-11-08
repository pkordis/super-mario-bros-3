package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaParaTroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.motion.KoopaParaTroopaMotionManager;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_PARATROOPA;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * The Koopa Paratroopa's registration: it is offered in the editor as "Koopa Paratroopa - Green" and
 * "Koopa Paratroopa - Red", the spawner builds a {@link KoopaParaTroopa} from either, and its manager claims
 * it. The class takes the troopa family's {@code KoopaTroopaMode}, defaulting to green.
 */
class KoopaParaTroopaModeTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaParaTroopaAnimator.class))
            .thenReturn(mock(KoopaParaTroopaAnimator.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The green paratroopa is placeable from the editor and names the paratroopa entity")
    void theGreenParatroopaIsRegistered() {
        // Prepare & Execute
        final EnemyType green = EnemyType.KOOPA_PARATROOPA_GREEN;

        // Verify
        assertThat(green.getLabel()).isEqualTo("Koopa Paratroopa - Green");
        assertThat(green.getLevelObjectType()).isEqualTo(KOOPA_PARATROOPA);
        assertThat(green.getMode()).isEqualTo(GREEN);
        assertThat(KOOPA_PARATROOPA.getInstanceType()).isEqualTo(KoopaParaTroopa.class);
        assertThat(KOOPA_PARATROOPA.isMultiTiled()).as("No enemy is single-tiled").isTrue();
        assertThat(new KoopaParaTroopaMotionManager().getType().isInstance(paratroopa()))
            .as("Its manager claims it, so the spawner can route it")
            .isTrue();
    }

    @Test
    @DisplayName("The red paratroopa is placeable from the editor as the same entity in its red mode")
    void theRedParatroopaIsRegistered() {
        // Prepare & Execute
        final EnemyType red = EnemyType.KOOPA_PARATROOPA_RED;

        // Verify
        assertThat(red.getLabel()).isEqualTo("Koopa Paratroopa - Red");
        assertThat(red.getLevelObjectType()).isEqualTo(KOOPA_PARATROOPA);
        assertThat(red.getMode()).isEqualTo(RED);
    }

    @Test
    @DisplayName("An unconfigured or unrecognised mode falls back to green")
    void defaultsToGreen() {
        // Prepare
        final KoopaParaTroopa unconfigured = paratroopa();
        final KoopaParaTroopa withRubbish = paratroopa();

        // Execute
        withRubbish.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, "PUCE")));

        // Verify
        assertThat(unconfigured.getMode()).isEqualTo(GREEN);
        assertThat(withRubbish.getMode()).isEqualTo(GREEN);
    }

    @Test
    @DisplayName("The mode the spawner writes is read by the paratroopa's own configure")
    void theSpawnersModeIsConsumed() throws NoSuchMethodException {
        // Prepare
        final KoopaParaTroopa paratroopa = paratroopa();

        // Execute
        paratroopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, RED.name())));

        // Verify - LevelObject's default would log the attribute and drop it
        assertThat(KoopaParaTroopa.class.getDeclaredMethod("configure", LevelObjectData.class).getDeclaringClass())
            .isEqualTo(KoopaParaTroopa.class);
        assertThat(paratroopa.getMode()).isEqualTo(RED);
    }

    private KoopaParaTroopa paratroopa() {
        final KoopaParaTroopa paratroopa = new KoopaParaTroopa(mock(GameEngine.class), of(0, 0));
        paratroopa.init();
        return paratroopa;
    }
}
