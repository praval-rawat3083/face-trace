package facetrace.app;

import facetrace.config.DatabaseConnection;
import facetrace.dao.CriminalDao;
import facetrace.match.FaceMatcher;
import facetrace.match.MatchResult;
import facetrace.model.CriminalRecord;
import facetrace.vision.FaceEmbedder;
import facetrace.vision.LbpEmbedder;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Enrolls a local ORL-style folder with synthetic labels and searches one probe image. */
public final class Phase2Demo {
    private static final String CASE_PREFIX = "ORL-DEMO-";
    private static final String EMBEDDER_NAME = "LBP-4x4-256-v1";
    private static final String SYNTHETIC_CASE = "SYNTHETIC DEMO ONLY - NOT A REAL OFFENCE";
    private static final int TOP_K = 3;
    private static boolean openCvLoaded;

    /** Keeps this command-line program from being created as an object. */
    private Phase2Demo() { }

    /** Reads command-line paths, enrolls the dataset, searches, prints, and logs one search. */
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: Phase2Demo <dataset-folder> <probe-image.pgm> [threshold-0-to-1]");
            return;
        }
        Path datasetRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path probePath = Path.of(args[1]).toAbsolutePath().normalize();
        double threshold = args.length == 3 ? Double.parseDouble(args[2]) : 0.0;
        FaceMatcher matcher = new FaceMatcher(threshold);
        if (!Files.isDirectory(datasetRoot) || !Files.isRegularFile(probePath)) {
            throw new IOException("Check that the dataset folder and probe image both exist");
        }

        List<Path> subjectFolders = findSubjectFolders(datasetRoot);
        FaceEmbedder embedder = new LbpEmbedder();
        float[] probe = embedder.embed(readPgm(probePath));
        System.out.println("Demo only: all case details are synthetic, and scores are not proof of identity.");
        System.out.println("Reading local images; the original image files are not copied into the project.");

        Connection connection = DatabaseConnection.getConnection();
        try (CriminalDao dao = new CriminalDao(connection)) {
            int imageCount = enrollDataset(connection, dao, subjectFolders, probePath, embedder);
            Map<String, Long> recordIds = new HashMap<>();
            Map<String, String> subjectNames = new HashMap<>();
            Map<String, float[]> gallery = loadGallery(connection, recordIds, subjectNames);
            if (gallery.isEmpty()) {
                throw new SQLException("No enrolled subject embeddings were found in the database");
            }

            List<MatchResult> bestActual = new FaceMatcher(0.0).rankTopK(probe, gallery, 1);
            List<MatchResult> matches = matcher.rankTopK(probe, gallery, TOP_K);
            logSearch(connection, probePath, matches, bestActual.get(0), recordIds);
            System.out.printf(Locale.ROOT, "Enrolled %d PGM images across %d subject folders.%n",
                    imageCount, subjectFolders.size());
            System.out.printf(Locale.ROOT, "Top matches with threshold %.3f:%n", threshold);
            if (matches.isEmpty()) {
                System.out.printf(Locale.ROOT, "No candidate reached the threshold; best actual score was %.4f.%n",
                        bestActual.get(0).similarity());
            }
            for (int rank = 0; rank < matches.size(); rank++) {
                MatchResult match = matches.get(rank);
                System.out.printf(Locale.ROOT, "%d. %s (%s) — cosine similarity %.4f%n", rank + 1,
                        subjectNames.get(match.caseCode()), match.caseCode(), match.similarity());
            }
            System.out.println("This search was written to match_logs.");
        }
    }

    /** Finds subject folders named s1, s2, and so on, in number order. */
    private static List<Path> findSubjectFolders(Path root) throws IOException {
        try (Stream<Path> children = Files.list(root)) {
            List<Path> folders = children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("s[0-9]+"))
                    .sorted(Comparator.comparingInt(Phase2Demo::subjectNumber))
                    .toList();
            if (folders.isEmpty()) {
                throw new IOException("The dataset folder must contain folders named s1, s2, and so on");
            }
            return folders;
        }
    }

    /** Reads the number from a folder name such as s12. */
    private static int subjectNumber(Path folder) {
        return Integer.parseInt(folder.getFileName().toString().substring(1));
    }

    /** Lists PGM files in a subject folder in a stable name order. */
    private static List<Path> pgmFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pgm"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    /** Adds or updates synthetic subject rows and replaces only this demo's old image embeddings. */
    private static int enrollDataset(Connection connection, CriminalDao dao, List<Path> folders,
                                     Path probePath, FaceEmbedder embedder) throws SQLException, IOException {
        boolean restoreAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            Map<String, CriminalRecord> existing = new HashMap<>();
            for (CriminalRecord record : dao.getAll()) {
                if (record.getCaseCode().startsWith(CASE_PREFIX)) {
                    existing.put(record.getCaseCode(), record);
                }
            }

            int imageCount = 0;
            for (Path folder : folders) {
                List<Path> images = new ArrayList<>(pgmFiles(folder));
                images.removeIf(path -> path.toAbsolutePath().normalize().equals(probePath));
                if (images.isEmpty()) {
                    throw new IOException("No training PGM images remain in " + folder
                            + "; use a probe file outside that subject's only training images");
                }
                int number = subjectNumber(folder);
                String subject = String.format(Locale.ROOT, "Subject-%03d", number);
                String caseCode = CASE_PREFIX + String.format(Locale.ROOT, "%03d", number);
                CriminalRecord oldRecord = existing.get(caseCode);
                long id = oldRecord == null ? 0 : oldRecord.getId();
                // The ORL dataset has no ages; zero marks an unknown demo age, not a real person's age.
                CriminalRecord record = new CriminalRecord(id, caseCode, subject, 0, SYNTHETIC_CASE,
                        images.get(0).toAbsolutePath().toString());
                if (oldRecord == null) {
                    record = dao.add(record);
                    existing.put(caseCode, record);
                } else if (!dao.update(record)) {
                    throw new SQLException("Could not update demo record " + caseCode);
                }
                replaceEmbeddings(connection, record.getId(), images, embedder);
                imageCount += images.size();
            }
            connection.commit();
            return imageCount;
        } catch (SQLException | IOException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(restoreAutoCommit);
        }
    }

    /** Replaces stored vectors for one demo subject using each PGM file in that folder. */
    private static void replaceEmbeddings(Connection connection, long recordId, List<Path> images,
                                          FaceEmbedder embedder) throws SQLException, IOException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM face_embeddings WHERE record_id = ? AND embedder_name = ?")) {
            delete.setLong(1, recordId);
            delete.setString(2, EMBEDDER_NAME);
            delete.executeUpdate();
        }
        String sql = "INSERT INTO face_embeddings (record_id, vector, embedder_name) VALUES (?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Path imagePath : images) {
                float[] vector = embedder.embed(readPgm(imagePath));
                insert.setLong(1, recordId);
                insert.setBytes(2, encodeVector(vector));
                insert.setString(3, EMBEDDER_NAME);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Loads saved vectors, averages each subject's pictures, and keeps their ids and display labels. */
    private static Map<String, float[]> loadGallery(Connection connection, Map<String, Long> recordIds,
                                                     Map<String, String> subjectNames)
            throws SQLException, IOException {
        Map<String, float[]> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        String sql = "SELECT c.id, c.case_code, c.full_name, e.vector FROM criminal_records c "
                + "JOIN face_embeddings e ON e.record_id = c.id "
                + "WHERE c.case_code LIKE ? AND e.embedder_name = ? ORDER BY c.case_code, e.id";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, CASE_PREFIX + "%");
            query.setString(2, EMBEDDER_NAME);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String caseCode = rows.getString("case_code");
                    float[] vector = decodeVector(rows.getBytes("vector"));
                    float[] total = totals.computeIfAbsent(caseCode, ignored -> new float[vector.length]);
                    if (vector.length != total.length) {
                        throw new SQLException("Stored LBP vectors have different sizes");
                    }
                    for (int i = 0; i < vector.length; i++) {
                        total[i] += vector[i];
                    }
                    counts.merge(caseCode, 1, Integer::sum);
                    recordIds.put(caseCode, rows.getLong("id"));
                    subjectNames.put(caseCode, rows.getString("full_name"));
                }
            }
        }
        for (Map.Entry<String, float[]> entry : totals.entrySet()) {
            int count = counts.get(entry.getKey());
            for (int i = 0; i < entry.getValue().length; i++) {
                entry.getValue()[i] /= count;
            }
        }
        return totals;
    }

    /** Saves one log row with the best real score and a null record when the threshold rejected it. */
    private static void logSearch(Connection connection, Path probePath, List<MatchResult> matches,
                                  MatchResult bestActual, Map<String, Long> recordIds) throws SQLException {
        String sql = "INSERT INTO match_logs (probe_file, matched_record_id, score) VALUES (?, ?, ?)";
        try (PreparedStatement log = connection.prepareStatement(sql)) {
            log.setString(1, probePath.toString());
            if (matches.isEmpty()) {
                log.setNull(2, Types.BIGINT);
            } else {
                log.setLong(2, recordIds.get(matches.get(0).caseCode()));
            }
            log.setDouble(3, bestActual.similarity());
            log.executeUpdate();
        }
    }

    /** Stores floats as four bytes each so the vector can be saved in a MySQL BLOB. */
    private static byte[] encodeVector(float[] vector) {
        ByteBuffer bytes = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (float value : vector) {
            bytes.putFloat(value);
        }
        return bytes.array();
    }

    /** Turns the saved bytes back into the feature values used by FaceMatcher. */
    private static float[] decodeVector(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IOException("A stored embedding has an invalid byte length");
        }
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = input.getFloat();
        }
        return vector;
    }

    /** Uses OpenCV only to read the PGM file; LbpEmbedder itself is pure Java. */
    static BufferedImage readPgm(Path imagePath) throws IOException {
        loadOpenCv();
        Mat gray = Imgcodecs.imread(imagePath.toAbsolutePath().toString(), Imgcodecs.IMREAD_GRAYSCALE);
        try {
            if (gray.empty() || gray.channels() != 1) {
                throw new IOException("Could not read a grayscale image: " + imagePath);
            }
            long pixelCount = gray.total();
            if (gray.cols() < 3 || gray.rows() < 3 || pixelCount > Integer.MAX_VALUE) {
                throw new IOException("Image dimensions are not supported: " + imagePath);
            }
            byte[] pixels = new byte[(int) pixelCount];
            if (gray.get(0, 0, pixels) != pixels.length) {
                throw new IOException("Could not read all image pixels: " + imagePath);
            }
            BufferedImage image = new BufferedImage(gray.cols(), gray.rows(), BufferedImage.TYPE_BYTE_GRAY);
            byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(pixels, 0, target, 0, pixels.length);
            return image;
        } finally {
            gray.release();
        }
    }

    /** Loads OpenCV's native library once before it reads PGM files. */
    private static synchronized void loadOpenCv() {
        if (!openCvLoaded) {
            OpenCV.loadLocally();
            openCvLoaded = true;
        }
    }
}
