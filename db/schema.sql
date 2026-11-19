-- ====================================================================
-- FaceTrace - Smart Attendance Management System
-- Database Schema Reset & Initialization
-- ====================================================================

-- 1. Clean up any old tables (including legacy criminal detection tables)
DROP TABLE IF EXISTS attendance_logs;
DROP TABLE IF EXISTS match_logs;
DROP TABLE IF EXISTS face_embeddings;
DROP TABLE IF EXISTS criminal_records;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS admins;

-- 2. Enrolled Students Directory
CREATE TABLE students (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(50) NOT NULL UNIQUE,       -- Roll Number / Student ID (e.g. STU-001)
    full_name VARCHAR(100) NOT NULL,              -- Full Name
    age INT NOT NULL,                             -- Age or Semester
    department VARCHAR(120) NOT NULL,             -- Department / Branch
    photo_path VARCHAR(500) NOT NULL,             -- Path to enrolled profile photo
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Biometric LBP Face Embeddings (Irreversible 4,096-dim float vectors)
CREATE TABLE face_embeddings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    vector BLOB NOT NULL,                         -- 4,096 float binary vector (16,384 bytes)
    embedder_name VARCHAR(100) NOT NULL,          -- "LBP-4x4-256-v1"
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_face_embeddings_student
        FOREIGN KEY (record_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Attendance Verification Logs
CREATE TABLE attendance_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    probe_file VARCHAR(500) NOT NULL,             -- Source image or "WEBCAM_LIVE_CAPTURE"
    matched_student_id BIGINT NULL,               -- Enrolled student ID if verified
    score DOUBLE NOT NULL,                        -- Cosine similarity score (0.0 to 1.0)
    status VARCHAR(50) NOT NULL DEFAULT 'PRESENT', -- 'PRESENT' or 'UNRECOGNIZED'
    check_in_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attendance_student_time (matched_student_id, check_in_time),
    CONSTRAINT fk_attendance_student
        FOREIGN KEY (matched_student_id) REFERENCES students(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Administrator Accounts (Salted BCrypt Security)
CREATE TABLE admins (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash CHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Immutable Administrative Audit Trail
CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,                  -- 'STUDENT_ENROLL', 'STUDENT_DELETE', 'ADMIN_LOGIN'
    entity_type VARCHAR(50) NOT NULL,             -- 'STUDENT', 'ADMIN'
    entity_id BIGINT NULL,
    details TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_log_created_at (created_at),
    CONSTRAINT fk_audit_log_admin
        FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
