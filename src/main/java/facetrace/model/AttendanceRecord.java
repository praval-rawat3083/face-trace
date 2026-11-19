package facetrace.model;

import java.sql.Timestamp;

/** Represents one daily attendance verification log entry. */
public final class AttendanceRecord {
    private final long id;
    private final String studentId;
    private final String studentName;
    private final String department;
    private final double matchScore;
    private final String status;
    private final Timestamp checkInTime;

    public AttendanceRecord(long id, String studentId, String studentName, String department,
                            double matchScore, String status, Timestamp checkInTime) {
        this.id = id;
        this.studentId = studentId;
        this.studentName = studentName;
        this.department = department;
        this.matchScore = matchScore;
        this.status = status;
        this.checkInTime = checkInTime;
    }

    public long getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getDepartment() { return department; }
    public double getMatchScore() { return matchScore; }
    public String getMatchPercentage() { return String.format("%.1f%%", matchScore * 100); }
    public String getStatus() { return status; }
    public Timestamp getCheckInTime() { return checkInTime; }
}
