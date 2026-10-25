package facetrace.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks the PGM reader using a tiny generated image, not a real person's photo. */
class Phase2DemoTest {
    @TempDir
    Path tempDir;

    /** Checks that the binary P5 pixel bytes are read into the matching gray pixel positions. */
    @Test
    void readsBinaryPgmPixels() throws Exception {
        byte[] header = "P5\n# generated test image\n3 3\n255\n".getBytes(StandardCharsets.US_ASCII);
        byte[] pixels = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        byte[] pgm = new byte[header.length + pixels.length];
        System.arraycopy(header, 0, pgm, 0, header.length);
        System.arraycopy(pixels, 0, pgm, header.length, pixels.length);
        Path path = tempDir.resolve("tiny-test.pgm");
        Files.write(path, pgm);

        BufferedImage image = Phase2Demo.readPgm(path);
        assertEquals(3, image.getWidth());
        assertEquals(3, image.getHeight());
        assertEquals(4, image.getRaster().getSample(1, 1, 0));
    }
}
