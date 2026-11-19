package facetrace.dao;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads saved student LBP vectors for verification and logs daily attendance with subject. */
public final class FaceSearchDao {
    private static final String EMBEDDER_NAME = "LBP-4x4-256-v1";
    private final Connection connection;

    public FaceSearchDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "A database connection is required");
    }

    /** Loads and averages stored photo vectors for each enrolled student. */
    public List<GalleryEntry> loadGallery() throws SQLException, IOException {
        String sql = "SELECT s.id, s.student_id, s.full_name, e.vector "
                + "FROM students s JOIN face_embeddings e ON e.record_id = s.id "
                + "WHERE e.embedder_name = ? ORDER BY s.student_id, e.id";

        Map<String, VectorTotal> totals = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, EMBEDDER_NAME);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String studentId = rows.getString("student_id");
                    float[] vector = decodeVector(rows.getBytes("vector"));
                    VectorTotal total = totals.get(studentId);
                    if (total == null) {
                        total = new VectorTotal(rows.getLong("id"), studentId,
                                rows.getString("full_name"), vector.length);
                        totals.put(studentId, total);
                    }
                    total.add(vector);
                }
            }
        }
        List<GalleryEntry> gallery = new ArrayList<>();
        for (VectorTotal total : totals.values()) {
            gallery.add(total.average());
        }
        return List.copyOf(gallery);
    }

    public void logSearch(String fileName, Long matchedStudentId, double bestScore) throws SQLException {
        logSearch(fileName, matchedStudentId, bestScore, "General");
    }

    /** Saves attendance check-in result to attendance_logs with subject and timestamp. */
    public void logSearch(String fileName, Long matchedStudentId, double bestScore, String subject) throws SQLException {
        String status = (matchedStudentId != null && bestScore >= 0.70) ? "PRESENT" : "UNRECOGNIZED";
        String cleanSubject = (subject != null && !subject.isBlank()) ? subject.trim() : "General";
        String sql = "INSERT INTO attendance_logs (probe_file, matched_student_id, subject, score, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fileName);
            if (matchedStudentId == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, matchedStudentId);
            }
            statement.setString(3, cleanSubject);
            statement.setDouble(4, bestScore);
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private float[] decodeVector(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) {
            throw new IOException("The database vector BLOB has an invalid size");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    public record GalleryEntry(long recordId, String studentId, String fullName, float[] vector) {
        public String caseCode() { return studentId; }
    }

    private static final class VectorTotal {
        private final long recordId;
        private final String studentId;
        private final String name;
        private final double[] sums;
        private int count;

        VectorTotal(long recordId, String studentId, String name, int size) {
            this.recordId = recordId;
            this.studentId = studentId;
            this.name = name;
            this.sums = new double[size];
        }

        void add(float[] vector) {
            for (int i = 0; i < sums.length; i++) {
                sums[i] += vector[i];
            }
            count++;
        }

        GalleryEntry average() {
            float[] average = new float[sums.length];
            for (int i = 0; i < sums.length; i++) {
                average[i] = (float) (sums[i] / count);
            }
            return new GalleryEntry(recordId, studentId, name, average);
        }
    }
}
