package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaParatroopaAnimator;
import house.x1337.app.smb3.game.object.level.enemy.motion.KoopaParaTroopaMotionManager;
import house.x1337.app.smb3.model.game.Offset;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * The Koopa Paratroopa's registration: it is offered in the editor as "Koopa Paratroopa - Green", the
 * spawner builds a {@link KoopaParatroopa} from it, and its manager claims it. The class takes the troopa
 * family's {@code KoopaTroopaMode}, defaulting to green, which is the only paratroopa this slice places.
 */
class KoopaParatroopaModeTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(KoopaParatroopaAnimator.class))
            .thenReturn(mock(KoopaParatroopaAnimator.class));
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
        assertThat(KOOPA_PARATROOPA.getInstanceType()).isEqualTo(KoopaParatroopa.class);
        assertThat(KOOPA_PARATROOPA.isMultiTiled()).as("No enemy is single-tiled").isTrue();
        assertThat(new KoopaParaTroopaMotionManager().getType().isInstance(paratroopa()))
            .as("Its manager claims it, so the spawner can route it")
            .isTrue();
    }

    @Test
    @DisplayName("An unconfigured or unrecognised mode falls back to green")
    void defaultsToGreen() {
        // Prepare
        final KoopaParatroopa unconfigured = paratroopa();
        final KoopaParatroopa withRubbish = paratroopa();

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
        final KoopaParatroopa paratroopa = paratroopa();

        // Execute
        paratroopa.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, RED.name())));

        // Verify - LevelObject's default would log the attribute and drop it
        assertThat(KoopaParatroopa.class.getDeclaredMethod("configure", LevelObjectData.class).getDeclaringClass())
            .isEqualTo(KoopaParatroopa.class);
        assertThat(paratroopa.getMode()).isEqualTo(RED);
    }

    private KoopaParatroopa paratroopa() {
        final KoopaParatroopa paratroopa = new KoopaParatroopa(mock(GameEngine.class), Offset.of(0, 0));
        paratroopa.init();
        return paratroopa;
    }
}
