package house.x1337.app.smb3.config.db;

import house.x1337.app.smb3.config.db.mongo.IntArrayCodec;
import house.x1337.app.smb3.model.repository.PipePortalRecord;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encodes and decodes a pipe portal record through the codec registry {@link MongoDBConfig} builds, so a missing
 * registration or an unmapped field fails here rather than against a live database.
 */
class MongoPipePortalCodecTest {
    @Test
    @DisplayName("A pipe portal record survives the Mongo codec with its id stored as _id")
    void roundTripsThroughTheConfiguredCodec() {
        // Prepare
        final MongoDBConfig config = new MongoDBConfig();
        final Codec<PipePortalRecord> codec = config
            .consolidatedCodecRegistry(config.customCodecRegistry(new IntArrayCodec()))
            .get(PipePortalRecord.class);
        final PipePortalRecord original = PipePortalRecord.builder()
            .id("portal-1")
            .description("Warp to the bonus room")
            .type(IN_AND_OUT)
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .levelSceneId("scene-1")
            .row(7)
            .column(12)
            .linksToId("portal-2")
            .build();
        final BsonDocument document = new BsonDocument();

        // Execute
        codec.encode(new BsonDocumentWriter(document), original, EncoderContext.builder().build());
        final PipePortalRecord decoded = codec.decode(
            new BsonDocumentReader(document),
            DecoderContext.builder().build()
        );

        // Verify
        assertThat(document.getString("_id").getValue()).isEqualTo("portal-1");
        assertThat(document.getString("levelSceneId").getValue())
            .as("The repository queries on this field name")
            .isEqualTo("scene-1");
        assertThat(document.getString("linksToId").getValue())
            .as("The repository clears links on this field name")
            .isEqualTo("portal-2");
        assertThat(document.getString("virtualObjectType").getValue()).isEqualTo("PIPE_PORTAL_VERTICAL");
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("A pipe portal stored before links existed decodes as free")
    void decodesAnOldDocumentAsFree() {
        // Prepare
        final MongoDBConfig config = new MongoDBConfig();
        final Codec<PipePortalRecord> codec = config
            .consolidatedCodecRegistry(config.customCodecRegistry(new IntArrayCodec()))
            .get(PipePortalRecord.class);
        final BsonDocument document = new BsonDocument()
            .append("_id", new BsonString("portal-1"))
            .append("levelSceneId", new BsonString("scene-1"));

        // Execute
        final PipePortalRecord decoded = codec.decode(
            new BsonDocumentReader(document),
            DecoderContext.builder().build()
        );

        // Verify
        assertThat(decoded.getId()).isEqualTo("portal-1");
        assertThat(decoded.getLinksToId()).isNull();
    }
}
