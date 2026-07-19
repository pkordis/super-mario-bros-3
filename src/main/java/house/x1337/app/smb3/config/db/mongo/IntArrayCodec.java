package house.x1337.app.smb3.config.db.mongo;

import house.x1337.app.smb3.annotation.Singleton;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

import java.util.ArrayList;
import java.util.List;

@Singleton
public final class IntArrayCodec implements Codec<int[]> {
    @Override
    public void encode(final BsonWriter writer, final int[] value, final EncoderContext encoderContext) {
        writer.writeStartArray();
        for (final int v : value) {
            writer.writeInt32(v);
        }
        writer.writeEndArray();
    }

    @Override
    public int[] decode(final BsonReader reader, final DecoderContext decoderContext) {
        final List<Integer> values = new ArrayList<>();
        reader.readStartArray();
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            values.add(reader.readInt32());
        }
        reader.readEndArray();
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public Class<int[]> getEncoderClass() {
        return int[].class;
    }
}

