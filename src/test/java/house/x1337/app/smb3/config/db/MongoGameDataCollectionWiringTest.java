package house.x1337.app.smb3.config.db;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import house.x1337.app.smb3.config.db.mongo.IntArrayCodec;
import house.x1337.app.smb3.repository.mongo.MongoGameSettingsRepository;
import house.x1337.app.smb3.repository.mongo.MongoPhysicsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MongoDBConfig} now offers two raw-document collections. Each repository must get its own: the settings the
 * {@code gameData} collection, the physics the {@code configuration} one. Building the clients and collections does
 * not connect, so no database is needed.
 */
class MongoGameDataCollectionWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withPropertyValues(
            "db.provider=MONGO_DB",
            "spring.data.mongodb.host=localhost",
            "spring.data.mongodb.port=27017",
            "spring.data.mongodb.database=wiring",
            "spring.data.mongodb.username=user",
            "spring.data.mongodb.password=secret"
        )
        .withUserConfiguration(MongoDBConfig.class)
        .withBean(IntArrayCodec.class)
        .withBean(MongoGameSettingsRepository.class)
        .withBean(MongoPhysicsRepository.class);

    @Test
    @DisplayName("The settings repository gets the gameData collection and the physics one the configuration one")
    void eachRepositoryGetsItsOwnCollection() {
        // Execute & Verify
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(collectionName(context.getBean(MongoGameSettingsRepository.class), "gameDataMongoCollection"))
                .isEqualTo("gameData");
            assertThat(collectionName(context.getBean(MongoPhysicsRepository.class), "configurationMongoCollection"))
                .isEqualTo("configuration");
            context.getBean(MongoClient.class).close();
        });
    }

    private String collectionName(final Object repository, final String field) {
        final MongoCollection<?> collection = (MongoCollection<?>) ReflectionTestUtils.getField(repository, field);
        assertThat(collection).isNotNull();
        return collection.getNamespace().getCollectionName();
    }
}
