package house.x1337.app.smb3.config.db;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import house.x1337.app.smb3.config.db.mongo.IntArrayCodec;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.TileRecord;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.MongoCredential.createCredential;
import static java.lang.String.format;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
@ConditionalOnProperty(name = "db.provider", havingValue = "MONGO_DB")
public class MongoDBConfig {

    @Value("${spring.data.mongodb.host}")
    private String host;

    @Value("${spring.data.mongodb.port}")
    private String port;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${spring.data.mongodb.username}")
    private String username;

    @Value("${spring.data.mongodb.password}")
    private String password;

    @Value("${spring.data.mongodb.collection.tiles:tiles}")
    private String tilesCollection;

    @Value("${spring.data.mongodb.collection.level-scenes:levelScenes}")
    private String levelScenesCollection;

    @Value("${spring.data.mongodb.collection.level-objects:levelObjects}")
    private String levelObjectsCollection;

    @Value("${spring.data.mongodb.collection.configuration:configuration}")
    private String configurationCollection;

    @Bean
    ConnectionString connectionString() {
        return new ConnectionString(format("mongodb://%s:%s/", host, port));
    }

    @Bean
    MongoCredential mongoCredential() {
        return createCredential(
            username,
            database,
            password.toCharArray()
        );
    }

    @Bean
    CodecRegistry customCodecRegistry(
        final IntArrayCodec intArrayCodec
    ) {
        return fromCodecs(
            intArrayCodec
        );
    }

    @Bean
    CodecRegistry consolidatedCodecRegistry(
        final CodecRegistry customCodecRegistry
    ) {
        return fromRegistries(
            getDefaultCodecRegistry(),
            customCodecRegistry,
            fromProviders(
                PojoCodecProvider.builder()
                    .register(TileRecord.class)
                    .register(LevelSceneRecord.class)
                    .register(LevelSceneRecord.LevelSceneLayerData.class)
                    .register(LevelObjectRecord.class)
                    .build()
            )
        );
    }

    @Bean
    MongoClientSettings mongoClientSettings(
        final MongoCredential mongoCredential,
        final ConnectionString connectionString,
        final CodecRegistry consolidatedCodecRegistry
    ) {
        return MongoClientSettings.builder()
            .credential(mongoCredential)
            .applyConnectionString(connectionString)
            .codecRegistry(consolidatedCodecRegistry)
            .build();
    }

    @Bean
    MongoClient mongoClient(final MongoClientSettings mongoClientSettings) {
        return MongoClients.create(mongoClientSettings);
    }

    @Bean
    MongoDatabase mongoDatabase(final MongoClient mongoClient) {
        return mongoClient.getDatabase(database);
    }

    @Bean
    MongoCollection<TileRecord> tileMongoCollection(final MongoDatabase mongoDatabase) {
        return mongoDatabase.getCollection(tilesCollection, TileRecord.class);
    }

    @Bean
    MongoCollection<LevelSceneRecord> levelSceneMongoCollection(final MongoDatabase mongoDatabase) {
        return mongoDatabase.getCollection(levelScenesCollection, LevelSceneRecord.class);
    }

    @Bean
    MongoCollection<LevelObjectRecord> levelObjectMongoCollection(final MongoDatabase mongoDatabase) {
        return mongoDatabase.getCollection(levelObjectsCollection, LevelObjectRecord.class);
    }

    @Bean
    MongoCollection<Document> configurationMongoCollection(final MongoDatabase mongoDatabase) {
        return mongoDatabase.getCollection(configurationCollection);
    }
}
