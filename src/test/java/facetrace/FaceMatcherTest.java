package facetrace;

import facetrace.match.FaceMatcher;
import facetrace.match.MatchResult;
import facetrace.vision.LbpEmbedder;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks LBP matching with generated test images, not photos of real people. */
class FaceMatcherTest {
    /** Checks two reads of the same picture receive a score very close to one. */
    @Test
    void sameImageGetsNearOneScore() throws IOException {
        LbpEmbedder embedder = new LbpEmbedder();
        float[] probe = embedder.embed(loadImage("/facetrace/synthetic-single.jpg"));
        float[] stored = embedder.embed(loadImage("/facetrace/synthetic-single.jpg"));
        List<MatchResult> results = new FaceMatcher(0.0)
                .rankTopK(probe, Map.of("SYNTHETIC-SINGLE", stored), 1);
        assertEquals(1, results.size());
        assertTrue(results.get(0).similarity() > 0.999);
    }

    /** Checks a different generated picture scores below the same-picture match. */
    @Test
    void differentImageGetsLowerScore() throws IOException {
        LbpEmbedder embedder = new LbpEmbedder();
        float[] probe = embedder.embed(loadImage("/facetrace/synthetic-single.jpg"));
        float[] same = embedder.embed(loadImage("/facetrace/synthetic-single.jpg"));
        float[] different = embedder.embed(loadImage("/facetrace/synthetic-pair.jpg"));
        List<MatchResult> results = new FaceMatcher(0.0).rankTopK(probe,
                Map.of("SYNTHETIC-SINGLE", same, "SYNTHETIC-PAIR", different), 2);
        assertEquals(2, results.size());
        assertEquals("SYNTHETIC-SINGLE", results.get(0).caseCode());
        assertTrue(results.get(0).similarity() > results.get(1).similarity());
    }

    /** Checks that the threshold removes weak scores and topK limits the ranked list. */
    @Test
    void thresholdAndTopKAreApplied() {
        FaceMatcher matcher = new FaceMatcher(0.99);
        Map<String, float[]> gallery = Map.of(
                "BEST", new float[]{1.0f, 0.0f},
                "CLOSE", new float[]{0.9f, 0.1f},
                "DIFFERENT", new float[]{0.0f, 1.0f});
        List<MatchResult> filtered = matcher.rankTopK(new float[]{1.0f, 0.0f}, gallery, 3);
        assertEquals(2, filtered.size());
        assertEquals("BEST", filtered.get(0).caseCode());
        List<MatchResult> topOne = matcher.rankTopK(new float[]{1.0f, 0.0f}, gallery, 1);
        assertEquals(1, topOne.size());
        assertEquals("BEST", topOne.get(0).caseCode());
    }

    /** Loads a bundled generated test image and fails clearly if it is missing. */
    private BufferedImage loadImage(String resourceName) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Missing generated test image: " + resourceName);
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Could not read generated test image: " + resourceName);
            }
            return image;
        }
    }
}
