package facetrace.service;

import facetrace.config.DatabaseConnection;
import facetrace.dao.AdminAuditDao;
import facetrace.dao.AdminEmbeddingDao;
import facetrace.dao.StudentDao;
import facetrace.model.AdminSession;
import facetrace.model.Student;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Applies admin security checks and keeps student record, embedding, and audit writes together. */
public final class AdminRecordService {
    private final RoleChecker roleChecker = new RoleChecker();
    private final AdminPhotoEnrollment photoEnrollment = new AdminPhotoEnrollment();

    /** Reads all enrolled students for a verified administrator. */
    public List<Student> loadRecords(AdminSession admin) throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        try (Connection connection = DatabaseConnection.getConnection()) {
            List<Student> records = new StudentDao(connection).getAll();
            new AdminAuditDao(connection).logRecordListView(admin);
            return records;
        }
    }

    /** Adds a student record, one face vector, and an audit row in one transaction. */
    public Student add(AdminSession admin, String studentId, String fullName, int age,
                       String department, Path photo, boolean consentConfirmed)
            throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        String cleanId = cleanCode(studentId);
        validateFields(cleanId, fullName, age, department, consentConfirmed);
        if (photo == null || !Files.isRegularFile(photo)) {
            throw new IOException("Choose a readable photo file first.");
        }
        String photoPath = photo.toAbsolutePath().normalize().toString();
        if (photoPath.length() > 500) {
            throw new IOException("The photo path is too long for the database field.");
        }
        float[] vector = photoEnrollment.embedOneFace(photo);
        Student draft = new Student(cleanId, fullName.trim(), age, department.trim(), photoPath);

        return inTransaction(connection -> {
            Student saved = new StudentDao(connection).add(draft);
            new AdminEmbeddingDao(connection).insert(saved.getId(), vector);
            new AdminAuditDao(connection).log(admin, "STUDENT_ENROLL", "STUDENT",
                    saved.getId(), "Enrolled student " + saved.getStudentId() + " (" + saved.getFullName() + ")");
            return saved;
        });
    }

    /** Updates student record details while keeping the saved photo and vector linked. */
    public boolean update(AdminSession admin, Student student, boolean consentConfirmed)
            throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        String cleanId = cleanCode(student.getStudentId());
        validateFields(cleanId, student.getFullName(), student.getAge(),
                student.getDepartment(), consentConfirmed);
        Student changed = new Student(student.getId(), cleanId,
                student.getFullName().trim(), student.getAge(), student.getDepartment().trim(),
                student.getPhotoPath());

        return inTransaction(connection -> {
            boolean updated = new StudentDao(connection).update(changed);
            if (updated) {
                new AdminAuditDao(connection).log(admin, "STUDENT_UPDATE", "STUDENT",
                        changed.getId(), "Updated student " + changed.getStudentId());
            }
            return updated;
        });
    }

    /** Removes a student record and audits the removal. */
    public boolean delete(AdminSession admin, long id) throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        return inTransaction(connection -> {
            Optional<Student> existing = new StudentDao(connection).getById(id);
            if (existing.isEmpty()) {
                return false;
            }
            boolean deleted = new StudentDao(connection).delete(id);
            if (deleted) {
                new AdminAuditDao(connection).log(admin, "STUDENT_DELETE", "STUDENT", id,
                        "Deleted student " + existing.get().getStudentId());
            }
            return deleted;
        });
    }

    public void logAdminLogin(AdminSession admin) throws IOException, SQLException {
        logSuccessfulLogin(admin);
    }

    public void logSuccessfulLogin(AdminSession admin) throws IOException, SQLException {
        roleChecker.requireAdmin(admin);
        try (Connection connection = DatabaseConnection.getConnection()) {
            new AdminAuditDao(connection).log(admin, "ADMIN_LOGIN", "ADMIN", admin.id(),
                    "Successful administrator sign-in");
        }
    }

    private void validateFields(String studentId, String name, int age, String department,
                                boolean consentConfirmed) {
        if (!consentConfirmed) {
            throw new IllegalArgumentException("Please check the consent confirmation box.");
        }
        if (name == null || name.trim().length() < 2 || name.trim().length() > 100) {
            throw new IllegalArgumentException("Please enter a valid student name (2 to 100 characters).");
        }
        if (studentId == null || studentId.trim().length() < 2 || studentId.trim().length() > 50) {
            throw new IllegalArgumentException("Enter a valid student ID / Roll No (e.g., STU-001 or 2026-CSE-01).");
        }
        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("Enter a valid age or semester number (1 to 120).");
        }
        if (department == null || department.isBlank() || department.trim().length() > 120) {
            throw new IllegalArgumentException("Enter a department or branch (e.g., Computer Science).");
        }
    }

    private String cleanCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

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
