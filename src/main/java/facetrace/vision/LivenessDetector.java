package facetrace.vision;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

/**
 * Passive Anti-Spoofing and Liveness Verification.
 * Analyzes micro-texture high-frequency variance using a Laplacian kernel to detect
 * static paper print attacks, low-resolution phone screen replays, and blurry spoofs.
 */
public final class LivenessDetector {
    private static final double MIN_VARIANCE_THRESHOLD = 50.0;   // Below this: blurry photo / paper print attack
    private static final double MAX_VARIANCE_THRESHOLD = 1500.0; // Above this: digital screen specular reflection moire

    /** Evaluates the cropped face region for live human skin micro-texture. */
    public static LivenessResult checkLiveness(Mat grayFace) {
        if (grayFace == null || grayFace.empty() || grayFace.cols() < 20 || grayFace.rows() < 20) {
            return new LivenessResult(false, 0.0, "Face region too small for liveness check.");
        }

        Mat laplacian = new Mat();
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();

        try {
            // Compute Laplacian of the grayscale face to measure edge gradient energy
            Imgproc.Laplacian(grayFace, laplacian, CvType.CV_64F);
            org.opencv.core.Core.meanStdDev(laplacian, mean, stddev);

            double std = stddev.get(0, 0)[0];
            double variance = std * std; // Variance of Laplacian (focus measure)

            if (variance < MIN_VARIANCE_THRESHOLD) {
                return new LivenessResult(false, variance,
                        String.format("⚠ Spoof Detected: Low micro-texture variance (%.1f). Avoid static/printed photos.", variance));
            } else if (variance > MAX_VARIANCE_THRESHOLD) {
                return new LivenessResult(false, variance,
                        String.format("⚠ Spoof Detected: Extreme digital screen glare/moiré (%.1f).", variance));
            } else {
                return new LivenessResult(true, variance,
                        String.format("✔ Live Human Face Verified (Texture Variance: %.1f)", variance));
            }
        } finally {
            laplacian.release();
            mean.release();
            stddev.release();
        }
    }

    public record LivenessResult(boolean isLive, double varianceScore, String message) { }
}
