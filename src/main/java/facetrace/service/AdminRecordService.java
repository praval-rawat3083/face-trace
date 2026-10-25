package facetrace.service;

import facetrace.config.DatabaseConnection;
import facetrace.dao.AdminAuditDao;
import facetrace.dao.AdminEmbeddingDao;
import facetrace.dao.CriminalDao;
import facetrace.model.AdminSession;
import facetrace.model.CriminalRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Applies admin checks and keeps record, embedding, and audit writes together. */
public final class AdminRecordService {
    private final RoleChecker roleChecker = new RoleChecker();
    private final AdminPhotoEnrollment photoEnrollment = new AdminPhotoEnrollment();

    /** Reads records only for a verified administrator. */
    public List<CriminalRecord> loadRecords(AdminSession admin) throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        try (Connection connection = DatabaseConnection.getConnection()) {
            List<CriminalRecord> records = new CriminalDao(connection).getAll();
            new AdminAuditDao(connection).logRecordListView(admin);
            return records;
        }
    }
    /** Adds a synthetic record, one face vector, and an audit row in one transaction. */
    public CriminalRecord add(AdminSession admin, String caseCode, String fullName, int age,
                              String note, Path photo, boolean syntheticConfirmed)
            throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        String cleanCaseCode = cleanCaseCode(caseCode);
        validateFields(cleanCaseCode, fullName, age, note, syntheticConfirmed);
        if (photo == null || !Files.isRegularFile(photo)) {
            throw new IOException("Choose a readable photo file first.");
        }
        String photoPath = photo.toAbsolutePath().normalize().toString();
        if (photoPath.length() > 500) {
            throw new IOException("The photo path is too long for the database field.");
        }
        float[] vector = photoEnrollment.embedOneFace(photo);
        CriminalRecord draft = new CriminalRecord(cleanCaseCode, fullName.trim(), age,
                note.trim(), photoPath);

        return inTransaction(connection -> {
            CriminalRecord saved = new CriminalDao(connection).add(draft);
            new AdminEmbeddingDao(connection).insert(saved.getId(), vector);
            new AdminAuditDao(connection).log(admin, "RECORD_ADD", "CRIMINAL_RECORD",
                    saved.getId(), "Added synthetic case " + saved.getCaseCode());
            return saved;
        });
    }
    /** Changes synthetic record details while keeping the saved photo and vector linked. */
    public boolean update(AdminSession admin, CriminalRecord record, boolean syntheticConfirmed)
            throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        String cleanCaseCode = cleanCaseCode(record.getCaseCode());
        validateFields(cleanCaseCode, record.getFullName(), record.getAge(),
                record.getOffenceType(), syntheticConfirmed);
        CriminalRecord changed = new CriminalRecord(record.getId(), cleanCaseCode,
                record.getFullName().trim(), record.getAge(), record.getOffenceType().trim(),
                record.getPhotoPath());

        return inTransaction(connection -> {
            CriminalDao dao = new CriminalDao(connection);
            if (dao.getById(changed.getId()).isEmpty()) {
                return false;
            }
            dao.update(changed);
            new AdminAuditDao(connection).log(admin, "RECORD_UPDATE", "CRIMINAL_RECORD",
                    changed.getId(), "Updated synthetic case " + changed.getCaseCode());
            return true;
        });
    }

    /** Deletes a record; MySQL removes its vectors and clears linked match ids. */
    public boolean delete(AdminSession admin, long recordId) throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        return inTransaction(connection -> {
            CriminalDao dao = new CriminalDao(connection);
            Optional<CriminalRecord> existing = dao.getById(recordId);
            if (existing.isEmpty() || !dao.delete(recordId)) {
                return false;
            }
            new AdminAuditDao(connection).log(admin, "RECORD_DELETE", "CRIMINAL_RECORD",
                    recordId, "Deleted synthetic case " + existing.get().getCaseCode());
            return true;
        });
    }

    /** Records a successful admin sign-in without saving any password text. */
    public void logAdminLogin(AdminSession admin) throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        try (Connection connection = DatabaseConnection.getConnection()) {
            new AdminAuditDao(connection).log(admin, "ADMIN_LOGIN", "ADMIN", admin.id(),
                    "Successful administrator sign-in");
        }
    }

    /** Ensures new entries use synthetic subject and demo case labels. */
    private void validateFields(String caseCode, String name, int age, String note,
                                boolean syntheticConfirmed) {
        if (!syntheticConfirmed) {
            throw new IllegalArgumentException("Confirm the synthetic details and synthetic photo first.");
        }
        if (name == null || !name.trim().matches("Subject-[0-9]{3}")) {
            throw new IllegalArgumentException("Use a synthetic name such as Subject-041.");
        }
        if (caseCode == null || !caseCode.matches("(?:ORL-)?DEMO-[A-Z0-9-]{1,40}")) {
            throw new IllegalArgumentException("Use a demo case code such as DEMO-041.");
        }
        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("Enter a synthetic age from 1 to 120.");
        }
        if (note == null || note.isBlank() || note.trim().length() > 120) {
            throw new IllegalArgumentException("Enter a short fictional note of up to 120 characters.");
        }
    }

    /** Normalizes a typed demo case code before checking and saving it. */
    private String cleanCaseCode(String caseCode) {
        return caseCode == null ? null : caseCode.trim().toUpperCase(Locale.ROOT);
    }

    /** Commits each record change and its audit row together, or rolls both back. */
    private <T> T inTransaction(AdminSqlWork<T> work) throws IOException, SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

}
