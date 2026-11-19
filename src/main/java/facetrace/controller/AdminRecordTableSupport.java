package facetrace.controller;

import facetrace.model.AdminSession;
import facetrace.model.Student;
import facetrace.service.AdminRecordService;
import facetrace.service.RoleChecker;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;

import java.io.File;
import java.util.List;

/** Connects the student records table to the admin controller and manages table selections. */
final class AdminRecordTableSupport {
    private final AdminController ui;
    private final AdminRecordService service;
    private AdminSession admin;
    private Student selectedRecord;

    AdminRecordTableSupport(AdminController ui, AdminRecordService service) {
        this.ui = ui;
        this.service = service;
    }

    void initialize() {
        ui.caseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStudentId()));
        ui.nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFullName()));
        ui.ageColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getAge() == 0 ? "N/A" : Integer.toString(cell.getValue().getAge())));
        ui.noteColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDepartment()));
        ui.photoColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(photoName(cell.getValue().getPhotoPath())));
        ui.recordsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selectedRecord = selected;
            if (selected == null) {
                ui.actions.selectionCleared();
            } else {
                ui.actions.showSelected(selected);
            }
        });
    }

    void setSession(AdminSession session) {
        new RoleChecker().requireAdmin(session);
        admin = session;
        ui.signedInLabel.setText("Signed in: " + session.username());
        refreshRecords();
    }

    AdminSession admin() { return admin; }
    Student selectedRecord() { return selectedRecord; }

    void refreshRecords() {
        if (admin == null) return;
        ui.refreshButton.setDisable(true);
        ui.statusLabel.setText("Loading enrolled students from database...");
        Task<List<Student>> task = new Task<>() {
            @Override
            protected List<Student> call() throws Exception {
                return service.loadRecords(admin);
            }
        };
        task.setOnSucceeded(event -> {
            ui.refreshButton.setDisable(false);
            List<Student> records = task.getValue();
            ui.recordsTable.getItems().setAll(records);
            ui.statusLabel.setText("Loaded " + records.size() + " enrolled students.");
        });
        task.setOnFailed(event -> {
            ui.refreshButton.setDisable(false);
            ui.statusLabel.setText("Could not load student records. Check database connection.");
        });
        Thread worker = new Thread(task, "facetrace-admin-load");
        worker.setDaemon(true);
        worker.start();
    }

    private String photoName(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) return "N/A";
        return new File(photoPath).getName();
    }
}
