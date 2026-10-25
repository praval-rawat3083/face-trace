package facetrace.service;

import facetrace.config.DatabaseConnection;
import facetrace.model.AdminSession;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

/** Checks an admin password against the BCrypt hash stored in MySQL. */
public final class AdminAuthService {
    /** Returns a signed-in account only when the supplied password matches its saved hash. */
    public Optional<AdminSession> authenticate(String username, char[] password)
            throws IOException, SQLException {
        if (password == null) {
            return Optional.empty();
        }
        if (username == null || username.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            return Optional.empty();
        }

        String sql = "SELECT id, username, password_hash, role FROM admins WHERE username = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, username.trim());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                String storedHash = row.getString("password_hash");
                boolean passwordMatches;
                try {
                    // BCrypt needs a short-lived String; the typed password is never logged or saved as text.
                    passwordMatches = BCrypt.checkpw(new String(password), storedHash);
                } catch (IllegalArgumentException invalidHash) {
                    return Optional.empty();
                }
                if (!passwordMatches) {
                    return Optional.empty();
                }
                return Optional.of(new AdminSession(row.getLong("id"), row.getString("username"),
                        row.getString("role")));
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
