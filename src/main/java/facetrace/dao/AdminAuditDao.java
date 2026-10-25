package facetrace.dao;

import facetrace.model.AdminSession;
import facetrace.service.RoleChecker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/** Writes successful admin actions to the existing audit_log table. */
public final class AdminAuditDao {
    private final Connection connection;

    /** Uses the caller's connection so an audit row can share the record transaction. */
    public AdminAuditDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "A database connection is required");
    }

    /** Records that an admin opened or refreshed the record list. */
    public void logRecordListView(AdminSession admin) throws SQLException {
        log(admin, "RECORDS_VIEW", "CRIMINAL_RECORD", null, "Viewed synthetic record list");
    }

    /** Saves one action without storing passwords, image bytes, or full image paths. */
    public void log(AdminSession admin, String action, String entityType, Long entityId, String details)
            throws SQLException {
        new RoleChecker().requireAdmin(admin);
        String sql = "INSERT INTO audit_log (admin_id, action, entity_type, entity_id, details) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, admin.id());
            statement.setString(2, action);
            statement.setString(3, entityType);
            if (entityId == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, entityId);
            }
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }
}
