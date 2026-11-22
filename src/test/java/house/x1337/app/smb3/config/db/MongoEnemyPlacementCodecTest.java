package house.x1337.app.smb3.config.db;

import house.x1337.app.smb3.config.db.mongo.CoordinatesCodec;
import house.x1337.app.smb3.config.db.mongo.IntArrayCodec;
import house.x1337.app.smb3.model.repository.EnemyPlacementRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord.LevelSceneLayerData;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encodes and decodes a level scene record whose NPC layer holds enemy placements, through the codec registry
 * {@link MongoDBConfig} builds, so a missing registration fails here rather than against a live database.
 */
class MongoEnemyPlacementCodecTest {
    @Test
    @DisplayName("The NPC layer's enemy placements survive the Mongo codec, each stamp id and pixel")
    void roundTripsThroughTheConfiguredCodec() {
        // Prepare
        final MongoDBConfig config = new MongoDBConfig();
        final Codec<LevelSceneRecord> codec = config
            .consolidatedCodecRegistry(config.customCodecRegistry(new IntArrayCodec(), new CoordinatesCodec()))
            .get(LevelSceneRecord.class);
        final LevelSceneRecord original = LevelSceneRecord.builder()
            .id("scene-1")
            .rows(1)
            .columns(2)
            .layer(NON_PLAYABLE_CHARACTERS.name(), LevelSceneLayerData.builder()
                .type(NON_PLAYABLE_CHARACTERS)
                .tileIds(new int[] {0, 0})
                .enemyPlacements(List.of(new EnemyPlacementRecord("goomba", 1695, 343)))
                .build())
            .build();
        final BsonDocument document = new BsonDocument();

        // Execute
        codec.encode(new BsonDocumentWriter(document), original, EncoderContext.builder().build());
        final LevelSceneRecord decoded = codec.decode(
            new BsonDocumentReader(document),
            DecoderContext.builder().build()
        );

        // Verify
        final BsonArray stored = document
            .getDocument("layers")
            .getDocument(NON_PLAYABLE_CHARACTERS.name())
            .getArray("enemyPlacements");
        assertThat(stored.get(0).asDocument().getString("stampId").getValue()).isEqualTo("goomba");
        assertThat(stored.get(0).asDocument().getInt32("x").getValue()).isEqualTo(1695);
        assertThat(stored.get(0).asDocument().getInt32("y").getValue()).isEqualTo(343);
        assertThat(decoded.getLayers().get(NON_PLAYABLE_CHARACTERS.name()).getEnemyPlacements())
            .containsExactly(new EnemyPlacementRecord("goomba", 1695, 343));
    }
}
