package facetrace.match;

/** Holds a case code and the similarity score actually calculated for it. */
public record MatchResult(String caseCode, double similarity) {
}
