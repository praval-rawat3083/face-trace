package facetrace.app;

import facetrace.config.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.io.Console;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Resets one existing admin password; visible entry is used only when the user requests it. */
public final class ResetAdminPassword {
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    /** Keeps this one-time setup command from being created as an object. */
    private ResetAdminPassword() { }

    /** Reads a new password visibly, confirms it, and stores its BCrypt hash for an ADMIN account. */
    public static void main(String[] args) throws IOException, SQLException {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run this script from a private interactive PowerShell or Command Prompt window.");
        }

        String username = console.readLine("Admin username to reset: ");
        if (username == null || username.isBlank() || username.trim().length() > 64) {
            throw new IllegalArgumentException("Enter the existing admin username.");
        }
        console.printf("Password typing will be visible on this screen. Use this only in private.%n");
        String password = console.readLine("New password (at least 12 characters): ");
        String confirmation = console.readLine("Type the same password again: ");
        if (password == null || confirmation == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Choose a password of at least 12 characters and try again.");
        }
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("The two passwords did not match; no change was made.");
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        updateAdminPassword(username.trim(), hash);
        console.printf("Admin password reset. Only the BCrypt hash was stored.%n");
        password = null;
        confirmation = null;
        hash = null;
    }

    /** Changes the hash only when the named account already has the ADMIN role. */
    private static void updateAdminPassword(String username, String hash) throws IOException, SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            String select = "SELECT role FROM admins WHERE username = ?";
            try (PreparedStatement query = connection.prepareStatement(select)) {
                query.setString(1, username);
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) {
                        throw new IllegalArgumentException("No account has that username; no change was made.");
                    }
                    if (!"ADMIN".equals(row.getString("role"))) {
                        throw new SecurityException("That account is not an ADMIN; no change was made.");
                    }
                }
            }
            String update = "UPDATE admins SET password_hash = ? WHERE username = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, hash);
                statement.setString(2, username);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("The admin password was not updated.");
                }
            }
        }
    }
}
