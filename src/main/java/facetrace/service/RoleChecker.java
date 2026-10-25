package facetrace.service;

import facetrace.model.AdminSession;

/** Keeps admin-only screens restricted to a verified ADMIN session. */
public final class RoleChecker {
    /** Stops the caller when there is no signed-in admin account. */
    public void requireAdmin(AdminSession session) {
        if (session == null || !"ADMIN".equals(session.role())) {
            throw new SecurityException("Administrator access is required.");
        }
    }
}
