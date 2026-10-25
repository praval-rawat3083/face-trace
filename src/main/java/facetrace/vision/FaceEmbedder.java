package facetrace.vision;

import java.awt.image.BufferedImage;

/** Turns a face image into numbers that can be compared with another face image. */
public interface FaceEmbedder {
    /** Builds a feature vector from the supplied image. */
    float[] embed(BufferedImage faceImage);
}
