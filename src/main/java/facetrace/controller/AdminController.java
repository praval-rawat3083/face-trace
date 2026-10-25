package facetrace.controller;

import facetrace.model.AdminSession;
import facetrace.model.CriminalRecord;
import facetrace.service.AdminRecordService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/** Routes FXML button events to small screen helpers. */
public final class AdminController {
    @FXML Label signedInLabel;
    @FXML Label statusLabel;
    @FXML Label photoPathLabel;
    @FXML TextField caseCodeField;
    @FXML TextField fullNameField;
    @FXML TextField ageField;
    @FXML TextField offenceTypeField;
    @FXML CheckBox syntheticConfirmCheckBox;
    @FXML Button choosePhotoButton;
    @FXML Button addButton;
    @FXML Button updateButton;
    @FXML Button deleteButton;
    @FXML Button clearButton;
    @FXML Button refreshButton;
    @FXML TableView<CriminalRecord> recordsTable;
    @FXML TableColumn<CriminalRecord, String> caseColumn;
    @FXML TableColumn<CriminalRecord, String> nameColumn;
    @FXML TableColumn<CriminalRecord, String> ageColumn;
    @FXML TableColumn<CriminalRecord, String> noteColumn;
    @FXML TableColumn<CriminalRecord, String> photoColumn;

    AdminRecordActions actions;
    private AdminRecordTableSupport table;

    /** Creates helpers only after FXML has connected all controls. */
    @FXML
    private void initialize() {
        AdminRecordService service = new AdminRecordService();
        table = new AdminRecordTableSupport(this, service);
        actions = new AdminRecordActions(this, table, service);
        table.initialize();
    }

    /** Receives the verified admin session from the login controller. */
    public void setSession(AdminSession session) {
        table.setSession(session);
    }

    @FXML private void choosePhoto() { actions.choosePhoto(); }
    @FXML private void addRecord() { actions.addRecord(); }
    @FXML private void updateRecord() { actions.updateRecord(); }
    @FXML private void deleteRecord() { actions.deleteRecord(); }
    @FXML private void clearForm() { actions.clearForm(); }
    @FXML private void refreshRecords() { table.refreshRecords(); }
}
