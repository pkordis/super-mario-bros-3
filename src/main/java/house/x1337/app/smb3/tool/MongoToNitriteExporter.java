package house.x1337.app.smb3.tool;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import house.x1337.app.smb3.config.db.mongo.IntArrayCodec;
import house.x1337.app.smb3.model.repository.EnemyRecord;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.TileRecord;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.mapper.jackson.JacksonMapperModule;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.repository.ObjectRepository;
import org.dizitart.no2.store.StoreModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.MongoCredential.createCredential;
import static java.lang.String.format;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Exports a MongoDB database (by default {@code smb3db}) into a Nitrite MVStore file
 * (by default {@code ./smb3.db}) that the game can open with {@code db.provider=NITRITE}.
 *
 * <p>Collections known to the application ({@code tiles}, {@code levelScenes},
 * {@code levelObjects}, {@code enemies}) are copied through the very same record classes and
 * {@code ObjectRepository} API the game uses, so the resulting file is byte-for-byte compatible
 * with {@code NitriteConfig}. Every other collection (for example {@code configuration}) is copied
 * document-by-document into a Nitrite collection of the same name, with the Mongo {@code _id}
 * field renamed to {@code id} — the key the Nitrite repositories query on.
 *
 * <p>An already existing output file is never overwritten: it is renamed in place to
 * {@code <name>.bak01}, {@code <name>.bak02}, … using the first free index.
 *
 * <p>Run it through {@code dev-toolbox/misc/export_mongo_to_nitrite.py}; see {@code --help}
 * for the full option list.
 */
public final class MongoToNitriteExporter {

    private static final String DEFAULT_MONGO_HOST = "localhost";
    private static final String DEFAULT_MONGO_PORT = "27017";
    private static final String DEFAULT_MONGO_DATABASE = "smb3db";
    private static final String DEFAULT_OUTPUT_FILE = "./smb3.db";
    private static final String NITRITE_USERNAME = "Mario";
    private static final String NITRITE_PASSWORD = "1337";
    private static final String MONGO_ID_FIELD = "_id";
    private static final String NITRITE_ID_FIELD = "id";
    private static final String BACKUP_SUFFIX_FORMAT = ".bak%02d";
    private static final int MAX_BACKUPS = 9999;
    private static final long SERVER_SELECTION_TIMEOUT_SECONDS = 10;

    private MongoToNitriteExporter() {
    }

    public static void main(final String... args) throws IOException {
        final Map<String, String> options = parseOptions(args);
        if (options.containsKey("help")) {
            printUsage();
            return;
        }

        final Properties properties = loadApplicationProperties();

        final String host = resolve(options, "host", properties, "spring.data.mongodb.host",
            "SPRING_DATA_MONGODB_HOST", DEFAULT_MONGO_HOST);
        final String port = resolve(options, "port", properties, "spring.data.mongodb.port",
            "SPRING_DATA_MONGODB_PORT", DEFAULT_MONGO_PORT);
        final String database = resolve(options, "database", properties, "spring.data.mongodb.database",
            "SPRING_DATA_MONGODB_DATABASE", DEFAULT_MONGO_DATABASE);
        final String username = resolve(options, "username", properties, "spring.data.mongodb.username",
            "SPRING_DATA_MONGODB_USERNAME", "");
        final String password = resolve(options, "password", properties, "spring.data.mongodb.password",
            "SPRING_DATA_MONGODB_PASSWORD", "");
        final String authDatabase = resolve(options, "auth-database", properties,
            "spring.data.mongodb.authentication-database", "SPRING_DATA_MONGODB_AUTHENTICATION_DATABASE", database);
        final String uri = resolve(options, "uri", properties, "spring.data.mongodb.uri",
            "SPRING_DATA_MONGODB_URI", format("mongodb://%s:%s/", host, port));
        final String outputPath = resolve(options, "output", properties, "db.nitrite.file-path",
            "DB_NITRITE_FILE_PATH", DEFAULT_OUTPUT_FILE);
        final boolean compress = Boolean.parseBoolean(resolve(options, "compress", properties, "db.nitrite.compress",
            "DB_NITRITE_COMPRESS", "false"));
        final boolean backup = !options.containsKey("no-backup");

        final Path output = Path.of(outputPath).toAbsolutePath().normalize();

        System.out.printf("Source     : %s (database '%s')%n", uri, database);
        System.out.printf("Destination: %s%n", output);

        final MongoClientSettings clientSettings = mongoClientSettings(uri, username, password, authDatabase);
        long total = 0;
        try (MongoClient mongoClient = MongoClients.create(clientSettings)) {
            final MongoDatabase mongoDatabase = mongoClient.getDatabase(database);
            ping(mongoDatabase, uri);

            // Rotated only once the source is known to be reachable, so a failed export leaves the previous
            // file exactly where it was.
            prepareOutput(output, backup);

            final Nitrite nitrite = openNitrite(output, compress);
            try {
                final Map<String, Class<?>> typedCollections = typedCollections(properties);
                for (final Map.Entry<String, Class<?>> entry : typedCollections.entrySet()) {
                    total += report(entry.getKey(), copyRepository(mongoDatabase, nitrite, entry.getKey(),
                        entry.getValue()));
                }

                final Set<String> exported = new LinkedHashSet<>(typedCollections.keySet());
                for (final String name : mongoDatabase.listCollectionNames()) {
                    if (!exported.add(name) || name.startsWith("system.")) {
                        continue;
                    }
                    total += report(name, copyDocuments(mongoDatabase, nitrite, name));
                }

                nitrite.commit();
            } finally {
                nitrite.close();
            }
        }

        System.out.printf("Done. %d document(s) exported, %d bytes written.%n", total, Files.size(output));
    }

    private static Map<String, Class<?>> typedCollections(final Properties properties) {
        final Map<String, Class<?>> collections = new LinkedHashMap<>();
        collections.put(properties.getProperty("spring.data.mongodb.collection.tiles", "tiles"), TileRecord.class);
        collections.put(properties.getProperty("spring.data.mongodb.collection.level-scenes", "levelScenes"),
            LevelSceneRecord.class);
        collections.put(properties.getProperty("spring.data.mongodb.collection.level-objects", "levelObjects"),
            LevelObjectRecord.class);
        collections.put(properties.getProperty("spring.data.mongodb.collection.enemies", "enemies"),
            EnemyRecord.class);
        return collections;
    }

    private static MongoClientSettings mongoClientSettings(final String uri, final String username,
            final String password, final String authDatabase) {
        final CodecRegistry codecRegistry = fromRegistries(
            getDefaultCodecRegistry(),
            fromCodecs(new IntArrayCodec()),
            fromProviders(
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register(TileRecord.class)
                    .register(LevelSceneRecord.class)
                    .register(LevelSceneRecord.LevelSceneLayerData.class)
                    .register(LevelObjectRecord.class)
                    .register(EnemyRecord.class)
                    .build()
            )
        );

        final MongoClientSettings.Builder builder = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(uri))
            .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_SECONDS,
                SECONDS))
            .codecRegistry(codecRegistry);
        if (!username.isBlank()) {
            builder.credential(createCredential(username, authDatabase, password.toCharArray()));
        }
        return builder.build();
    }

    private static void ping(final MongoDatabase mongoDatabase, final String uri) throws IOException {
        try {
            mongoDatabase.runCommand(new Document("ping", 1));
        } catch (final MongoException exception) {
            throw new IOException("Unable to reach MongoDB at " + uri + ": " + exception.getMessage(), exception);
        }
    }

    private static Nitrite openNitrite(final Path output, final boolean compress) {
        final StoreModule storeModule = MVStoreModule.withConfig()
            .filePath(output.toString())
            .compress(compress)
            .build();
        return Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new JacksonMapperModule())
            .openOrCreate(NITRITE_USERNAME, NITRITE_PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private static <T> long copyRepository(final MongoDatabase mongoDatabase, final Nitrite nitrite,
            final String collectionName, final Class<T> type) {
        final MongoCollection<T> source = mongoDatabase.getCollection(collectionName, type);
        final ObjectRepository<T> destination = nitrite.getRepository(type);
        long count = 0;
        for (final T record : source.find()) {
            destination.insert(record);
            count++;
        }
        return count;
    }

    private static long copyDocuments(final MongoDatabase mongoDatabase, final Nitrite nitrite,
            final String collectionName) {
        final MongoCollection<Document> source = mongoDatabase.getCollection(collectionName);
        final NitriteCollection destination = nitrite.getCollection(collectionName);
        long count = 0;
        for (final Document document : source.find()) {
            destination.insert(toNitriteDocument(document, true));
            count++;
        }
        return count;
    }

    private static org.dizitart.no2.collection.Document toNitriteDocument(final Document source,
            final boolean renameId) {
        final org.dizitart.no2.collection.Document target = org.dizitart.no2.collection.Document.createDocument();
        for (final Map.Entry<String, Object> entry : source.entrySet()) {
            final String key = renameId && MONGO_ID_FIELD.equals(entry.getKey()) ? NITRITE_ID_FIELD : entry.getKey();
            target.put(key, toNitriteValue(entry.getValue()));
        }
        return target;
    }

    private static Object toNitriteValue(final Object value) {
        return switch (value) {
            case null -> null;
            case Document document -> toNitriteDocument(document, false);
            case ObjectId objectId -> objectId.toHexString();
            case Binary binary -> binary.getData();
            case List<?> list -> {
                final List<Object> converted = new ArrayList<>(list.size());
                for (final Object element : list) {
                    converted.add(toNitriteValue(element));
                }
                yield converted;
            }
            case Map<?, ?> map -> {
                final Map<String, Object> converted = new LinkedHashMap<>();
                for (final Map.Entry<?, ?> entry : map.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), toNitriteValue(entry.getValue()));
                }
                yield converted;
            }
            default -> value;
        };
    }

    private static void prepareOutput(final Path output, final boolean backup) throws IOException {
        final Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(output)) {
            return;
        }
        if (!backup) {
            throw new IOException("Output file already exists and --no-backup was given: " + output);
        }
        final Path renamed = rotate(output);
        System.out.printf("Renamed existing %s -> %s%n", output.getFileName(), renamed.getFileName());
    }

    private static Path rotate(final Path output) throws IOException {
        final Path parent = output.getParent();
        for (int index = 1; index <= MAX_BACKUPS; index++) {
            final Path candidate = parent.resolve(output.getFileName() + format(BACKUP_SUFFIX_FORMAT, index));
            if (!Files.exists(candidate)) {
                Files.move(output, candidate, ATOMIC_MOVE);
                return candidate;
            }
        }
        throw new IOException("Exhausted backup slots for " + output + " (tried up to " + MAX_BACKUPS + ")");
    }

    private static long report(final String collectionName, final long count) {
        System.out.printf("  %-20s %6d document(s)%n", collectionName, count);
        return count;
    }

    private static String resolve(final Map<String, String> options, final String optionKey,
            final Properties properties, final String propertyKey, final String environmentKey,
            final String defaultValue) {
        final String option = options.get(optionKey);
        if (option != null && !option.isBlank()) {
            return option;
        }
        final String environment = System.getenv(environmentKey);
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        final String property = properties.getProperty(propertyKey);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return defaultValue;
    }

    private static Properties loadApplicationProperties() {
        final Properties properties = new Properties();
        try (InputStream stream = MongoToNitriteExporter.class.getResourceAsStream("/application.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException("Unable to read application.properties", exception);
        }
        return properties;
    }

    private static Map<String, String> parseOptions(final String... args) {
        final Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            final String argument = args[index];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + argument);
            }
            final String stripped = argument.substring(2);
            final int separator = stripped.indexOf('=');
            if (separator >= 0) {
                options.put(stripped.substring(0, separator), stripped.substring(separator + 1));
            } else if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
                options.put(stripped, args[++index]);
            } else {
                options.put(stripped, "");
            }
        }
        return options;
    }

    private static void printUsage() {
        System.out.println("""
            Exports a MongoDB database into a Nitrite (MVStore) file readable by the game.

            Usage:
              java -cp <classpath> house.x1337.app.smb3.tool.MongoToNitriteExporter [options]

            Options:
              --uri <uri>            Full Mongo connection string (overrides --host/--port)
              --host <host>          Mongo host              (default: localhost)
              --port <port>          Mongo port              (default: 27017)
              --database <name>      Mongo database          (default: smb3db)
              --username <user>      Mongo user              (default: none -> no authentication)
              --password <pass>      Mongo password
              --auth-database <name> Authentication database (default: --database)
              --output <path>        Nitrite output file     (default: ./smb3.db)
              --compress <bool>      MVStore compression     (default: false)
              --no-backup            Fail instead of rotating an existing output file
              --help                 Print this help

            Resolution order per setting: command line > environment (SPRING_DATA_MONGODB_*) >
            src/main/resources/application.properties > built-in default.

            An existing output file is renamed in place to <name>.bak01, <name>.bak02, ...
            using the first free index; nothing is ever overwritten.
            """);
    }
}
