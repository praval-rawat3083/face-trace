package facetrace.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compares feature vectors, filters by a chosen threshold, and ranks the best candidates. */
public final class FaceMatcher {
    private final double threshold;

    /** Sets the smallest cosine score that is allowed into the results, from zero to one. */
    public FaceMatcher(double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("The threshold must be between 0 and 1");
        }
        this.threshold = threshold;
    }

    /** Returns at most topK qualifying candidates, highest similarity first. */
    public List<MatchResult> rankTopK(float[] probe, Map<String, float[]> gallery, int topK) {
        Objects.requireNonNull(probe, "The probe vector is required");
        Objects.requireNonNull(gallery, "The gallery is required");
        if (probe.length == 0 || topK < 1) {
            throw new IllegalArgumentException("The probe must have values and topK must be at least 1");
        }

        List<MatchResult> results = new ArrayList<>();
        for (Map.Entry<String, float[]> entry : gallery.entrySet()) {
            String caseCode = entry.getKey();
            float[] candidate = entry.getValue();
            if (caseCode == null || caseCode.isBlank() || candidate == null || candidate.length != probe.length) {
                throw new IllegalArgumentException("Every candidate needs a case code and a matching vector size");
            }
            double score = cosineSimilarity(probe, candidate);
            if (score >= threshold) {
                results.add(new MatchResult(caseCode, score));
            }
        }

        results.sort(Comparator.comparingDouble(MatchResult::similarity).reversed()
                .thenComparing(MatchResult::caseCode));
        return List.copyOf(results.subList(0, Math.min(topK, results.size())));
    }

    /** Calculates how much two vectors point in the same direction. */
    private double cosineSimilarity(float[] left, float[] right) {
        double dot = 0.0;
        double leftSquare = 0.0;
        double rightSquare = 0.0;
        for (int i = 0; i < left.length; i++) {
            if (!Float.isFinite(left[i]) || !Float.isFinite(right[i])) {
                throw new IllegalArgumentException("Feature vectors must contain finite numbers");
            }
            dot += left[i] * right[i];
            leftSquare += left[i] * left[i];
            rightSquare += right[i] * right[i];
        }
        if (leftSquare == 0.0 || rightSquare == 0.0) {
            return 0.0;
        }
        double score = dot / (Math.sqrt(leftSquare) * Math.sqrt(rightSquare));
        return Math.max(-1.0, Math.min(1.0, score));
    }
}
