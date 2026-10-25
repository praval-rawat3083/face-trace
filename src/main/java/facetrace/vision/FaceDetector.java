package facetrace.vision;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads an image, finds faces with OpenCV's Haar file, and prepares the largest face. */
public final class FaceDetector {
    private final CascadeClassifier classifier;

    /** Starts OpenCV and loads the cascade file packaged with this project. */
    public FaceDetector() throws IOException {
        OpenCV.loadLocally();
        Path cascadePath = Files.createTempFile("facetrace-haar-", ".xml");
        try (InputStream input = FaceDetector.class.getResourceAsStream("/haarcascade_frontalface_default.xml")) {
            if (input == null) {
                throw new IOException("Haar cascade is missing from src/main/resources");
            }
            Files.copy(input, cascadePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        cascadePath.toFile().deleteOnExit();
        classifier = new CascadeClassifier(cascadePath.toString());
        if (classifier.empty()) {
            throw new IOException("OpenCV could not read the Haar cascade file");
        }
    }

    /** Reads one image, reports zero/one/many faces, and crops the largest detected face. */
    public DetectionResult detect(Path imagePath) {
        Mat original = Imgcodecs.imread(imagePath.toAbsolutePath().toString());
        if (original.empty()) {
            original.release();
            return new DetectionResult(Status.IMAGE_NOT_READ, List.of(), new Mat(), new Mat());
        }

        Mat gray = new Mat();
        Mat equalized = new Mat();
        Mat annotated = original.clone();
        MatOfRect found = new MatOfRect();
        Mat preparedFace = new Mat();
        try {
            Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, equalized);
            classifier.detectMultiScale(equalized, found, 1.1, 5, 0, new Size(30, 30), new Size());
            Rect[] rectangles = found.toArray();
            List<Rect> faces = new ArrayList<>();
            Rect largest = null;
            for (Rect rectangle : rectangles) {
                faces.add(new Rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height));
                Imgproc.rectangle(annotated, rectangle, new Scalar(0, 255, 0), 3);
                if (largest == null || (long) rectangle.width * rectangle.height > (long) largest.width * largest.height) {
                    largest = rectangle;
                }
            }

            Status status = rectangles.length == 0 ? Status.NO_FACE
                    : rectangles.length == 1 ? Status.ONE_FACE : Status.MULTIPLE_FACES;
            if (largest != null) {
                Mat faceRegion = equalized.submat(largest);
                try {
                    Imgproc.resize(faceRegion, preparedFace, new Size(160, 160));
                } finally {
                    faceRegion.release();
                }
            }
            return new DetectionResult(status, faces, preparedFace, annotated);
        } finally {
            original.release();
            gray.release();
            equalized.release();
            found.release();
        }
    }

    /** Names the clear outcomes returned when an image is checked. */
    public enum Status { IMAGE_NOT_READ, NO_FACE, ONE_FACE, MULTIPLE_FACES }

    /** Holds the detected boxes, a normalized main face, and the boxed copy of the input image. */
    public static final class DetectionResult implements AutoCloseable {
        private final Status status;
        private final List<Rect> faces;
        private final Mat mainFace;
        private final Mat annotatedImage;

        /** Keeps the computed result together so callers can inspect and release its image data. */
        private DetectionResult(Status status, List<Rect> faces, Mat mainFace, Mat annotatedImage) {
            this.status = status;
            this.faces = Collections.unmodifiableList(new ArrayList<>(faces));
            this.mainFace = mainFace;
            this.annotatedImage = annotatedImage;
        }

        /** Gives the result label, such as NO_FACE or MULTIPLE_FACES. */
        public Status status() { return status; }

        /** Gives the rectangles OpenCV actually found in the image. */
        public List<Rect> faces() { return faces; }

        /** Gives the largest detected face resized to 160 by 160, or an empty matrix. */
        public Mat mainFace() { return mainFace; }

        /** Gives a copy of the source image with every detected face box drawn on it. */
        public Mat annotatedImage() { return annotatedImage; }

        /** Releases native image memory after the caller has finished with this result. */
        @Override
        public void close() {
            mainFace.release();
            annotatedImage.release();
        }
    }
}
