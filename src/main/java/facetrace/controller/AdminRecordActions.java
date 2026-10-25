package facetrace.controller;

import facetrace.model.CriminalRecord;
import facetrace.service.AdminRecordService;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/** Handles the admin form while delegating database work to services and tasks. */
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

    /** Lets the user choose a local photo without copying it into the project. */
    void choosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose one permitted synthetic demo photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.pgm"));
        File selected = chooser.showOpenDialog(ui.choosePhotoButton.getScene().getWindow());
        if (selected != null) {
            selectedPhoto = selected.toPath().toAbsolutePath().normalize();
            ui.photoPathLabel.setText(selected.getName());
            ui.statusLabel.setText("The photo stays where it is; only its path is saved.");
        }
    }

    /** Adds a synthetic subject after detecting one face and measuring its LBP vector. */
    void addRecord() {
        Integer age = readAge();
        if (age == null) return;
        if (selectedPhoto == null) {
            ui.statusLabel.setText("Choose one photo containing exactly one detectable face.");
            return;
        }
        String code = ui.caseCodeField.getText();
        String name = ui.fullNameField.getText();
        String note = ui.offenceTypeField.getText();
        boolean confirmed = ui.syntheticConfirmCheckBox.isSelected();
        Task<CriminalRecord> task = new Task<>() {
            @Override
            protected CriminalRecord call() throws Exception {
                return service.add(table.admin(), code, name, age, note, selectedPhoto, confirmed);
            }
        };
        tasks.runAction(task, "Record and LBP vector saved; the add action was audited.");
    }

    /** Updates a selected synthetic row but keeps its existing photo and vector. */
    void updateRecord() {
        CriminalRecord selected = table.selectedRecord();
        Integer age = readAge();
        if (selected == null || age == null) return;
        CriminalRecord changed = new CriminalRecord(selected.getId(), ui.caseCodeField.getText(),
                ui.fullNameField.getText(), age, ui.offenceTypeField.getText(), selected.getPhotoPath());
        boolean confirmed = ui.syntheticConfirmCheckBox.isSelected();
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return service.update(table.admin(), changed, confirmed);
            }
        };
        tasks.runAction(task, "Record updated; the edit action was audited.");
    }

    /** Confirms a delete before removing the row and its linked embedding. */
    void deleteRecord() {
        CriminalRecord selected = table.selectedRecord();
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm delete");
        confirm.setHeaderText("Delete synthetic demo record?");
        confirm.setContentText(selected.getFullName() + " / " + selected.getCaseCode()
                + " will be removed with its vector. The audit entry will remain.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        long id = selected.getId();
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return service.delete(table.admin(), id);
            }
        };
        tasks.runAction(task, "Record deleted; the delete action was audited.");
    }

    /** Clears selected values so the form can accept another synthetic record. */
    void clearForm() {
        ui.recordsTable.getSelectionModel().clearSelection();
        selectedPhoto = null;
        ui.caseCodeField.clear();
        ui.fullNameField.clear();
        ui.ageField.clear();
        ui.offenceTypeField.clear();
        ui.photoPathLabel.setText("No photo selected");
        ui.syntheticConfirmCheckBox.setSelected(false);
        selectionCleared();
    }

    /** Copies a table row into the form for editing. */
    void showSelected(CriminalRecord record) {
        selectedPhoto = null;
        ui.caseCodeField.setText(record.getCaseCode());
        ui.fullNameField.setText(record.getFullName());
        ui.ageField.setText(Integer.toString(record.getAge()));
        ui.offenceTypeField.setText(record.getOffenceType());
        ui.photoPathLabel.setText(table.photoName(record.getPhotoPath()));
        ui.syntheticConfirmCheckBox.setSelected(false);
        ui.choosePhotoButton.setDisable(true);
        ui.updateButton.setDisable(false);
        ui.deleteButton.setDisable(false);
    }

    /** Restores the new-record buttons after the table selection is cleared. */
    void selectionCleared() {
        ui.updateButton.setDisable(true);
        ui.deleteButton.setDisable(true);
        ui.choosePhotoButton.setDisable(false);
    }

    private Integer readAge() {
        try {
            return Integer.parseInt(ui.ageField.getText().trim());
        } catch (NumberFormatException invalidNumber) {
            ui.statusLabel.setText("Enter a whole-number synthetic age from 1 to 120.");
            return null;
        }
    }
}
