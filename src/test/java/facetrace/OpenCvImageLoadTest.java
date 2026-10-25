package facetrace;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** This test checks that OpenCV can open an image; it uses a made-up color picture, not a person. */
class OpenCvImageLoadTest {

    /** Make a small temporary image and ask OpenCV to load it, so no private photo is needed. */
    @Test
    void loadsImageWithOpenCv() throws IOException {
        OpenCV.loadLocally();
        Path imagePath = Files.createTempFile("facetrace-test-", ".png");
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            image.setRGB(3, 3, 0x336699);
            ImageIO.write(image, "png", imagePath.toFile());

            Mat loadedImage = Imgcodecs.imread(imagePath.toString());
            try {
                assertNotNull(loadedImage);
                assertFalse(loadedImage.empty(), "OpenCV should load the generated image");
            } finally {
                loadedImage.release();
            }
        } finally {
            Files.deleteIfExists(imagePath);
        }
    }
}
