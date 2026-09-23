package house.x1337.app.smb3.tool;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.model.repository.EnemyRecord;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.TileRecord;
import org.bson.Document;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.mapper.jackson.JacksonMapperModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dizitart.no2.filters.FluentFilter.where;

class MongoToNitriteExporterTest {

    private static final String DATABASE = "smb3db";

    @TempDir
    private Path workingDirectory;

    private MongoServer mongoServer;
    private String uri;

    @BeforeEach
    void startMongo() {
        mongoServer = new MongoServer(new MemoryBackend());
        final InetSocketAddress address = mongoServer.bind();
        uri = "mongodb://localhost:" + address.getPort() + "/";
    }

    @AfterEach
    void stopMongo() {
        mongoServer.shutdownNow();
    }

    @Test
    @DisplayName("Exported file is readable by Nitrite and carries every seeded record")
    void exportsEveryCollectionIntoAReadableNitriteFile() throws IOException {
        // Prepare
        seedMongo();
        final Path output = workingDirectory.resolve("smb3.db");

        // Execute
        MongoToNitriteExporter.main("--uri", uri, "--database", DATABASE, "--output", output.toString());

        // Verify
        assertThat(output).as("Nitrite output file").exists();
        final Nitrite nitrite = openNitrite(output);
        try {
            final TileRecord tile = nitrite.getRepository(TileRecord.class).find(where("id").eq(7)).firstOrNull();
            assertThat(tile).isNotNull();
            assertThat(tile.getSha256()).isEqualTo("abc123");
            assertThat(tile.getType()).isEqualTo(TileType.SOLID);
            assertThat(tile.getArgbData()).containsExactly(1, 2, 3, 4);
            assertThat(tile.getOriginalArgbData()).containsExactly(5, 6);

            final LevelSceneRecord scene = nitrite.getRepository(LevelSceneRecord.class)
                .find(where("id").eq("1-1")).firstOrNull();
            assertThat(scene).isNotNull();
            assertThat(scene.getTitle()).isEqualTo("World 1-1");
            assertThat(scene.getRows()).isEqualTo(27);
            assertThat(scene.getColumns()).isEqualTo(200);
            assertThat(scene.getSpawnPointRow()).isEqualTo(20);
            assertThat(scene.getLayers()).containsOnlyKeys(LevelSceneLayerType.STATIC_ENVIRONMENT.name());
            assertThat(scene.getLayers().get(LevelSceneLayerType.STATIC_ENVIRONMENT.name()).getTileIds())
                .containsExactly(0, 7, 7, 0);

            final LevelObjectRecord levelObject = nitrite.getRepository(LevelObjectRecord.class)
                .find(where("id").eq(42)).firstOrNull();
            assertThat(levelObject).isNotNull();
            assertThat(levelObject.getType()).isEqualTo("QUESTION_BLOCK");
            assertThat(levelObject.getData()).containsEntry("reward", "COIN");

            final EnemyRecord enemy = nitrite.getRepository(EnemyRecord.class)
                .find(where("id").eq("goomba-normal")).firstOrNull();
            assertThat(enemy).isNotNull();
            assertThat(enemy.getEnemyType()).isEqualTo(EnemyType.GOOMBA);
            assertThat(enemy.getTileIds()).containsExactly(11, 12);

            final org.dizitart.no2.collection.Document physics = nitrite.getCollection("configuration")
                .find(where("id").eq("physics")).firstOrNull();
            assertThat(physics).as("Physics configuration document keyed by 'id'").isNotNull();
            assertThat(((Number) physics.get("GRAVITY")).floatValue()).isEqualTo(0.5f);
        } finally {
            nitrite.close();
        }
    }

    @Test
    @DisplayName("Repeated exports rotate the previous files to .bak01 and .bak02")
    void rotatesExistingOutputFilesIncrementally() throws IOException {
        // Prepare
        seedMongo();
        final Path output = workingDirectory.resolve("smb3.db");

        // Execute
        MongoToNitriteExporter.main("--uri", uri, "--database", DATABASE, "--output", output.toString());
        MongoToNitriteExporter.main("--uri", uri, "--database", DATABASE, "--output", output.toString());
        MongoToNitriteExporter.main("--uri", uri, "--database", DATABASE, "--output", output.toString());

        // Verify
        assertThat(output).exists();
        assertThat(workingDirectory.resolve("smb3.db.bak01")).as("First rotation").exists();
        assertThat(workingDirectory.resolve("smb3.db.bak02")).as("Second rotation").exists();
        assertThat(workingDirectory.resolve("smb3.db.bak03")).as("No premature third rotation").doesNotExist();
        assertThat(Files.size(output)).isPositive();
    }

    @Test
    @DisplayName("Export refuses to overwrite an existing file when backups are disabled")
    void failsWhenBackupsAreDisabledAndTheOutputExists() throws IOException {
        // Prepare
        seedMongo();
        final Path output = workingDirectory.resolve("smb3.db");
        Files.writeString(output, "existing");

        // Execute & Verify
        assertThat(catchIoException(output)).isInstanceOf(IOException.class)
            .hasMessageContaining("--no-backup");
        assertThat(Files.readString(output)).isEqualTo("existing");
    }

    @Test
    @DisplayName("An unreachable MongoDB leaves the previous export untouched")
    void keepsThePreviousExportWhenMongoIsUnreachable() throws IOException {
        // Prepare
        final Path output = workingDirectory.resolve("smb3.db");
        Files.writeString(output, "previous export");
        mongoServer.shutdownNow();

        // Execute & Verify
        assertThat(catchIoException(output, false)).isInstanceOf(IOException.class)
            .hasMessageContaining("Unable to reach MongoDB");
        assertThat(Files.readString(output)).isEqualTo("previous export");
        assertThat(workingDirectory.resolve("smb3.db.bak01")).as("No rotation on failure").doesNotExist();
    }

    private Throwable catchIoException(final Path output) {
        return catchIoException(output, true);
    }

    private Throwable catchIoException(final Path output, final boolean noBackup) {
        try {
            if (noBackup) {
                MongoToNitriteExporter.main("--uri", uri, "--database", DATABASE, "--output", output.toString(),
                    "--no-backup");
            } else {
                MongoToNitriteExporter.main("--uri", uri, "--database", DATABASE, "--output", output.toString());
            }
            return null;
        } catch (final IOException exception) {
            return exception;
        }
    }

    private Nitrite openNitrite(final Path output) {
        return Nitrite.builder()
            .loadModule(MVStoreModule.withConfig().filePath(output.toString()).compress(false).build())
            .loadModule(new JacksonMapperModule())
            .openOrCreate("Mario", "1337");
    }

    private void seedMongo() {
        try (MongoClient client = MongoClients.create(MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(uri))
            .build())) {
            final MongoDatabase database = client.getDatabase(DATABASE);
            database.getCollection("tiles").insertOne(new Document(Map.of(
                "_id", 7,
                "sha256", "abc123",
                "type", TileType.SOLID.name(),
                "description", "Ground",
                "originalArgbData", List.of(5, 6),
                "argbData", List.of(1, 2, 3, 4)
            )));
            database.getCollection("levelScenes").insertOne(new Document(Map.of(
                "_id", "1-1",
                "title", "World 1-1",
                "description", "First level",
                "rows", 27,
                "columns", 200,
                "updatedAt", 1_700_000_000_000L,
                "spawnPointRow", 20,
                "spawnPointColumn", 3,
                "layers", new Document(LevelSceneLayerType.STATIC_ENVIRONMENT.name(), new Document(Map.of(
                    "type", LevelSceneLayerType.STATIC_ENVIRONMENT.name(),
                    "tileIds", List.of(0, 7, 7, 0)
                )))
            )));
            database.getCollection("levelObjects").insertOne(new Document(Map.of(
                "_id", 42,
                "type", "QUESTION_BLOCK",
                "description", "Coin block",
                "data", new Document("reward", "COIN")
            )));
            database.getCollection("enemies").insertOne(new Document(Map.of(
                "_id", "goomba-normal",
                "description", "Goomba",
                "enemyType", EnemyType.GOOMBA.name(),
                "rows", 1,
                "columns", 2,
                "tileIds", List.of(11, 12),
                "renderingStarterRow", 0,
                "renderingStarterColumn", 0,
                "updatedAt", 1_700_000_000_000L
            )));
            database.getCollection("configuration").insertOne(new Document(Map.of(
                "_id", "physics",
                "GRAVITY", 0.5f
            )));
        }
    }
}
