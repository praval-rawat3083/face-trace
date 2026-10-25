package facetrace.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens a MySQL connection using settings kept in a local, ignored properties file. */
public final class DatabaseConnection {
    private static final Path SETTINGS_FILE = Path.of("config", "database.properties");

    /** Keeps this helper class from being created as an object. */
    private DatabaseConnection() { }

    /** Opens the database named for normal project use in the local settings file. */
    public static Connection getConnection() throws IOException, SQLException {
        return openConfiguredDatabase("db.name");
    }

    /** Opens only the separate test database named in the local settings file. */
    public static Connection getTestConnection() throws IOException, SQLException {
        return openConfiguredDatabase("db.testName");
    }

    /** Reads the local settings, checks the requested database name, and opens MySQL. */
    private static Connection openConfiguredDatabase(String databaseKey) throws IOException, SQLException {
        Properties settings = loadSettings();
        String host = required(settings, "db.host");
        String portText = required(settings, "db.port");
        String database = required(settings, databaseKey);
        String user = required(settings, "db.user");
        String password = settings.getProperty("db.password");
        if (password == null) {
            throw new IOException("Missing db.password in config/database.properties");
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException error) {
            throw new IOException("db.port must be a number", error);
        }
        if (port < 1 || port > 65535 || !database.matches("[A-Za-z0-9_]+")) {
            throw new IOException("Check db.port and database name in config/database.properties");
        }
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password);
    }

    /** Loads settings from the local file and explains how to create it if it is missing. */
    private static Properties loadSettings() throws IOException {
        if (!Files.isRegularFile(SETTINGS_FILE)) {
            throw new IOException("Copy config/config.example.properties to config/database.properties first");
        }
        Properties settings = new Properties();
        try (InputStream input = Files.newInputStream(SETTINGS_FILE)) {
            settings.load(input);
        }
        return settings;
    }

    /** Returns one required non-empty setting or names the missing entry. */
    private static String required(Properties settings, String key) throws IOException {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Set " + key + " in config/database.properties");
        }
        return value.trim();
    }
}
