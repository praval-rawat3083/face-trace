package facetrace.service;

import facetrace.config.DatabaseConnection;
import facetrace.dao.FaceSearchDao;
import facetrace.match.FaceMatcher;
import facetrace.match.MatchResult;
import facetrace.vision.FaceDetector;
import facetrace.vision.LbpEmbedder;
import facetrace.vision.LivenessDetector;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

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

/** Runs attendance biometric verification with anti-spoofing and lecture/subject tracking. */
public final class FaceSearchService {
    private volatile FaceDetector detector;

    /** Verifies a static image from disk against enrolled student vectors. */
    public SearchResponse search(Path imagePath, double threshold, String subject) throws IOException, SQLException {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return new SearchResponse(null, List.of(),
                    "The selected photo cannot be found. Choose a readable JPEG, PNG, BMP, or PGM photo.", true);
        }
        Mat original = Imgcodecs.imread(imagePath.toAbsolutePath().toString());
        try {
            if (original.empty()) {
                return new SearchResponse(null, List.of(), "That file could not be read as an image.", true);
            }
            return searchMat(original, threshold, imagePath.getFileName().toString(), subject);
        } finally {
            original.release();
        }
    }

    public SearchResponse search(Path imagePath, double threshold) throws IOException, SQLException {
        return search(imagePath, threshold, "General");
    }

    public SearchResponse searchMat(Mat frame, double threshold, String sourceLabel) throws IOException, SQLException {
        return searchMat(frame, threshold, sourceLabel, "General");
    }

    /** Verifies an in-memory camera frame (or static image) against enrolled student vectors. */
    public SearchResponse searchMat(Mat frame, double threshold, String sourceLabel, String subject) throws IOException, SQLException {
        if (frame == null || frame.empty()) {
            return new SearchResponse(null, List.of(), "No camera frame or image provided.", true);
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            return new SearchResponse(null, List.of(), "Threshold must be a valid confidence number from 0 to 1.", true);
        }

        FaceDetector faceDetector = getDetector();
        Mat gray = new Mat();
        Mat equalized = new Mat();
        Mat annotated = frame.clone();
        try {
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, equalized);

            MatOfRect found = new MatOfRect();
            try {
                faceDetector.getClassifier().detectMultiScale(equalized, found, 1.15, 4, 0, new Size(30, 30), new Size());
                Rect[] rects = found.toArray();
                if (rects.length == 0) {
                    byte[] preview = toPng(annotated);
                    return new SearchResponse(preview, List.of(),
                            "No face detected in frame. Ensure good lighting and look straight into the camera.", true);
                }

                // Liveness / Anti-spoofing check on the primary face
                Rect primaryFace = rects[0];
                for (Rect r : rects) {
                    if (r.width * r.height > primaryFace.width * primaryFace.height) {
                        primaryFace = r;
                    }
                }

                Mat faceCrop = equalized.submat(primaryFace);
                LivenessDetector.LivenessResult liveness = LivenessDetector.checkLiveness(faceCrop);
                faceCrop.release();

                Scalar boxColor = liveness.isLive() ? new Scalar(16, 185, 129) : new Scalar(239, 68, 68); // green or red
                for (Rect r : rects) {
                    Imgproc.rectangle(annotated, r, boxColor, 3);
                }

                byte[] preview = toPng(annotated);
                BufferedImage grayImage = matToBufferedImage(gray);
                float[] probe = new LbpEmbedder().embed(grayImage);

                try (Connection connection = DatabaseConnection.getConnection()) {
                    FaceSearchDao dao = new FaceSearchDao(connection);
                    List<FaceSearchDao.GalleryEntry> entries = dao.loadGallery();
                    if (entries.isEmpty()) {
                        return new SearchResponse(preview, List.of(),
                                "No enrolled students found. Enroll students via 'Student Registration' first.", liveness.isLive());
                    }

                    Map<String, float[]> vectors = new LinkedHashMap<>();
                    Map<String, FaceSearchDao.GalleryEntry> byId = new LinkedHashMap<>();
                    for (FaceSearchDao.GalleryEntry entry : entries) {
                        vectors.put(entry.studentId(), entry.vector());
                        byId.put(entry.studentId(), entry);
                    }

                    List<MatchResult> bestActual = new FaceMatcher(0.0).rankTopK(probe, vectors, 1);
                    List<MatchResult> matches = new FaceMatcher(threshold).rankTopK(probe, vectors, 3);
                    MatchResult best = bestActual.get(0);
                    Long matchedId = matches.isEmpty() ? null : byId.get(matches.get(0).caseCode()).recordId();

                    dao.logSearch(sourceLabel != null ? sourceLabel : "LIVE_CAPTURE", matchedId, best.similarity(), subject);

                    List<SearchHit> hits = new ArrayList<>();
                    for (MatchResult match : matches) {
                        FaceSearchDao.GalleryEntry entry = byId.get(match.caseCode());
                        hits.add(new SearchHit(entry.fullName(), entry.studentId(), match.similarity()));
                    }

                    String livenessNote = liveness.isLive() ? " [Live Face Verified]" : " [" + liveness.message() + "]";
                    String message;
                    if (!matches.isEmpty()) {
                        SearchHit top = hits.get(0);
                        message = String.format(Locale.ROOT,
                                "✔ Verified & Marked Present: %s (%s) — %s | Match: %.1f%%%s",
                                top.name(), top.caseCode(), subject, top.score() * 100, livenessNote);
                    } else {
                        message = String.format(Locale.ROOT,
                                "✖ Unrecognized Face (Best match: %.1f%%, below %.0f%% cutoff). Attendance not marked.%s",
                                best.similarity() * 100, threshold * 100, livenessNote);
                    }

                    return new SearchResponse(preview, hits, message, liveness.isLive());
                }
            } finally {
                found.release();
            }
        } finally {
            gray.release();
            equalized.release();
            annotated.release();
        }
    }

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

    private BufferedImage matToBufferedImage(Mat gray) {
        long pixelCount = gray.total();
        byte[] pixels = new byte[(int) pixelCount];
        gray.get(0, 0, pixels);
        BufferedImage image = new BufferedImage(gray.cols(), gray.rows(), BufferedImage.TYPE_BYTE_GRAY);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, target, 0, pixels.length);
        return image;
    }

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

    public record SearchHit(String name, String caseCode, double score) {
        public String studentId() { return caseCode; }
        public String percentage() { return String.format(Locale.ROOT, "%.1f%%", score * 100); }
    }

    public record SearchResponse(byte[] previewPng, List<SearchHit> hits, String message, boolean isLive) {
        public SearchResponse {
            hits = List.copyOf(hits);
        }
    }
}
