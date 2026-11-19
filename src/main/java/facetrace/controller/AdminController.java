package facetrace.controller;

import facetrace.config.DatabaseConnection;
import facetrace.dao.StudentDao;
import facetrace.model.AdminSession;
import facetrace.model.Student;
import facetrace.service.AdminRecordService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Routes FXML button events to student management and analytics helpers. */
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
    @FXML Button exportCsvButton;
    @FXML Button manualAttendanceButton;
    @FXML TableView<Student> recordsTable;
    @FXML TableColumn<Student, String> caseColumn;
    @FXML TableColumn<Student, String> nameColumn;
    @FXML TableColumn<Student, String> ageColumn;
    @FXML TableColumn<Student, String> noteColumn;
    @FXML TableColumn<Student, String> photoColumn;

    // Analytics Controls
    @FXML Label kpiTotalStudentsLabel;
    @FXML Label kpiTotalSessionsLabel;
    @FXML Label kpiDefaultersLabel;
    @FXML Button filterDefaultersButton;
    @FXML Button refreshAnalyticsButton;
    @FXML TableView<StudentDao.StudentAttendanceSummary> analyticsTable;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, String> statStudentIdCol;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, String> statNameCol;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, String> statDeptCol;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, Integer> statPresentCol;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, Integer> statTotalCol;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, String> statPercentageCol;
    @FXML TableColumn<StudentDao.StudentAttendanceSummary, String> statStatusCol;

    private boolean showingDefaultersOnly = false;
    private List<StudentDao.StudentAttendanceSummary> cachedSummaries = new ArrayList<>();

    AdminRecordActions actions;
    private AdminRecordTableSupport table;

    @FXML
    private void initialize() {
        AdminRecordService service = new AdminRecordService();
        table = new AdminRecordTableSupport(this, service);
        actions = new AdminRecordActions(this, table, service);
        table.initialize();

        initAnalyticsTable();
    }

    private void initAnalyticsTable() {
        statStudentIdCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().studentId()));
        statNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().fullName()));
        statDeptCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().department()));
        statPresentCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().presents()));
        statTotalCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().totalSessions()));
        statPercentageCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().percentageFormatted()));
        statStatusCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().statusPill()));
    }

    public void setSession(AdminSession session) {
        table.setSession(session);
        refreshAnalytics();
    }

    @FXML
    void refreshAnalytics() {
        refreshAnalyticsButton.setDisable(true);
        Task<List<StudentDao.StudentAttendanceSummary>> task = new Task<>() {
            @Override
            protected List<StudentDao.StudentAttendanceSummary> call() throws Exception {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    return new StudentDao(conn).getAttendanceSummaries();
                }
            }
        };

        task.setOnSucceeded(event -> {
            refreshAnalyticsButton.setDisable(false);
            cachedSummaries = task.getValue();

            int totalStudents = cachedSummaries.size();
            int totalSessions = cachedSummaries.isEmpty() ? 0 : cachedSummaries.get(0).totalSessions();
            long defaultersCount = cachedSummaries.stream().filter(StudentDao.StudentAttendanceSummary::isDefaulter).count();

            kpiTotalStudentsLabel.setText(String.valueOf(totalStudents));
            kpiTotalSessionsLabel.setText(String.valueOf(totalSessions));
            kpiDefaultersLabel.setText(String.valueOf(defaultersCount));

            applyAnalyticsFilter();
        });

        task.setOnFailed(event -> {
            refreshAnalyticsButton.setDisable(false);
            statusLabel.setText("Could not load attendance analytics.");
        });

        Thread t = new Thread(task, "analytics-loader-thread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void toggleDefaulterFilter() {
        showingDefaultersOnly = !showingDefaultersOnly;
        if (showingDefaultersOnly) {
            filterDefaultersButton.setText("👥 Show All Students");
            filterDefaultersButton.setStyle("-fx-background-color: #3b82f6;");
        } else {
            filterDefaultersButton.setText("⚠ Show Defaulters Only (< 75%)");
            filterDefaultersButton.setStyle("-fx-background-color: #ef4444;");
        }
        applyAnalyticsFilter();
    }

    private void applyAnalyticsFilter() {
        if (showingDefaultersOnly) {
            analyticsTable.getItems().setAll(
                    cachedSummaries.stream().filter(StudentDao.StudentAttendanceSummary::isDefaulter).toList()
            );
        } else {
            analyticsTable.getItems().setAll(cachedSummaries);
        }
    }

    @FXML private void choosePhoto() { actions.choosePhoto(); }
    @FXML private void addRecord() { actions.addRecord(); }
    @FXML private void updateRecord() { actions.updateRecord(); }
    @FXML private void deleteRecord() { actions.deleteRecord(); }
    @FXML private void clearForm() { actions.clearForm(); }
    @FXML private void refreshRecords() { table.refreshRecords(); }
    @FXML private void exportAttendanceCsv() { actions.exportAttendanceCsv(); }
    @FXML private void manualAttendanceEntry() { actions.manualAttendanceEntry(); }
}
