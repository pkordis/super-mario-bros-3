package house.x1337.app.smb3.config.db.mongo;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.Coordinates;
import lombok.Getter;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

@Getter
@Singleton
public final class CoordinatesCodec implements Codec<Coordinates> {
    private final Class<Coordinates> encoderClass = Coordinates.class;

    @Override
    public void encode(
        final BsonWriter writer,
        final Coordinates value,
        final EncoderContext encoderContext
    ) {
        writer.writeStartDocument();
        writer.writeInt32("x", value.x());
        writer.writeInt32("y", value.y());
        writer.writeEndDocument();
    }

    @Override
    public Coordinates decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
        reader.readStartDocument();
        final int x = reader.readInt32("x");
        final int y = reader.readInt32("y");
        reader.readEndDocument();
        return Coordinates.of(x, y);
    }
}

