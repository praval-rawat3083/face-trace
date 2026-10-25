package facetrace.service;

import facetrace.config.DatabaseConnection;
import facetrace.dao.FaceSearchDao;
import facetrace.match.FaceMatcher;
import facetrace.match.MatchResult;
import facetrace.vision.FaceDetector;
import facetrace.vision.LbpEmbedder;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs one search without copying the chosen photo or blocking the JavaFX window. */
public final class FaceSearchService {
    private volatile FaceDetector detector;

    /** Processes the selected photo, ranks real saved vectors, and logs one completed search. */
    public SearchResponse search(Path imagePath, double threshold) throws IOException, SQLException {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return new SearchResponse(null, List.of(),
                    "The selected file cannot be found. Choose a readable JPEG, PNG, BMP, or PGM photo.");
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            return new SearchResponse(null, List.of(), "Threshold must be a number from 0 to 1.");
        }

        FaceDetector faceDetector = getDetector();
        try (FaceDetector.DetectionResult detection = faceDetector.detect(imagePath)) {
            if (detection.status() == FaceDetector.Status.IMAGE_NOT_READ) {
                return new SearchResponse(null, List.of(),
                        "That file could not be read as an image. Choose a readable JPEG, PNG, BMP, or PGM file.");
            }

            byte[] preview = toPng(detection.annotatedImage());
            if (detection.status() == FaceDetector.Status.NO_FACE) {
                return new SearchResponse(preview, List.of(),
                        "No face was detected. Choose a clear, front-facing image; no search was logged.");
            }

            // Step 5 enrolled full grayscale images, so use the same input preparation for fair scores.
            float[] probe = new LbpEmbedder().embed(readGrayImage(imagePath));
            try (Connection connection = DatabaseConnection.getConnection()) {
                FaceSearchDao dao = new FaceSearchDao(connection);
                List<FaceSearchDao.GalleryEntry> entries = dao.loadGallery();
                if (entries.isEmpty()) {
                    return new SearchResponse(preview, List.of(),
                            "No saved LBP embeddings were found. Enroll the permitted demo gallery first.");
                }

                Map<String, float[]> vectors = new LinkedHashMap<>();
                Map<String, FaceSearchDao.GalleryEntry> byCaseCode = new LinkedHashMap<>();
                for (FaceSearchDao.GalleryEntry entry : entries) {
                    vectors.put(entry.caseCode(), entry.vector());
                    byCaseCode.put(entry.caseCode(), entry);
                }

                List<MatchResult> bestActual = new FaceMatcher(0.0).rankTopK(probe, vectors, 1);
                List<MatchResult> matches = new FaceMatcher(threshold).rankTopK(probe, vectors, 3);
                MatchResult best = bestActual.get(0);
                Long matchedId = matches.isEmpty() ? null : byCaseCode.get(matches.get(0).caseCode()).recordId();
                dao.logSearch(imagePath.getFileName().toString(), matchedId, best.similarity());

                List<SearchHit> hits = new ArrayList<>();
                for (MatchResult match : matches) {
                    FaceSearchDao.GalleryEntry entry = byCaseCode.get(match.caseCode());
                    hits.add(new SearchHit(entry.fullName(), entry.caseCode(), match.similarity()));
                }
                String faceNote = detection.status() == FaceDetector.Status.MULTIPLE_FACES
                        ? "Detected " + detection.faces().size()
                        + " faces; comparing the full grayscale photo. Choose one face for a clearer result. " : "";
                String message = matches.isEmpty()
                        ? faceNote + String.format(Locale.ROOT,
                        "No candidate reached the threshold. Highest measured score: %.4f. Search logged.", best.similarity())
                        : faceNote + "Search finished and logged to match_logs.";
                return new SearchResponse(preview, hits, message);
            }
        }
    }

    /** Loads OpenCV and the cascade once, on the first background search. */
    private FaceDetector getDetector() throws IOException {
        if (detector == null) {
            synchronized (this) {
                if (detector == null) {
                    detector = new FaceDetector();
                }
            }
        }
        return detector;
    }

    /** Reads the full photo in grayscale to match the existing Phase2Demo gallery vectors. */
    private BufferedImage readGrayImage(Path imagePath) throws IOException {
        Mat gray = Imgcodecs.imread(imagePath.toAbsolutePath().toString(), Imgcodecs.IMREAD_GRAYSCALE);
        try {
            long pixelCount = gray.total();
            if (gray.empty() || gray.channels() != 1 || gray.cols() < 3 || gray.rows() < 3
                    || pixelCount > Integer.MAX_VALUE) {
                throw new IOException("The selected photo is too small or too large to process");
            }
            byte[] pixels = new byte[(int) pixelCount];
            if (gray.get(0, 0, pixels) != pixels.length) {
                throw new IOException("Could not read all pixels from the selected photo");
            }
            BufferedImage image = new BufferedImage(gray.cols(), gray.rows(), BufferedImage.TYPE_BYTE_GRAY);
            byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(pixels, 0, target, 0, pixels.length);
            return image;
        } finally {
            gray.release();
        }
    }

    /** Encodes the boxed preview in memory so the original photo is never overwritten or copied. */
    private byte[] toPng(Mat annotatedImage) throws IOException {
        MatOfByte encoded = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", annotatedImage, encoded)) {
                throw new IOException("Could not prepare the boxed photo preview");
            }
            return encoded.toArray();
        } finally {
            encoded.release();
        }
    }

    /** Search-row values shown in the results table. */
    public record SearchHit(String name, String caseCode, double score) { }

    /** Search results plus an in-memory copy of the detector preview and a plain status message. */
    public record SearchResponse(byte[] previewPng, List<SearchHit> hits, String message) {
        public SearchResponse {
            hits = List.copyOf(hits);
        }
    }
}
