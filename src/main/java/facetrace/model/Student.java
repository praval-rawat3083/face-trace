package facetrace.model;

/** Holds one enrolled student profile for the Smart Attendance System. */
public final class Student {
    private final long id;
    private final String studentId;
    private final String fullName;
    private final int age;
    private final String department;
    private final String photoPath;

    /** Creates a new student profile before database insertion. */
    public Student(String studentId, String fullName, int age, String department, String photoPath) {
        this(0, studentId, fullName, age, department, photoPath);
    }

    /** Creates a student record with database ID. */
    public Student(long id, String studentId, String fullName, int age, String department, String photoPath) {
        this.id = id;
        this.studentId = studentId;
        this.fullName = fullName;
        this.age = age;
        this.department = department;
        this.photoPath = photoPath;
    }

    /** Returns a copy with the generated database id. */
    public Student withId(long newId) {
        return new Student(newId, studentId, fullName, age, department, photoPath);
    }

    public long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getFullName() { return fullName; }
    public int getAge() { return age; }
    public String getDepartment() { return department; }
    public String getPhotoPath() { return photoPath; }

    // Compatibility getters
    public String getCaseCode() { return studentId; }
    public String getOffenceType() { return department; }
}
