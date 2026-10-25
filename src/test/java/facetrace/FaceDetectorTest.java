package facetrace;

import facetrace.vision.FaceDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks zero, one, and multiple face outcomes with generated images, not real people's photos. */
class FaceDetectorTest {
    @TempDir
    Path tempDir;

    /** Confirms a generated blank picture returns a clear no-face result. */
    @Test
    void reportsNoFaceInBlankImage() throws IOException {
        BufferedImage blank = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = blank.createGraphics();
        pen.setColor(Color.LIGHT_GRAY);
        pen.fillRect(0, 0, 320, 240);
        pen.dispose();
        Path path = tempDir.resolve("blank.jpg");
        ImageIO.write(blank, "jpg", path.toFile());
        try (FaceDetector.DetectionResult result = new FaceDetector().detect(path)) {
            assertEquals(FaceDetector.Status.NO_FACE, result.status());
            assertTrue(result.faces().isEmpty());
            assertTrue(result.mainFace().empty());
        }
    }

    /** Confirms the generated one-face image gives one result and a 160 by 160 crop. */
    @Test
    void detectsOneGeneratedFace() throws Exception {
        Path image = Path.of(getClass().getResource("/facetrace/synthetic-single.jpg").toURI());
        try (FaceDetector.DetectionResult result = new FaceDetector().detect(image)) {
            assertEquals(FaceDetector.Status.ONE_FACE, result.status());
            assertEquals(1, result.faces().size());
            assertEquals(160, result.mainFace().cols());
            assertEquals(160, result.mainFace().rows());
        }
    }

    /** Confirms the generated two-face image gives multiple results and a largest-face crop. */
    @Test
    void detectsManyGeneratedFaces() throws Exception {
        Path image = Path.of(getClass().getResource("/facetrace/synthetic-pair.jpg").toURI());
        try (FaceDetector.DetectionResult result = new FaceDetector().detect(image)) {
            assertEquals(FaceDetector.Status.MULTIPLE_FACES, result.status());
            assertTrue(result.faces().size() > 1);
            assertEquals(160, result.mainFace().cols());
            assertEquals(160, result.mainFace().rows());
        }
    }
}
