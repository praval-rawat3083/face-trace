package facetrace.controller;

import facetrace.config.DatabaseConnection;
import facetrace.model.Student;
import facetrace.service.AdminRecordService;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

/** Handles student enrollment actions while delegating database work to services and tasks. */
final class AdminRecordActions {
    private final AdminController ui;
    private final AdminRecordTableSupport table;
    private final AdminRecordService service;
    private final AdminTaskRunner tasks;
    private Path selectedPhoto;

    AdminRecordActions(AdminController ui, AdminRecordTableSupport table, AdminRecordService service) {
        this.ui = ui;
        this.table = table;
        this.service = service;
        tasks = new AdminTaskRunner(ui, table);
    }

    void choosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose student enrollment photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.pgm"));
        File selected = chooser.showOpenDialog(ui.choosePhotoButton.getScene().getWindow());
        if (selected != null) {
            selectedPhoto = selected.toPath().toAbsolutePath().normalize();
            ui.photoPathLabel.setText(selected.getName());
            ui.statusLabel.setText("Selected photo: " + selected.getName());
        }
    }

    void addRecord() {
        Integer age = readAge();
        if (age == null) return;
        if (selectedPhoto == null) {
            ui.statusLabel.setText("Choose one photo containing exactly one detectable face.");
            return;
        }
        String studentId = ui.caseCodeField.getText();
        String name = ui.fullNameField.getText();
        String department = ui.offenceTypeField.getText();
        boolean confirmed = ui.syntheticConfirmCheckBox.isSelected();
        Task<Student> task = new Task<>() {
            @Override
            protected Student call() throws Exception {
                return service.add(table.admin(), studentId, name, age, department, selectedPhoto, confirmed);
            }
        };
        tasks.runAction(task, "Student profile and LBP vector saved; enrollment was audited.");
    }

    void updateRecord() {
        Student selected = table.selectedRecord();
        Integer age = readAge();
        if (selected == null || age == null) return;
        Student changed = new Student(selected.getId(), ui.caseCodeField.getText(),
                ui.fullNameField.getText(), age, ui.offenceTypeField.getText(), selected.getPhotoPath());
        boolean confirmed = ui.syntheticConfirmCheckBox.isSelected();
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return service.update(table.admin(), changed, confirmed);
            }
        };
        tasks.runAction(task, "Student profile & branch updated; the edit action was audited.");
    }

    void deleteRecord() {
        Student selected = table.selectedRecord();
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete student record?");
        confirm.setContentText(selected.getFullName() + " (" + selected.getStudentId()
                + ") will be removed with its face vector. The audit entry will remain.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        long id = selected.getId();
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return service.delete(table.admin(), id);
            }
        };
        tasks.runAction(task, "Student record deleted; the delete action was audited.");
    }

    /** Lets admin/teachers manually update attendance for an approved student. */
    void manualAttendanceEntry() {
        Student selected = table.selectedRecord();
        if (selected == null) {
            ui.statusLabel.setText("Please select a student from the table first to update attendance.");
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Select Student");
            info.setHeaderText("No Student Selected");
            info.setContentText("Please select a student from the directory table first, then click 'Manual Attendance Entry'.");
            info.showAndWait();
            return;
        }

        List<String> bcaSubjects = List.of(
                "Java Programming (BCA-301)",
                "Data Structures & Algorithms (BCA-302)",
                "Database Management Systems (BCA-303)",
                "Web Technologies (BCA-304)",
                "Computer Networks (BCA-305)",
                "Operating Systems (BCA-306)",
                "Software Engineering (BCA-307)",
                "Python Programming (BCA-308)",
                "General Attendance"
        );

        ChoiceDialog<String> dialog = new ChoiceDialog<>(bcaSubjects.get(0), bcaSubjects);
        dialog.setTitle("Manual Attendance Override");
        dialog.setHeaderText("Mark Verified Attendance for " + selected.getFullName() + " (" + selected.getStudentId() + ")");
        dialog.setContentText("Select BCA Subject / Course:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String subject = result.get();
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO attendance_logs (probe_file, matched_student_id, subject, score, status) " +
                         "VALUES ('MANUAL_TEACHER_OVERRIDE', ?, ?, 1.0, 'PRESENT')")) {
                stmt.setLong(1, selected.getId());
                stmt.setString(2, subject);
                stmt.executeUpdate();

                ui.statusLabel.setText("✔ Manual attendance marked for " + selected.getFullName() + " in " + subject);
                ui.refreshAnalytics();

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Attendance Updated");
                success.setHeaderText("Attendance Marked Successfully");
                success.setContentText("Marked PRESENT for:\n" + selected.getFullName() +
                        " (" + selected.getStudentId() + ")\nSubject: " + subject);
                success.showAndWait();
            } catch (Exception ex) {
                ui.statusLabel.setText("Manual update failed: " + ex.getMessage());
            }
        }
    }

    /** Exports all attendance logs into a clean CSV file for Excel / teachers. */
    void exportAttendanceCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Attendance CSV Report");
        chooser.setInitialFileName("bca_attendance_report.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File file = chooser.showSaveDialog(ui.choosePhotoButton.getScene().getWindow());
        if (file == null) return;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT a.check_in_time, s.student_id, s.full_name, s.department, a.subject, a.score, a.status " +
                     "FROM attendance_logs a LEFT JOIN students s ON a.matched_student_id = s.id " +
                     "ORDER BY a.check_in_time DESC");
             ResultSet rs = stmt.executeQuery();
             PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {

            writer.println("Check-in Timestamp,Student ID / Roll No,Student Name,Branch / Department,Subject / Course,Match Confidence (%),Status");
            int count = 0;
            while (rs.next()) {
                String timestamp = rs.getString("check_in_time");
                String studentId = rs.getString("student_id") != null ? rs.getString("student_id") : "UNKNOWN";
                String name = rs.getString("full_name") != null ? rs.getString("full_name") : "Unregistered Face";
                String dept = rs.getString("department") != null ? rs.getString("department") : "BCA";
                String subject = rs.getString("subject") != null ? rs.getString("subject") : "General";
                double score = rs.getDouble("score");
                String status = rs.getString("status");

                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%.1f%%\",\"%s\"%n",
                        timestamp, studentId, name, dept, subject, score * 100, status);
                count++;
            }
            ui.statusLabel.setText("Exported " + count + " attendance logs to: " + file.getName());
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("CSV Export Successful");
            alert.setHeaderText("BCA Attendance Report Exported");
            alert.setContentText("Successfully exported " + count + " records to:\n" + file.getAbsolutePath());
            alert.showAndWait();
        } catch (Exception ex) {
            ui.statusLabel.setText("Export failed: " + ex.getMessage());
        }
    }

    void selectionCleared() {
        ui.updateButton.setDisable(true);
        ui.deleteButton.setDisable(true);
        ui.addButton.setDisable(false);
    }

    void showSelected(Student selected) {
        if (selected == null) return;
        ui.caseCodeField.setText(selected.getStudentId());
        ui.fullNameField.setText(selected.getFullName());
        ui.ageField.setText(selected.getAge() == 0 ? "" : Integer.toString(selected.getAge()));
        ui.offenceTypeField.setText(selected.getDepartment());
        ui.photoPathLabel.setText(selected.getPhotoPath());
        ui.syntheticConfirmCheckBox.setSelected(true);
        ui.updateButton.setDisable(false);
        ui.deleteButton.setDisable(false);
        ui.addButton.setDisable(true);
        ui.statusLabel.setText("Selected student: " + selected.getFullName() + ". You can edit branch/details and click 'Update Student'.");
    }

    void clearForm() {
        selectedPhoto = null;
        ui.caseCodeField.clear();
        ui.fullNameField.clear();
        ui.ageField.clear();
        ui.offenceTypeField.clear();
        ui.photoPathLabel.setText("No photo selected");
        ui.syntheticConfirmCheckBox.setSelected(false);
        ui.recordsTable.getSelectionModel().clearSelection();
        ui.updateButton.setDisable(true);
        ui.deleteButton.setDisable(true);
        ui.addButton.setDisable(false);
        ui.statusLabel.setText("Form cleared. Enter student details to enroll.");
    }

    private Integer readAge() {
        String text = ui.ageField.getText() == null ? "" : ui.ageField.getText().trim();
        if (text.isEmpty()) {
            ui.statusLabel.setText("Enter a valid age or semester number.");
            return null;
        }
        try {
            int age = Integer.parseInt(text);
            if (age < 1 || age > 120) {
                ui.statusLabel.setText("Enter a valid number between 1 and 120.");
                return null;
            }
            return age;
        } catch (NumberFormatException e) {
            ui.statusLabel.setText("Age/Semester must be a whole number.");
            return null;
        }
    }
}
