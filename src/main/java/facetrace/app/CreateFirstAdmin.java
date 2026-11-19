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

/** Creates the first admin without putting hardcoded passwords in code. */
public final class CreateFirstAdmin {
    private static final int MINIMUM_PASSWORD_LENGTH = 6;

    private CreateFirstAdmin() { }

    public static void main(String[] args) throws IOException, SQLException {
        String username;
        String passwordStr;

        if (args != null && args.length >= 2) {
            username = args[0].trim();
            passwordStr = args[1];
        } else {
            Console console = System.console();
            if (console == null) {
                System.out.println("Usage: java CreateFirstAdmin <username> <password>");
                System.out.println("Or run interactively in Command Prompt / PowerShell.");
                return;
            }

            username = console.readLine("Admin username: ");
            if (username == null || username.isBlank() || username.trim().length() > 64) {
                throw new IllegalArgumentException("Enter a username between 1 and 64 characters.");
            }
            char[] password = console.readPassword("Admin password (at least 6 characters): ");
            char[] confirmation = console.readPassword("Type the password again: ");
            if (password == null || confirmation == null) {
                clear(password);
                clear(confirmation);
                throw new IllegalStateException("Password entry was cancelled.");
            }

            try {
                if (password.length < MINIMUM_PASSWORD_LENGTH) {
                    throw new IllegalArgumentException("Choose a password with at least " + MINIMUM_PASSWORD_LENGTH + " characters.");
                }
                if (!Arrays.equals(password, confirmation)) {
                    throw new IllegalArgumentException("The two passwords did not match.");
                }
                passwordStr = new String(password);
            } finally {
                clear(password);
                clear(confirmation);
            }
        }

        if (username.isBlank() || passwordStr.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Username required and password must be at least " + MINIMUM_PASSWORD_LENGTH + " characters.");
        }

        String hash = BCrypt.hashpw(passwordStr, BCrypt.gensalt(12));
        insertFirstAdmin(username.trim(), hash);
        System.out.println("Admin account '" + username.trim() + "' created successfully!");
    }

    private static void insertFirstAdmin(String username, String hash) throws IOException, SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM admins";
            try (PreparedStatement check = connection.prepareStatement(checkSql);
                 ResultSet count = check.executeQuery()) {
                if (count.next() && count.getInt(1) > 0) {
                    System.out.println("Notice: An administrator already exists. Adding/updating account...");
                }
            }

            String insertSql = "REPLACE INTO admins (username, password_hash, role) VALUES (?, ?, 'ADMIN')";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, username);
                insert.setString(2, hash);
                insert.executeUpdate();
            }
        }
    }

    private static void clear(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }
}
