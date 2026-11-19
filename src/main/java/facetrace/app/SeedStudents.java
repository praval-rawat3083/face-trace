package facetrace.app;

import facetrace.config.DatabaseConnection;
import facetrace.dao.AdminEmbeddingDao;
import facetrace.dao.StudentDao;
import facetrace.model.Student;
import facetrace.service.AdminPhotoEnrollment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/** Seeds enrolled student profiles and their 4,096-dim LBP vectors from att_faces into MySQL. */
public final class SeedStudents {
    private static final String[] DEPARTMENTS = {
            "BCA (General)", "BCA (Cloud Computing)", "BCA (Data Analytics)", "BCA (Cyber Security)", "BCA (AI & ML)"
    };

    public static void main(String[] args) throws Exception {
        Path datasetDir = args.length > 0 ? Path.of(args[0]) : Path.of("att_faces");
        if (!Files.isDirectory(datasetDir)) {
            System.err.println("Dataset directory not found: " + datasetDir.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("Starting student enrollment from " + datasetDir.toAbsolutePath() + "...");
        AdminPhotoEnrollment embedder = new AdminPhotoEnrollment();

        try (Connection connection = DatabaseConnection.getConnection()) {
            StudentDao studentDao = new StudentDao(connection);
            AdminEmbeddingDao embeddingDao = new AdminEmbeddingDao(connection);

            int enrolledCount = 0;
            for (int i = 1; i <= 40; i++) {
                String studentCode = String.format("STU-%03d", i);
                String fullName = String.format("Student %02d", i);
                String department = DEPARTMENTS[i % DEPARTMENTS.length];
                int age = 19 + (i % 4);

                Path photoPath = datasetDir.resolve("s" + i).resolve("1.pgm");
                if (!Files.isRegularFile(photoPath)) {
                    continue;
                }

                // Check if already enrolled
                boolean exists = false;
                for (Student existing : studentDao.getAll()) {
                    if (studentCode.equalsIgnoreCase(existing.getStudentId())) {
                        exists = true;
                        break;
                    }
                }
                if (exists) {
                    System.out.println("Skipping already enrolled: " + studentCode);
                    continue;
                }

                try {
                    float[] vector = embedder.embedOneFace(photoPath);
                    Student saved = studentDao.add(new Student(
                            studentCode, fullName, age, department, photoPath.toAbsolutePath().toString()
                    ));
                    embeddingDao.insert(saved.getId(), vector);
                    enrolledCount++;
                    System.out.printf("Enrolled %s (%s, %s)\n", studentCode, fullName, department);
                } catch (Exception ex) {
                    System.err.println("Failed to enroll " + studentCode + ": " + ex.getMessage());
                }
            }
            System.out.printf("\nCompleted! Successfully enrolled %d students into MySQL database.\n", enrolledCount);
        }
    }
}
