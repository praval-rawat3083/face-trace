package facetrace.vision;

import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.nio.file.Path;

/** Runs face detection from the console and saves a boxed copy beside the input image. */
public final class FaceDetectorRunner {
    /** Keeps this utility class from being created as an object. */
    private FaceDetectorRunner() { }

    /** Takes an input path and an optional output path, then saves the detector's annotated copy. */
    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: FaceDetectorRunner <image-path> [output-path]");
            return;
        }

        Path inputPath = Path.of(args[0]).toAbsolutePath().normalize();
        String inputName = inputPath.getFileName().toString();
        int dot = inputName.lastIndexOf('.');
        String baseName = dot > 0 ? inputName.substring(0, dot) : inputName;
        Path outputPath = args.length == 2 ? Path.of(args[1]).toAbsolutePath().normalize()
                : inputPath.resolveSibling(baseName + "-faces.png");
        if (inputPath.equals(outputPath)) {
            throw new IOException("Choose an output path different from the original image");
        }

        FaceDetector detector = new FaceDetector();
        try (FaceDetector.DetectionResult result = detector.detect(inputPath)) {
            if (result.status() == FaceDetector.Status.IMAGE_NOT_READ) {
                System.err.println("Could not open the input image: " + inputPath);
                return;
            }
            if (!Imgcodecs.imwrite(outputPath.toString(), result.annotatedImage())) {
                throw new IOException("Could not save the result image: " + outputPath);
            }
            System.out.println("Result: " + result.status() + "; faces found: " + result.faces().size());
            System.out.println("Saved boxed copy to: " + outputPath);
        }
    }
}
