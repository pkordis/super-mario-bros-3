package house.x1337.app.smb3.model.repository;

import house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.EnemyLevelObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LevelObjectRecordTypeResolutionTest {
    @Test
    @DisplayName("No enemy type is ever single-tiled, whatever its sprite size")
    void enemyTypesAreNeverSingleTiled() {
        for (final LevelObjectTypeSingleTiled type : LevelObjectTypeSingleTiled.values()) {
            assertThat(EnemyLevelObject.class.isAssignableFrom(type.getInstanceType()))
                .as("%s is single-tiled, so it would be built as a terrain cell", type)
                .isFalse();
        }
    }

    @Test
    @DisplayName("The Goomba is a multi-tiled type even though its art is one tile")
    void goombaIsMultiTiled() {
        // Prepare & Execute
        final Optional<LevelObjectType> resolved = recordOfType(LevelObjectTypeMultiTiled.GOOMBA.name())
            .findLevelObjectType();

        // Verify
        assertThat(resolved).contains(LevelObjectTypeMultiTiled.GOOMBA);
        assertThat(resolved.orElseThrow().isMultiTiled())
            .as("Multi-tiled keeps it out of the collision grid's tile-bound path")
            .isTrue();
        assertThat(EnemyLevelObject.class.isAssignableFrom(resolved.orElseThrow().getInstanceType()))
            .as("...and it is what the enemy spawner looks for")
            .isTrue();
    }

    @Test
    @DisplayName("A single-tiled type still resolves, and still reports itself as a terrain cell")
    void singleTiledTypeResolves() {
        // Prepare & Execute
        final Optional<LevelObjectType> resolved = recordOfType(LevelObjectTypeSingleTiled.QUESTION_BLOCK.name())
            .findLevelObjectType();

        // Verify
        assertThat(resolved).contains(LevelObjectTypeSingleTiled.QUESTION_BLOCK);
        assertThat(resolved.orElseThrow().isSingleTiled()).isTrue();
    }

    @Test
    @DisplayName("An unclassified tile and an unknown type name both resolve to nothing")
    void unresolvableRecordsAreEmpty() {
        assertThat(recordOfType(null).findLevelObjectType())
            .as("A tile nobody has classified yet")
            .isEmpty();
        assertThat(recordOfType("A_TYPE_THAT_NO_LONGER_EXISTS").findLevelObjectType())
            .as("A type string dropped from both enums")
            .isEmpty();
    }

    private LevelObjectRecord recordOfType(final String typeName) {
        return LevelObjectRecord
            .builder()
            .id(1)
            .type(typeName)
            .build();
    }
}
