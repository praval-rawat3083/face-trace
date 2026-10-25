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

/** Reads saved LBP vectors for search and records one measured search result. */
public final class FaceSearchDao {
    private static final String EMBEDDER_NAME = "LBP-4x4-256-v1";
    private final Connection connection;

    /** Uses the connection owned by the background search task. */
    public FaceSearchDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "A database connection is required");
    }

    /** Averages every stored photo vector for the same record. */
    public List<GalleryEntry> loadGallery() throws SQLException, IOException {
        String sql = "SELECT c.id, c.case_code, c.full_name, e.vector "
                + "FROM criminal_records c JOIN face_embeddings e ON e.record_id = c.id "
                + "WHERE e.embedder_name = ? ORDER BY c.case_code, e.id";
        Map<String, VectorTotal> totals = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, EMBEDDER_NAME);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String caseCode = rows.getString("case_code");
                    float[] vector = decodeVector(rows.getBytes("vector"));
                    VectorTotal total = totals.get(caseCode);
                    if (total == null) {
                        total = new VectorTotal(rows.getLong("id"), caseCode,
                                rows.getString("full_name"), vector.length);
                        totals.put(caseCode, total);
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

    /** Saves the selected file name and the real best score; a rejected result has no record id. */
    public void logSearch(String fileName, Long matchedRecordId, double bestScore) throws SQLException {
        String sql = "INSERT INTO match_logs (probe_file, matched_record_id, score) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fileName);
            if (matchedRecordId == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, matchedRecordId);
            }
            statement.setDouble(3, bestScore);
            statement.executeUpdate();
        }
    }

    /** Reads the stored four-byte floats in the same order used by Phase2Demo. */
    private float[] decodeVector(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IOException("A saved face vector has an invalid size");
        }
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = input.getFloat();
        }
        return vector;
    }

    /** Holds a running total while several photos for one record are read. */
    private static final class VectorTotal {
        private final long recordId;
        private final String caseCode;
        private final String fullName;
        private final double[] values;
        private int count;

        private VectorTotal(long recordId, String caseCode, String fullName, int size) {
            this.recordId = recordId;
            this.caseCode = caseCode;
            this.fullName = fullName;
            this.values = new double[size];
        }

        private void add(float[] vector) throws IOException {
            if (vector.length != values.length) {
                throw new IOException("Saved LBP vectors have different sizes");
            }
            for (int i = 0; i < vector.length; i++) {
                values[i] += vector[i];
            }
            count++;
        }

        private GalleryEntry average() {
            float[] average = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                average[i] = (float) (values[i] / count);
            }
            return new GalleryEntry(recordId, caseCode, fullName, average);
        }
    }

    /** One database record and its averaged face vector. */
    public record GalleryEntry(long recordId, String caseCode, String fullName, float[] vector) { }
}
