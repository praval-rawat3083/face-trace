package facetrace.service;

import facetrace.model.AdminSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleCheckerTest {
    private final RoleChecker checker = new RoleChecker();

    @Test
    void allowsAdminRole() {
        assertDoesNotThrow(() -> checker.requireAdmin(new AdminSession(1, "demo-admin", "ADMIN")));
    }

    @Test
    void blocksOtherRolesAndMissingSessions() {
        assertThrows(SecurityException.class,
                () -> checker.requireAdmin(new AdminSession(2, "demo-reviewer", "REVIEWER")));
        assertThrows(SecurityException.class, () -> checker.requireAdmin(null));
    }
}
