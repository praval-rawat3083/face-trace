package facetrace.dao;

import facetrace.config.DatabaseConnection;
import facetrace.model.Student;

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

/** Data access object for the students table in the Smart Attendance System. */
public final class StudentDao implements AutoCloseable {
    private final Connection connection;

    public StudentDao() throws IOException, SQLException {
        this(DatabaseConnection.getConnection());
    }

    public StudentDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "A database connection is required");
    }

    /** Saves one student and returns the instance with database-generated id. */
    public Student add(Student student) throws SQLException {
        String sql = "INSERT INTO students (student_id, full_name, age, department, photo_path) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, student.getStudentId());
            statement.setString(2, student.getFullName());
            statement.setInt(3, student.getAge());
            statement.setString(4, student.getDepartment());
            statement.setString(5, student.getPhotoPath());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return an id for the new student");
                }
                return student.withId(keys.getLong(1));
            }
        }
    }

    /** Finds a student by primary key ID. */
    public Optional<Student> getById(long id) throws SQLException {
        String sql = "SELECT id, student_id, full_name, age, department, photo_path "
                + "FROM students WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readStudent(rows)) : Optional.empty();
            }
        }
    }

    /** Finds a student by unique studentId / roll number. */
    public Optional<Student> getByStudentId(String studentId) throws SQLException {
        String sql = "SELECT id, student_id, full_name, age, department, photo_path "
                + "FROM students WHERE student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readStudent(rows)) : Optional.empty();
            }
        }
    }

    /** Reads all enrolled students ordered by ID. */
    public List<Student> getAll() throws SQLException {
        String sql = "SELECT id, student_id, full_name, age, department, photo_path "
                + "FROM students ORDER BY id";
        List<Student> students = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                students.add(readStudent(rows));
            }
        }
        return students;
    }

    /** Updates an existing student record. */
    public boolean update(Student student) throws SQLException {
        String sql = "UPDATE students SET student_id = ?, full_name = ?, age = ?, department = ?, photo_path = ? "
                + "WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.getStudentId());
            statement.setString(2, student.getFullName());
            statement.setInt(3, student.getAge());
            statement.setString(4, student.getDepartment());
            statement.setString(5, student.getPhotoPath());
            statement.setLong(6, student.getId());
            return statement.executeUpdate() > 0;
        }
    }

    /** Deletes an enrolled student by ID. */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM students WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    /** Calculates attendance statistics and highlights defaulters (< 75% attendance). */
    public List<StudentAttendanceSummary> getAttendanceSummaries() throws SQLException {
        // Find total sessions recorded across the system
        int totalSessions = 1;
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(DISTINCT DATE(check_in_time)) FROM attendance_logs")) {
            if (rs.next() && rs.getInt(1) > 0) {
                totalSessions = rs.getInt(1);
            }
        }

        String sql = "SELECT s.id, s.student_id, s.full_name, s.department, " +
                "COUNT(a.id) as present_count " +
                "FROM students s " +
                "LEFT JOIN attendance_logs a ON a.matched_student_id = s.id AND a.status = 'PRESENT' " +
                "GROUP BY s.id, s.student_id, s.full_name, s.department " +
                "ORDER BY s.student_id";

        List<StudentAttendanceSummary> list = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String studentId = rs.getString("student_id");
                String name = rs.getString("full_name");
                String dept = rs.getString("department");
                int presents = rs.getInt("present_count");
                double percentage = Math.min(100.0, (presents * 100.0) / totalSessions);
                boolean isDefaulter = percentage < 75.0;

                list.add(new StudentAttendanceSummary(id, studentId, name, dept, presents, totalSessions, percentage, isDefaulter));
            }
        }
        return list;
    }

    private Student readStudent(ResultSet rows) throws SQLException {
        return new Student(
                rows.getLong("id"),
                rows.getString("student_id"),
                rows.getString("full_name"),
                rows.getInt("age"),
                rows.getString("department"),
                rows.getString("photo_path")
        );
    }

    public record StudentAttendanceSummary(long id, String studentId, String fullName, String department,
                                           int presents, int totalSessions, double percentage, boolean isDefaulter) {
        public String percentageFormatted() { return String.format("%.1f%%", percentage); }
        public String statusPill() { return isDefaulter ? "DEFICIT (<75%)" : "SATISFACTORY"; }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
