package facetrace.model;

/** Safe identity details kept only while an admin is signed in. */
public record AdminSession(long id, String username, String role) { }
