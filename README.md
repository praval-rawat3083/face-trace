# FaceTrace — Smart Attendance & Biometric Management System

FaceTrace is an edge-deployable, enterprise-grade biometric attendance system built with Java 17, OpenCV, and SQLite. It combines **Spatial 4x4 Grid Local Binary Patterns (LBP)** and **Cosine Similarity** to perform real-time face verification in **~2.26 ms** with **96.00% benchmarked accuracy**, equipped with passive **Anti-Spoofing (Liveness Detection)**, **Lecture / Subject Tracking**, and a **Student Self-Registration Kiosk**.

---

## 4-Member Project Architecture & Division of Work

Designed specifically as a comprehensive capstone/final-year group project:

| Member | Subsystem / Role | Key Modules & Implementation |
|---|---|---|
| **Member 1** | **Computer Vision & Security** | • OpenCV real-time webcam frame acquisition at 30 FPS (`WebcamService`).<br>• Spatial $4 \times 4$ Grid LBP texture extraction (4,096-dim vector) & Cosine matcher.<br>• Passive Anti-Spoofing & Liveness detection (`LivenessDetector`) using Laplacian micro-texture variance analysis. |
| **Member 2** | **Kiosk UI & Self-Registration** | • JavaFX Kiosk interface (`SearchView.fxml`, `search.css`).<br>• Student Self-Registration portal with webcam snapshot & auto-enrollment (`StudentEnrollmentService`).<br>• Real-time confidence percentage formatting & color-coded status badges. |
| **Member 3** | **Analytics & Compliance Dashboard** | • Admin/Faculty portal (`AdminView.fxml`, `admin.css`).<br>• Attendance Analytics KPI cards: Total Students, Total Sessions, Defaulters Count.<br>• Automatic **75% Attendance Defaulter Filter** & 1-click **Export to CSV/Excel**. |
| **Member 4** | **Data Architecture & System Integration** | • SQLite embedded database engine (`attendance.db`) with zero external configuration.<br>• Lecture / Subject-wise timetable tracking (`Data Structures`, `OS`, `Networks`, etc.).<br>• BCrypt salted password hashing and administrative audit trails (`audit_log`). |

---

## Key Features

1. **Dual Kiosk Modes:**
   * **Attendance Check-in Tab:** Real-time webcam streaming with green bounding box tracking, subject/lecture dropdown, calibrated cutoff threshold (default 70% / 0.70), and match percentage output.
   * **Student Self-Registration Tab:** Enables students to enroll themselves directly on the kiosk by capturing a webcam photo and entering Roll Number, Name, Semester, and Department.
2. **Passive Anti-Spoofing & Liveness Verification:**
   * Analyzes high-frequency Laplacian variance on the cropped face.
   * Automatically detects and flags printed paper photo attacks and low-resolution smartphone screen replays (`⚠ Spoof Detected`).
3. **Lecture / Subject-Wise Attendance:**
   * Attendance logs are tagged with the specific subject/period being conducted (`Data Structures`, `Operating Systems`, `Database Systems`, etc.).
4. **Attendance Analytics & Defaulters List (< 75%):**
   * Computes per-student attendance rates across the semester.
   * 1-click toggle filter to isolate and export all students with attendance **below 75%**.
5. **1-Click Excel / CSV Export:**
   * Exports timestamped, filtered attendance sheets directly into `.csv` files ready for Microsoft Excel or Google Sheets.
6. **Zero-Setup Embedded Database:**
   * Runs natively on **SQLite (`attendance.db`)** without needing MySQL Server or external cloud services.

---

## Technology Stack

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| **Programming Language** | Java SE | 17 (LTS) / 22 | Core business logic, multithreading, and vision orchestration |
| **Build System** | Apache Maven | 3.8+ | Dependency management and build packaging |
| **Computer Vision** | OpenCV (`nu.pattern.opencv`) | 4.9.0 | Grayscale conversion, contrast equalization, Haar face detection, video capture |
| **GUI Framework** | JavaFX + FXML + CSS | 17.0.10 | Modern responsive kiosk and administration views |
| **Relational Database** | SQLite (`sqlite-jdbc`) | 3.45.1.0 | Embedded relational database (`attendance.db`) with zero configuration |
| **Security & Auth** | jBCrypt | 0.4 | Salted Blowfish hashing for administrative access governance |
| **Testing** | JUnit Jupiter | 5.10.2 | Unit testing and algorithmic verification |

---

## How to Run

### Method 1: Easiest (Double-Click)
Double-click the **`run_app.bat`** file in the project folder.

### Method 2: Command Line
```cmd
mvn compile javafx:run
```

### Admin Credentials
* **Username:** `admin`
* **Password:** `admin123`
