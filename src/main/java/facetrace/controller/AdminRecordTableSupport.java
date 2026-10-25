package facetrace.controller;

import facetrace.model.AdminSession;
import facetrace.model.CriminalRecord;
import facetrace.service.AdminRecordService;
import facetrace.service.RoleChecker;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.List;

/** Prepares the records table and loads its rows without blocking the screen. */
final class AdminRecordTableSupport {
    private final AdminController ui;
    private final AdminRecordService service;
    private AdminSession admin;
    private CriminalRecord selectedRecord;

    AdminRecordTableSupport(AdminController ui, AdminRecordService service) {
        this.ui = ui;
        this.service = service;
    }

    /** Connects columns and row selection to the form. */
    void initialize() {
        ui.caseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getCaseCode()));
        ui.nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFullName()));
        ui.ageColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Integer.toString(cell.getValue().getAge())));
        ui.noteColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getOffenceType()));
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

    /** Accepts only an admin session and starts the first table load. */
    void setSession(AdminSession session) {
        new RoleChecker().requireAdmin(session);
        admin = session;
        ui.signedInLabel.setText("Signed in: " + session.username());
        refreshRecords();
    }

    /** Fetches current rows on a worker thread. */
    void refreshRecords() {
        refreshRecords(true);
    }

    /** Reloads rows without replacing a just-completed action message. */
    void refreshRecords(boolean showCount) {
        if (admin == null) {
            ui.statusLabel.setText("An administrator must sign in first.");
            return;
        }
        ui.refreshButton.setDisable(true);
        Task<List<CriminalRecord>> task = new Task<>() {
            @Override
            protected List<CriminalRecord> call() throws Exception {
                return service.loadRecords(admin);
            }
        };
        task.setOnSucceeded(event -> {
            ui.refreshButton.setDisable(false);
            ui.recordsTable.getItems().setAll(task.getValue());
            if (showCount) ui.statusLabel.setText("Loaded " + task.getValue().size() + " saved records.");
        });
        task.setOnFailed(event -> {
            ui.refreshButton.setDisable(false);
            ui.statusLabel.setText("Could not load records: " + safeMessage(task.getException()));
        });
        start(task);
    }

    /** Returns the selected row for edit and delete actions. */
    CriminalRecord selectedRecord() {
        return selectedRecord;
    }

    /** Returns the session to a service task that has already passed the role guard. */
    AdminSession admin() {
        return admin;
    }

    /** Shows only a file name rather than a full local photo path. */
    String photoName(String path) {
        if (path == null || path.isBlank()) return "No photo";
        try {
            Path file = Path.of(path).getFileName();
            return file == null ? path : file.toString();
        } catch (RuntimeException invalidPath) {
            return "Selected photo";
        }
    }

    private String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null ? "Check the database settings." : error.getMessage();
    }

    private void start(Task<?> task) {
        Thread worker = new Thread(task, "facetrace-admin-load");
        worker.setDaemon(true);
        worker.start();
    }
}
