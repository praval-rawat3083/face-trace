package facetrace.app;

import facetrace.config.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.io.Console;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

/** Creates the first admin without putting a password in a source file or command line. */
public final class CreateFirstAdmin {
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    /** Keeps this one-time setup command from being created as an object. */
    private CreateFirstAdmin() { }

    /** Prompts in the terminal, stores only a BCrypt hash, and stops if an admin already exists. */
    public static void main(String[] args) throws IOException, SQLException {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run this script from an interactive PowerShell or Command Prompt window.");
        }

        String username = console.readLine("First admin username: ");
        if (username == null || username.isBlank() || username.trim().length() > 64) {
            throw new IllegalArgumentException("Enter a username between 1 and 64 characters.");
        }
        char[] password = console.readPassword("First admin password (at least 12 characters): ");
        char[] confirmation = console.readPassword("Type the password again: ");
        if (password == null || confirmation == null) {
            clear(password);
            clear(confirmation);
            throw new IllegalStateException("Password entry was cancelled.");
        }

        try {
            if (password.length < MINIMUM_PASSWORD_LENGTH) {
                throw new IllegalArgumentException("Choose a password with at least 12 characters.");
            }
            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("The two passwords did not match.");
            }

            // BCrypt accepts text, so this brief in-memory value is never printed or written to a file.
            String hash = BCrypt.hashpw(new String(password), BCrypt.gensalt(12));
            insertFirstAdmin(username.trim(), hash);
            console.printf("First admin account created. Only the BCrypt hash was stored.%n");
        } finally {
            clear(password);
            clear(confirmation);
        }
    }

    /** Inserts one ADMIN only when the admins table has no accounts. */
    private static void insertFirstAdmin(String username, String hash) throws IOException, SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM admins");
                 ResultSet row = count.executeQuery()) {
                row.next();
                if (row.getLong(1) != 0) {
                    throw new IllegalStateException("An admin already exists; first-admin setup can only run once.");
                }
            }
            String sql = "INSERT INTO admins (username, password_hash, role) VALUES (?, ?, 'ADMIN')";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setString(1, username);
                insert.setString(2, hash);
                insert.executeUpdate();
            }
        }
    }

    /** Clears password characters as soon as they are no longer needed. */
    private static void clear(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }
}
