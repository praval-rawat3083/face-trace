package facetrace.dao;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** Saves a float vector using the same byte order and name as Phase2Demo. */
public final class AdminEmbeddingDao {
    private static final String EMBEDDER_NAME = "LBP-4x4-256-v1";
    private final Connection connection;

    /** Uses the caller's transaction so a record cannot be saved without its vector. */
    public AdminEmbeddingDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "A database connection is required");
    }

    /** Writes a computed vector; the image pixels themselves are not stored here. */
    public void insert(long recordId, float[] vector) throws SQLException {
        String sql = "INSERT INTO face_embeddings (record_id, vector, embedder_name) VALUES (?, ?, ?)";
        ByteBuffer bytes = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (float value : vector) {
            bytes.putFloat(value);
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recordId);
            statement.setBytes(2, bytes.array());
            statement.setString(3, EMBEDDER_NAME);
            statement.executeUpdate();
        }
    }
}
