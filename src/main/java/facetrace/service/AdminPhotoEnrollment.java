package facetrace.service;

import facetrace.vision.FaceDetector;
import facetrace.vision.LbpEmbedder;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Path;

/** Checks that a photo has one face and makes its compatible LBP vector. */
public final class AdminPhotoEnrollment {
    private volatile FaceDetector detector;

    /** Rejects unreadable, no-face, and multi-face images with clear messages. */
    public float[] embedOneFace(Path photo) throws IOException {
        try (FaceDetector.DetectionResult result = getDetector().detect(photo)) {
            if (result.status() == FaceDetector.Status.IMAGE_NOT_READ) {
                throw new IOException("The selected photo could not be read.");
            }
            if (result.status() == FaceDetector.Status.NO_FACE) {
                throw new IOException("No face was detected. Choose a clear, front-facing synthetic photo.");
            }
            if (result.status() == FaceDetector.Status.MULTIPLE_FACES) {
                throw new IOException("More than one face was found. Choose a photo with one face.");
            }
        }

        Mat gray = Imgcodecs.imread(photo.toAbsolutePath().toString(), Imgcodecs.IMREAD_GRAYSCALE);
        try {
            long pixelsCount = gray.total();
            if (gray.empty() || gray.cols() < 3 || gray.rows() < 3 || pixelsCount > Integer.MAX_VALUE) {
                throw new IOException("The photo is too small or too large to process.");
            }
            byte[] pixels = new byte[(int) pixelsCount];
            if (gray.get(0, 0, pixels) != pixels.length) {
                throw new IOException("Could not read the photo pixels.");
            }
            BufferedImage image = new BufferedImage(gray.cols(), gray.rows(), BufferedImage.TYPE_BYTE_GRAY);
            byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(pixels, 0, target, 0, pixels.length);
            return new LbpEmbedder().embed(image);
        } finally {
            gray.release();
        }
    }

    /** Loads OpenCV and the bundled Haar file once for this enrollment service. */
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
}
