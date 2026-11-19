package facetrace.vision;

import javafx.application.Platform;
import javafx.scene.image.Image;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Manages real-time live webcam capture and on-screen face tracking using OpenCV. */
public final class WebcamService {
    private VideoCapture capture;
    private ScheduledExecutorService executor;
    private CascadeClassifier faceClassifier;
    private volatile boolean isRunning = false;
    private volatile Mat lastCapturedFrame;
    private volatile Consumer<Image> currentCallback;

    public WebcamService() {
        OpenCV.loadLocally();
        initClassifier();
    }

    private void initClassifier() {
        try {
            Path cascadePath = Files.createTempFile("facetrace-haar-", ".xml");
            try (InputStream input = WebcamService.class.getResourceAsStream("/haarcascade_frontalface_default.xml")) {
                if (input != null) {
                    Files.copy(input, cascadePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    cascadePath.toFile().deleteOnExit();
                    faceClassifier = new CascadeClassifier(cascadePath.toString());
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** Changes where the live camera frames are rendered (e.g. Check-in preview vs Registration preview). */
    public void setFrameCallback(Consumer<Image> callback) {
        this.currentCallback = callback;
    }

    /** Starts webcam capture on the default camera (index 0) and streams frames at ~30 FPS. */
    public boolean startCamera(Consumer<Image> onFrameRendered, Consumer<String> onError) {
        this.currentCallback = onFrameRendered;
        if (isRunning) return true;

        capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            onError.accept("Unable to access local webcam (Device 0). Check camera permissions or connection.");
            capture.release();
            return false;
        }

        isRunning = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "facetrace-webcam-stream");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> {
            if (!isRunning || !capture.isOpened()) return;

            Mat rawFrame = new Mat();
            try {
                if (capture.read(rawFrame) && !rawFrame.empty()) {
                    synchronized (this) {
                        if (lastCapturedFrame != null) {
                            lastCapturedFrame.release();
                        }
                        lastCapturedFrame = rawFrame.clone();
                    }

                    Mat annotated = rawFrame.clone();
                    try {
                        detectAndAnnotate(annotated);
                        Image fxImage = matToImage(annotated);
                        Consumer<Image> cb = currentCallback;
                        if (fxImage != null && cb != null) {
                            Platform.runLater(() -> cb.accept(fxImage));
                        }
                    } finally {
                        annotated.release();
                    }
                }
            } catch (Exception ex) {
                // Ignore transient frame errors
            } finally {
                rawFrame.release();
            }
        }, 0, 33, TimeUnit.MILLISECONDS);

        return true;
    }

    /** Draws green face detection bounding boxes on the video frame. */
    private void detectAndAnnotate(Mat frame) {
        if (faceClassifier == null || faceClassifier.empty()) return;

        Mat gray = new Mat();
        MatOfRect faces = new MatOfRect();
        try {
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);
            faceClassifier.detectMultiScale(gray, faces, 1.15, 4, 0, new Size(40, 40), new Size());
            for (Rect rect : faces.toArray()) {
                Imgproc.rectangle(frame, rect, new Scalar(16, 185, 129), 2); // Emerald green box
            }
        } finally {
            gray.release();
            faces.release();
        }
    }

    /** Returns the most recent webcam snapshot for attendance processing or enrollment. */
    public synchronized Mat getLastFrameCopy() {
        if (lastCapturedFrame == null || lastCapturedFrame.empty()) {
            return null;
        }
        return lastCapturedFrame.clone();
    }

    /** Converts an OpenCV Mat into a JavaFX Image for UI rendering. */
    public static Image matToImage(Mat mat) {
        if (mat == null || mat.empty()) return null;
        MatOfByte byteMat = new MatOfByte();
        try {
            if (Imgcodecs.imencode(".png", mat, byteMat)) {
                return new Image(new ByteArrayInputStream(byteMat.toArray()));
            }
        } finally {
            byteMat.release();
        }
        return null;
    }

    /** Stops webcam streaming and releases device resources. */
    public void stopCamera() {
        isRunning = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        if (capture != null && capture.isOpened()) {
            capture.release();
        }
        synchronized (this) {
            if (lastCapturedFrame != null) {
                lastCapturedFrame.release();
                lastCapturedFrame = null;
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
