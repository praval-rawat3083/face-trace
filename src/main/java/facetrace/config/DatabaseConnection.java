package facetrace.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/** Manages database connections with full MySQL and SQLite support. */
public final class DatabaseConnection {
    private static final Path SETTINGS_FILE = Path.of("config", "database.properties");
    private static final String SQLITE_DB = "attendance.db";
    private static final String SQLITE_TEST_DB = "attendance_test.db";
    private static volatile boolean initialized = false;

    private DatabaseConnection() { }

    /** Opens the main application database (MySQL primary, with graceful fallback). */
    public static Connection getConnection() throws IOException, SQLException {
        Connection conn = openConnection(SQLITE_DB, "db.name");
        ensureSchema(conn);
        return conn;
    }

    /** Opens the test database. */
    public static Connection getTestConnection() throws IOException, SQLException {
        Connection conn = openConnection(SQLITE_TEST_DB, "db.testName");
        ensureSchema(conn);
        return conn;
    }

    private static Connection openConnection(String defaultSqliteFile, String mysqlKey) throws IOException, SQLException {
        Properties settings = loadSettings();
        String dbType = settings.getProperty("db.type", "sqlite").trim().toLowerCase();

        if ("mysql".equals(dbType)) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                // Driver not found, fall back
            }
            String host = required(settings, "db.host");
            String portText = required(settings, "db.port");
            String database = required(settings, mysqlKey);
            String user = required(settings, "db.user");
            String password = settings.getProperty("db.password", "");
            String url = "jdbc:mysql://" + host + ":" + portText + "/" + database
                    + "?serverTimezone=UTC&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false";
            try {
                return DriverManager.getConnection(url, user, password);
            } catch (SQLException ex) {
                System.err.println("[FaceTrace] MySQL connection to " + url + " failed: " + ex.getMessage());
                System.err.println("[FaceTrace] Falling back to local embedded database so application runs smoothly.");
                return openSqliteConnection(defaultSqliteFile);
            }
        } else {
            return openSqliteConnection(defaultSqliteFile);
        }
    }

    private static Connection openSqliteConnection(String file) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on classpath", e);
        }
        String url = "jdbc:sqlite:" + file;
        Connection conn = DriverManager.getConnection(url);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /** Automatically creates tables if not present on first run. */
    private static void ensureSchema(Connection conn) {
        if (initialized) return;
        synchronized (DatabaseConnection.class) {
            if (initialized) return;
            try {
                boolean isMysql = conn.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
                try (Statement stmt = conn.createStatement()) {
                    if (isMysql) {
                        stmt.execute("CREATE TABLE IF NOT EXISTS students (" +
                                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                                "student_id VARCHAR(50) NOT NULL UNIQUE, " +
                                "full_name VARCHAR(100) NOT NULL, " +
                                "age INT NOT NULL, " +
                                "department VARCHAR(120) NOT NULL, " +
                                "photo_path VARCHAR(500) NOT NULL, " +
                                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

                        stmt.execute("CREATE TABLE IF NOT EXISTS face_embeddings (" +
                                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                                "record_id BIGINT NOT NULL, " +
                                "vector MEDIUMBLOB NOT NULL, " +
                                "embedder_name VARCHAR(100) NOT NULL, " +
                                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                                "CONSTRAINT fk_face_embeddings_student " +
                                "FOREIGN KEY (record_id) REFERENCES students(id) ON DELETE CASCADE" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

                        stmt.execute("CREATE TABLE IF NOT EXISTS attendance_logs (" +
                                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                                "probe_file VARCHAR(500) NOT NULL, " +
                                "matched_student_id BIGINT NULL, " +
                                "subject VARCHAR(100) NOT NULL DEFAULT 'General Attendance', " +
                                "score DOUBLE NOT NULL, " +
                                "status VARCHAR(50) NOT NULL DEFAULT 'PRESENT', " +
                                "check_in_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                                "INDEX idx_attendance_student_time (matched_student_id, check_in_time), " +
                                "CONSTRAINT fk_attendance_student " +
                                "FOREIGN KEY (matched_student_id) REFERENCES students(id) ON DELETE SET NULL" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

                        stmt.execute("CREATE TABLE IF NOT EXISTS admins (" +
                                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                                "username VARCHAR(64) NOT NULL UNIQUE, " +
                                "password_hash CHAR(60) NOT NULL, " +
                                "role VARCHAR(20) NOT NULL DEFAULT 'ADMIN', " +
                                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

                        stmt.execute("CREATE TABLE IF NOT EXISTS audit_log (" +
                                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                                "admin_id BIGINT NULL, " +
                                "action VARCHAR(50) NOT NULL, " +
                                "entity_type VARCHAR(50) NOT NULL, " +
                                "entity_id BIGINT NULL, " +
                                "details TEXT NULL, " +
                                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                                "INDEX idx_audit_log_created_at (created_at), " +
                                "CONSTRAINT fk_audit_log_admin " +
                                "FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

                        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM admins")) {
                            if (rs.next() && rs.getInt(1) == 0) {
                                stmt.execute("INSERT INTO admins (username, password_hash, role) VALUES " +
                                        "('admin', '$2a$12$pisNh8.XNrqPW9WymlJlMO788kCkQSDNkhLrV1tyKVH/1zsb/LAqa', 'ADMIN')");
                            }
                        }
                    } else {
                        stmt.execute("CREATE TABLE IF NOT EXISTS students (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "student_id TEXT UNIQUE NOT NULL, " +
                                "full_name TEXT NOT NULL, " +
                                "age INTEGER NOT NULL, " +
                                "department TEXT NOT NULL, " +
                                "photo_path TEXT NOT NULL, " +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

                        stmt.execute("CREATE TABLE IF NOT EXISTS face_embeddings (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "record_id INTEGER NOT NULL, " +
                                "vector BLOB NOT NULL, " +
                                "embedder_name TEXT NOT NULL, " +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                                "FOREIGN KEY (record_id) REFERENCES students(id) ON DELETE CASCADE);");

                        stmt.execute("CREATE TABLE IF NOT EXISTS attendance_logs (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "probe_file TEXT NOT NULL, " +
                                "matched_student_id INTEGER, " +
                                "subject TEXT DEFAULT 'General Attendance', " +
                                "score REAL NOT NULL, " +
                                "status TEXT NOT NULL DEFAULT 'PRESENT', " +
                                "check_in_time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                                "FOREIGN KEY (matched_student_id) REFERENCES students(id) ON DELETE SET NULL);");

                        stmt.execute("CREATE TABLE IF NOT EXISTS admins (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "username TEXT UNIQUE NOT NULL, " +
                                "password_hash TEXT NOT NULL, " +
                                "role TEXT NOT NULL DEFAULT 'ADMIN', " +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);");

                        stmt.execute("CREATE TABLE IF NOT EXISTS audit_log (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "admin_id INTEGER, " +
                                "action TEXT NOT NULL, " +
                                "entity_type TEXT NOT NULL, " +
                                "entity_id INTEGER, " +
                                "details TEXT, " +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                                "FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL);");
                    }
                    initialized = true;
                }
            } catch (SQLException ex) {
                // Ignore if already created
            }
        }
    }

    private static Properties loadSettings() {
        Properties settings = new Properties();
        if (Files.isRegularFile(SETTINGS_FILE)) {
            try (InputStream input = Files.newInputStream(SETTINGS_FILE)) {
                settings.load(input);
            } catch (IOException ignored) {
            }
        }
        return settings;
    }

    private static String required(Properties settings, String key) throws IOException {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required setting: " + key);
        }
        return value.trim();
    }
}
