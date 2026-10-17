package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.enumeration.enemy.GoombaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.service.LevelObjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PARA_GOOMBA;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.RED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * {@code configure} selects which {@code sprites/enemy/paragoomba/<mode>} bundle a placed Para-Goomba
 * animates from. The modes are the family's shared {@code GoombaMode} - the Para-Goomba tints the
 * Goomba's own body art - and {@code EnemyAnimator}'s asset cache is already keyed by
 * {@code EnemyMode} and rooted at each animator's own folder, so two palettes need no loader changes
 * at all.
 */
class ParaGoombaModeTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        gameEngine = mock(GameEngine.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(mock(ParaGoombaAnimator.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("An unconfigured Para-Goomba wears the normal palette")
    void defaultsToNormal() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoomba();

        // Execute & Verify
        assertThat(paraGoomba.getMode()).isEqualTo(NORMAL);
        assertThat(paraGoomba.getMode().getFramesFolder()).isEqualTo("normal");
    }

    @Test
    @DisplayName("A mode attribute of RED switches it to the red palette's own sprite folder")
    void configuresTheRedPalette() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoomba();

        // Execute
        paraGoomba.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, RED.name())));

        // Verify
        assertThat(paraGoomba.getMode()).isEqualTo(RED);
        assertThat(paraGoomba.getMode().getFramesFolder()).isEqualTo("red");
    }

    @Test
    @DisplayName("Each mode resolves a distinct sprite folder, so the two never share a cache entry")
    void eachModeResolvesItsOwnFolder() {
        // Prepare & Execute
        final String normalFolder = NORMAL.getFramesFolder();
        final String redFolder = RED.getFramesFolder();

        // Verify
        assertThat(normalFolder).isNotEqualTo(redFolder);
        assertThat(GoombaMode.values()).containsExactly(NORMAL, RED);
    }

    @Test
    @DisplayName("An absent or unrecognised mode falls back to normal rather than failing")
    void unknownModeFallsBackToNormal() {
        // Prepare
        final ParaGoomba withNoAttributes = paraGoomba();
        final ParaGoomba withRubbish = paraGoomba();

        // Execute
        withNoAttributes.configure(new LevelObjectData(Map.of()));
        withRubbish.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, "PUCE")));

        // Verify
        assertThat(withNoAttributes.getMode()).isEqualTo(NORMAL);
        assertThat(withRubbish.getMode()).isEqualTo(NORMAL);
    }

    @Test
    @DisplayName("A valid mode is consumed by the entity, never left to the warning default")
    void aValidModeIsConsumedRatherThanDisregarded() throws NoSuchMethodException {
        // Prepare - LevelObject's default configure logs every attribute it does not recognise and drops
        // it, so the only way a mode arrives without a warning is the entity overriding configure.
        final var override = ParaGoomba.class.getDeclaredMethod("configure", LevelObjectData.class);

        // Execute
        final ParaGoomba paraGoomba = paraGoomba();
        paraGoomba.configure(new LevelObjectData(Map.of(MODE_ATTRIBUTE, RED.name())));

        // Verify
        assertThat(override.getDeclaringClass())
            .as("configure is ParaGoomba's own, not LevelObject's warn-and-discard default")
            .isEqualTo(ParaGoomba.class);
        assertThat(override.getDeclaringClass()).isNotEqualTo(LevelObject.class);
        assertThat(paraGoomba.getMode()).as("And the attribute really took effect").isEqualTo(RED);
    }

    @Test
    @DisplayName("Both Para-Goomba variants are placeable from the palette and name the same entity")
    void bothVariantsAreRegisteredForThePalette() {
        // Prepare & Execute
        final EnemyType normal = EnemyType.PARA_GOOMBA_NORMAL;
        final EnemyType red = EnemyType.PARA_GOOMBA_RED;

        // Verify
        assertThat(normal.getLevelObjectType()).isEqualTo(PARA_GOOMBA);
        assertThat(red.getLevelObjectType()).as("One entity, two palettes").isEqualTo(PARA_GOOMBA);
        assertThat(PARA_GOOMBA.getInstanceType()).isEqualTo(ParaGoomba.class);
        assertThat(PARA_GOOMBA.isMultiTiled()).as("No enemy is single-tiled").isTrue();
        assertThat(normal.getMode()).isEqualTo(NORMAL);
        assertThat(red.getMode()).isEqualTo(RED);
        assertThat(normal.getLabel()).isEqualTo("Para-Goomba - Normal");
        assertThat(red.getLabel()).isEqualTo("Para-Goomba - Red");
    }

    @Test
    @DisplayName("The mode a placement carries round-trips through the attribute the spawner writes")
    void theSpawnersAttributeRoundTrips() {
        // Prepare - the spawner writes EnemyType#getMode().name() and the entity reads it back
        final ParaGoomba paraGoomba = paraGoomba();
        final Map<String, Object> placementData = Map.of(
            MODE_ATTRIBUTE,
            EnemyType.PARA_GOOMBA_RED.getMode().name()
        );

        // Execute
        paraGoomba.configure(new LevelObjectData(placementData));

        // Verify
        assertThat(paraGoomba.getMode()).isEqualTo(RED);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private ParaGoomba paraGoomba() {
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, Offset.of(0, 0));
        paraGoomba.init();
        return paraGoomba;
    }
}
