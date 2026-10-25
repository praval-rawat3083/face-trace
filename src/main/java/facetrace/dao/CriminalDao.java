package facetrace.dao;

import facetrace.config.DatabaseConnection;
import facetrace.model.CriminalRecord;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runs safe SQL commands for saving and reading criminal_records rows. */
public final class CriminalDao implements AutoCloseable {
    private final Connection connection;

    /** Opens a connection to the normal database in the local config file. */
    public CriminalDao() throws IOException, SQLException {
        this(DatabaseConnection.getConnection());
    }

    /** Uses the supplied connection, which lets tests point only at the test database. */
    public CriminalDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "A database connection is required");
    }

    /** Saves one row and returns a copy containing the id MySQL assigned. */
    public CriminalRecord add(CriminalRecord record) throws SQLException {
        String sql = "INSERT INTO criminal_records (case_code, full_name, age, offence_type, photo_path) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindRecord(statement, record, false);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("MySQL did not return an id for the new record");
                }
                return record.withId(keys.getLong(1));
            }
        }
    }

    /** Finds one row by id, or returns an empty result when there is no such row. */
    public Optional<CriminalRecord> getById(long id) throws SQLException {
        String sql = "SELECT id, case_code, full_name, age, offence_type, photo_path "
                + "FROM criminal_records WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readRecord(rows)) : Optional.empty();
            }
        }
    }

    /** Reads every row in id order so the returned list is steady and easy to inspect. */
    public List<CriminalRecord> getAll() throws SQLException {
        String sql = "SELECT id, case_code, full_name, age, offence_type, photo_path "
                + "FROM criminal_records ORDER BY id";
        List<CriminalRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                records.add(readRecord(rows));
            }
        }
        return records;
    }

    /** Replaces the saved fields for one id and says whether a row was changed. */
    public boolean update(CriminalRecord record) throws SQLException {
        String sql = "UPDATE criminal_records SET case_code = ?, full_name = ?, age = ?, "
                + "offence_type = ?, photo_path = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRecord(statement, record, true);
            return statement.executeUpdate() == 1;
        }
    }

    /** Deletes one row and lets MySQL apply the schema's linked-row rules. */
    public boolean delete(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM criminal_records WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        }
    }

    /** Closes the connection when the caller has finished using this DAO. */
    @Override
    public void close() throws SQLException {
        connection.close();
    }

    /** Copies a record's values into the insert or update statement in the correct order. */
    private void bindRecord(PreparedStatement statement, CriminalRecord record, boolean includeId)
            throws SQLException {
        statement.setString(1, record.getCaseCode());
        statement.setString(2, record.getFullName());
        statement.setInt(3, record.getAge());
        statement.setString(4, record.getOffenceType());
        statement.setString(5, record.getPhotoPath());
        if (includeId) {
            statement.setLong(6, record.getId());
        }
    }

    /** Turns the current SQL row into a simple Java record object. */
    private CriminalRecord readRecord(ResultSet rows) throws SQLException {
        return new CriminalRecord(rows.getLong("id"), rows.getString("case_code"),
                rows.getString("full_name"), rows.getInt("age"), rows.getString("offence_type"),
                rows.getString("photo_path"));
    }
}
