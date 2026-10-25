package facetrace.controller;

import javafx.concurrent.Task;

/** Runs record writes away from the JavaFX thread and restores the controls afterward. */
final class AdminTaskRunner {
    private final AdminController ui;
    private final AdminRecordTableSupport table;

    AdminTaskRunner(AdminController ui, AdminRecordTableSupport table) {
        this.ui = ui;
        this.table = table;
    }

    /** Shows a short result message after one background action completes. */
    void runAction(Task<?> task, String successMessage) {
        setBusy(true);
        task.setOnSucceeded(event -> {
            setBusy(false);
            if (Boolean.FALSE.equals(task.getValue())) {
                ui.statusLabel.setText("That record no longer exists. Refresh the table and try again.");
                return;
            }
            ui.statusLabel.setText(successMessage);
            ui.actions.clearForm();
            table.refreshRecords(false);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            ui.statusLabel.setText("Action failed: " + message(task.getException()));
        });
        start(task);
    }

    /** Disables screen controls while a save, delete, or image embedding is running. */
    private void setBusy(boolean busy) {
        boolean hasSelection = table.selectedRecord() != null;
        ui.addButton.setDisable(busy);
        ui.updateButton.setDisable(busy || !hasSelection);
        ui.deleteButton.setDisable(busy || !hasSelection);
        ui.choosePhotoButton.setDisable(busy || hasSelection);
        ui.clearButton.setDisable(busy);
        ui.refreshButton.setDisable(busy);
        ui.recordsTable.setDisable(busy);
        ui.caseCodeField.setDisable(busy);
        ui.fullNameField.setDisable(busy);
        ui.ageField.setDisable(busy);
        ui.offenceTypeField.setDisable(busy);
        ui.syntheticConfirmCheckBox.setDisable(busy);
    }

    /** Keeps the window usable if a task fails without an exception message. */
    private String message(Throwable error) {
        return error == null || error.getMessage() == null
                ? "Check the database and selected photo." : error.getMessage();
    }

    /** Runs a JavaFX task on its own daemon thread. */
    private void start(Task<?> task) {
        Thread worker = new Thread(task, "facetrace-admin-records");
        worker.setDaemon(true);
        worker.start();
    }
}
