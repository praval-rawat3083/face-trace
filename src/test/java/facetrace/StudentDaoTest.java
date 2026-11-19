package facetrace;

import facetrace.dao.StudentDao;
import facetrace.model.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class StudentDaoTest {
    private Connection connection;
    private StudentDao dao;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE students (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "student_id VARCHAR(50) UNIQUE NOT NULL, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "age INT NOT NULL, " +
                    "department VARCHAR(120) NOT NULL, " +
                    "photo_path VARCHAR(500) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
        dao = new StudentDao(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testAddAndGetStudent() throws SQLException {
        Student student = new Student("STU-101", "Test Student", 21, "Computer Science", "/photos/s1.jpg");
        Student saved = dao.add(student);
        assertTrue(saved.getId() > 0);

        Optional<Student> fetched = dao.getById(saved.getId());
        assertTrue(fetched.isPresent());
        assertEquals("STU-101", fetched.get().getStudentId());
        assertEquals("Test Student", fetched.get().getFullName());
    }

    @Test
    void testGetAllStudents() throws SQLException {
        dao.add(new Student("STU-101", "Student 1", 20, "CS", "/p1.jpg"));
        dao.add(new Student("STU-102", "Student 2", 21, "IT", "/p2.jpg"));

        List<Student> list = dao.getAll();
        assertEquals(2, list.size());
    }
}
