package facetrace;

import facetrace.config.DatabaseConnection;
import facetrace.dao.CriminalDao;
import facetrace.model.CriminalRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks each DAO operation against MySQL's separate facetrace_test database with synthetic rows. */
class CriminalDaoTest {
    private CriminalDao dao;
    private final List<Long> addedIds = new ArrayList<>();

    /** Opens the test-only database before each test, never the normal project database. */
    @BeforeEach
    void connectToTestDatabase() throws IOException, SQLException {
        dao = new CriminalDao(DatabaseConnection.getTestConnection());
        addedIds.clear();
    }

    /** Removes this test's rows and closes its database connection after every test. */
    @AfterEach
    void removeTestRows() throws SQLException {
        if (dao == null) {
            return;
        }
        try {
            for (long id : addedIds) {
                dao.delete(id);
            }
        } finally {
            dao.close();
        }
    }

    /** Checks that add saves a row and returns the id assigned by MySQL. */
    @Test
    void addsRecord() throws SQLException {
        CriminalRecord saved = addSample();
        assertTrue(saved.getId() > 0);
        assertEquals("Subject-001", saved.getFullName());
    }

    /** Checks that getById returns the saved synthetic subject. */
    @Test
    void getsRecordById() throws SQLException {
        CriminalRecord saved = addSample();
        CriminalRecord found = dao.getById(saved.getId()).orElseThrow();
        assertEquals(saved.getCaseCode(), found.getCaseCode());
        assertEquals("Subject-001", found.getFullName());
    }

    /** Checks that getAll includes records created for this test. */
    @Test
    void getsAllRecords() throws SQLException {
        CriminalRecord first = addSample();
        CriminalRecord second = addSample();
        List<Long> ids = dao.getAll().stream().map(CriminalRecord::getId).toList();
        assertTrue(ids.contains(first.getId()));
        assertTrue(ids.contains(second.getId()));
    }

    /** Checks that update changes the stored values for one synthetic subject. */
    @Test
    void updatesRecord() throws SQLException {
        CriminalRecord saved = addSample();
        CriminalRecord changed = new CriminalRecord(saved.getId(), saved.getCaseCode(), "Subject-002",
                24, "Synthetic demo data - test only", saved.getPhotoPath());
        assertTrue(dao.update(changed));
        assertEquals("Subject-002", dao.getById(saved.getId()).orElseThrow().getFullName());
    }

    /** Checks that delete removes a row and later lookup finds nothing. */
    @Test
    void deletesRecord() throws SQLException {
        CriminalRecord saved = addSample();
        assertTrue(dao.delete(saved.getId()));
        assertFalse(dao.getById(saved.getId()).isPresent());
    }

    /** Inserts one uniquely named synthetic test row and remembers it for cleanup. */
    private CriminalRecord addSample() throws SQLException {
        String caseCode = "TEST-DAO-" + UUID.randomUUID();
        CriminalRecord saved = dao.add(new CriminalRecord(caseCode, "Subject-001", 21,
                "Synthetic demo data - test only", "synthetic-test/no-photo"));
        addedIds.add(saved.getId());
        return saved;
    }
}
