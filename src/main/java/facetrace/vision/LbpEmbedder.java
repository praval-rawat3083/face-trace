package facetrace.vision;

import java.awt.image.BufferedImage;

/** Builds a face feature vector using only Java's built-in image tools. */
public final class LbpEmbedder implements FaceEmbedder {
    private static final int GRID_SIZE = 4;
    private static final int PATTERN_COUNT = 256;
    private static final int[] NEIGHBOR_X = {-1, 0, 1, 1, 1, 0, -1, -1};
    private static final int[] NEIGHBOR_Y = {-1, -1, -1, 0, 1, 1, 1, 0};

    /** Compares each pixel with its eight neighbors and returns a normalized grid histogram. */
    @Override
    public float[] embed(BufferedImage faceImage) {
        if (faceImage == null || faceImage.getWidth() < 3 || faceImage.getHeight() < 3) {
            throw new IllegalArgumentException("The face image must be at least 3 by 3 pixels");
        }

        float[] features = new float[GRID_SIZE * GRID_SIZE * PATTERN_COUNT];
        int[] samplesPerCell = new int[GRID_SIZE * GRID_SIZE];

        // LBP gives a pixel a small number by checking whether its neighbors are brighter or darker.
        for (int y = 1; y < faceImage.getHeight() - 1; y++) {
            for (int x = 1; x < faceImage.getWidth() - 1; x++) {
                int pattern = patternAt(faceImage, x, y);
                // Count this 0-to-255 pattern in the pixel's square of the 4-by-4 grid.
                int cellX = x * GRID_SIZE / faceImage.getWidth();
                int cellY = y * GRID_SIZE / faceImage.getHeight();
                int cell = cellY * GRID_SIZE + cellX;
                features[cell * PATTERN_COUNT + pattern]++;
                samplesPerCell[cell]++;
            }
        }

        // Each cell is divided by its own sample count so large cells do not dominate the vector.
        for (int cell = 0; cell < samplesPerCell.length; cell++) {
            if (samplesPerCell[cell] == 0) {
                continue;
            }
            int firstBin = cell * PATTERN_COUNT;
            for (int bin = 0; bin < PATTERN_COUNT; bin++) {
                features[firstBin + bin] /= samplesPerCell[cell];
            }
        }
        return features;
    }

    /** Makes one eight-bit pattern by comparing the center pixel to each neighbor. */
    private int patternAt(BufferedImage image, int x, int y) {
        int center = grayAt(image, x, y);
        int pattern = 0;
        for (int bit = 0; bit < NEIGHBOR_X.length; bit++) {
            int neighbor = grayAt(image, x + NEIGHBOR_X[bit], y + NEIGHBOR_Y[bit]);
            // A 1 means the neighbor is the same brightness or brighter; a 0 means darker.
            if (neighbor >= center) {
                pattern |= 1 << (7 - bit);
            }
        }
        return pattern;
    }

    /** Converts one color pixel to a gray brightness value using a simple weighted average. */
    private int grayAt(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return (red * 299 + green * 587 + blue * 114 + 500) / 1000;
    }
}
