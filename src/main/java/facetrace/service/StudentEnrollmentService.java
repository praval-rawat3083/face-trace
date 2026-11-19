package facetrace.service;

import facetrace.config.DatabaseConnection;
import facetrace.dao.AdminEmbeddingDao;
import facetrace.dao.StudentDao;
import facetrace.model.Student;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/** Enables self-service student registration directly from the kiosk webcam or image file. */
public final class StudentEnrollmentService {
    private final AdminPhotoEnrollment photoEnrollment = new AdminPhotoEnrollment();

    public Student enroll(String studentId, String fullName, int age, String department, Path photoPath)
            throws IOException, SQLException {
        validate(studentId, fullName, age, department);

        if (photoPath == null || !Files.isRegularFile(photoPath)) {
            throw new IOException("A clear student face photo is required.");
        }

        String cleanId = studentId.trim().toUpperCase(Locale.ROOT);
        float[] vector = photoEnrollment.embedOneFace(photoPath);

        try (Connection conn = DatabaseConnection.getConnection()) {
            StudentDao dao = new StudentDao(conn);
            if (dao.getByStudentId(cleanId).isPresent()) {
                throw new IllegalArgumentException("Student ID '" + cleanId + "' is already registered.");
            }

            Student student = new Student(cleanId, fullName.trim(), age, department.trim(), photoPath.toAbsolutePath().toString());
            Student saved = dao.add(student);
            new AdminEmbeddingDao(conn).insert(saved.getId(), vector);
            return saved;
        }
    }

    public Student enrollFromCameraFrame(String studentId, String fullName, int age, String department, Mat frame)
            throws IOException, SQLException {
        if (frame == null || frame.empty()) {
            throw new IOException("Camera frame was empty. Ensure webcam is running.");
        }

        Path saveDir = Path.of("enrolled_photos");
        if (!Files.exists(saveDir)) {
            Files.createDirectories(saveDir);
        }

        String cleanId = studentId.trim().toUpperCase(Locale.ROOT);
        Path targetPath = saveDir.resolve(cleanId + "_" + System.currentTimeMillis() + ".png").toAbsolutePath();
        Imgcodecs.imwrite(targetPath.toString(), frame);

        return enroll(studentId, fullName, age, department, targetPath);
    }

    private void validate(String studentId, String fullName, int age, String department) {
        if (studentId == null || studentId.trim().length() < 2 || studentId.trim().length() > 50) {
            throw new IllegalArgumentException("Student ID / Roll No must be between 2 and 50 characters.");
        }
        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > 100) {
            throw new IllegalArgumentException("Full Name must be between 2 and 100 characters.");
        }
        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("Enter a valid age or semester number (1 to 120).");
        }
        if (department == null || department.isBlank() || department.trim().length() > 120) {
            throw new IllegalArgumentException("Department / Branch is required.");
        }
    }
}
